/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Scope.WriteableScope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.DynamicVarSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.HashMap;
import java.util.Map;

import static com.sun.tools.javac.code.Flags.PRIVATE;
import static com.sun.tools.javac.code.Flags.STATIC;
import static com.sun.tools.javac.code.Flags.SYNTHETIC;
import static com.sun.tools.javac.code.Kinds.Kind.MTH;

public class TransConstants extends TreeTranslator {

    protected static final Context.Key<TransConstants> transConstantsKey = new Context.Key<>();

    public static TransConstants instance(Context context) {
        TransConstants instance = context.get(transConstantsKey);
        if (instance == null)
            instance = new TransConstants(context);
        return instance;
    }

    private final Names names;
    private final Symtab syms;
    private TreeMaker make;
    private Resolve rs;
    private Target target;

    @SuppressWarnings("this-escape")
    protected TransConstants(Context context) {
        context.put(transConstantsKey, this);
        names = Names.instance(context);
        syms = Symtab.instance(context);
        make = TreeMaker.instance(context);
        rs = Resolve.instance(context);
        target = Target.instance(context);
    }

    /** The currently enclosing class.
     */
    JCClassDecl currentClass;

    /** Environment for symbol lookup, set by translateTopLevelClass.
     */
    Env<AttrContext> attrEnv;

    /** A table mapping static local symbols to their desugared counterparts.
     */
    Map<VarSymbol, VarSymbol> staticLocalsTable;

    ListBuffer<JCTree> pendingClassDefs;

    public void visitVarDef(JCVariableDecl tree) {
        if (tree.sym.isStatic() && tree.sym.owner.kind == MTH) {
            if (isSplittableStaticLocal(tree)) {
                splitStaticLocalInit(tree);
            } else {
                liftStaticLocal(tree);
            }
            // do not emit the variable declaration
            result = make.Skip();
        } else {
            super.visitVarDef(tree);
        }
    }

    @Override
    public void visitIdent(JCIdent tree) {
        if (staticLocalsTable != null && staticLocalsTable.containsKey(tree.sym)) {
            Symbol translatedSym = staticLocalsTable.get(tree.sym);
            result = make.Ident(translatedSym);
        } else {
            super.visitIdent(tree);
        }
    }

    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        Map<VarSymbol, VarSymbol> prevStaticLocalsTable = staticLocalsTable;
        try {
            staticLocalsTable = new HashMap<>();
            super.visitMethodDef(tree);
        } finally {
            staticLocalsTable = prevStaticLocalsTable;
        }
    }

    @Override
    public void visitClassDef(JCClassDecl tree) {
        JCClassDecl prevClass = currentClass;
        ListBuffer<JCTree> prevPendingClassDefs = pendingClassDefs;
        try {
            currentClass = tree;
            pendingClassDefs = new ListBuffer<>();
            super.visitClassDef(tree);
            tree.defs = tree.defs.appendList(pendingClassDefs.toList());
        } finally {
            currentClass = prevClass;
            pendingClassDefs = prevPendingClassDefs;
        }
    }

    boolean isSplittableStaticLocal(JCVariableDecl tree) {
        return tree.sym.isFinal() &&
                !hasAnonClassDefs(tree);
    }

    boolean hasAnonClassDefs(JCTree tree) {
        class AnonClassFinder extends TreeScanner {
            boolean anonFound;

            @Override
            public void visitNewClass(JCNewClass tree) {
                if (tree.def != null) {
                    anonFound = true;
                }
                super.visitNewClass(tree);
            }

            @Override
            public void visitClassDef(JCClassDecl tree) {
                // do not recurse
            }
        }
        AnonClassFinder anonClassFinder = new AnonClassFinder();
        tree.accept(anonClassFinder);
        return anonClassFinder.anonFound;
    }

    private void splitStaticLocalInit(JCVariableDecl tree) {
        Assert.checkNonNull(tree.init);
        // create synthetic init symbol
        MethodSymbol initSym = new MethodSymbol(
                STATIC | SYNTHETIC | PRIVATE,
                tree.name.append('$', names.fromString("init")),
                new MethodType(List.nil(), tree.type, List.nil(), syms.methodClass),
                currentClass.sym);
        enterSynthetic(tree.pos(), initSym, currentClass.sym.members());
        // create synthetic init tree
        JCExpression initExpr = translate(tree.init);
        JCMethodDecl initDef = make.MethodDef(initSym, make.Block(0, List.of(make.Return(initExpr))));
        pendingClassDefs.add(initDef);
        // drop original init
        tree.init = null;
        List<Type> lazyInit_staticArgTypes = List.of(syms.methodHandleLookupType,
                syms.stringType,
                syms.classType,
                syms.methodHandleType);

        MethodSymbol bsm = rs.resolveInternalMethod(tree, attrEnv, syms.constantBootstraps,
                names.invoke, lazyInit_staticArgTypes, List.nil());

        // set a constant value that points to a dynamic symbol, so that Gen can emit the correct ldc
        DynamicVarSymbol condySym = new DynamicVarSymbol(tree.name, currentClass.sym, bsm.asHandle(), tree.type,
                new LoadableConstant[] { initSym.asHandle() });
        staticLocalsTable.put(tree.sym, condySym);
    }

    private void liftStaticLocal(JCVariableDecl tree) {
        Assert.checkNonNull(tree.init);
        // create synthetic init symbol
        VarSymbol liftedSym = new VarSymbol(
                STATIC | SYNTHETIC | PRIVATE,
                makeSyntheticName(tree.name.append('$', names.fromString("static")), currentClass.sym.members()),
                tree.sym.type, currentClass.sym);
        enterSynthetic(tree.pos(), liftedSym, currentClass.sym.members());
        // create synthetic init tree
        JCExpression initExpr = translate(tree.init);
        JCVariableDecl liftedDecl = make.VarDef(liftedSym, initExpr);
        pendingClassDefs.add(liftedDecl);
        staticLocalsTable.put(tree.sym, liftedSym);
    }

    // copied from Lower
    private void enterSynthetic(DiagnosticPosition pos, Symbol sym, WriteableScope s) {
        s.enter(sym);
    }

    private Name makeSyntheticName(Name name, Scope s) {
        do {
            name = name.append(
                    target.syntheticNameChar(),
                    names.empty);
        } while (lookupSynthetic(name, s) != null);
        return name;
    }

    private Symbol lookupSynthetic(Name name, Scope s) {
        Symbol sym = s.findFirst(name);
        return (sym==null || (sym.flags()&SYNTHETIC)==0) ? null : sym;
    }

    /** Translate a toplevel class and return a list consisting of
     *  the translated class and translated versions of all inner classes.
     *  @param env   The attribution environment current at the class definition.
     *               We need this for resolving some additional symbols.
     *  @param cdef  The tree representing the class definition.
     */
    public JCTree translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        try {
            attrEnv = env;
            this.make = make;
            currentClass = null;
            return translate(cdef);
        } finally {
            // note that recursive invocations of this method fail hard
            attrEnv = null;
            this.make = null;
            currentClass = null;
        }
    }
}
