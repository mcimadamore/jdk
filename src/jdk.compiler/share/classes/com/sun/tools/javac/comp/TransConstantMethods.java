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

import com.sun.source.tree.MemberReferenceTree.ReferenceMode;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Scope.WriteableScope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.DynamicVarSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCMemberReference;
import com.sun.tools.javac.tree.JCTree.JCMemberReference.ReferenceKind;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Pair;

import static com.sun.tools.javac.code.Flags.CONST_METHOD;
import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.PRIVATE;
import static com.sun.tools.javac.code.Flags.PUBLIC;
import static com.sun.tools.javac.code.Flags.STATIC;
import static com.sun.tools.javac.code.Flags.SYNTHETIC;

public class TransConstantMethods extends TreeTranslator {

    protected static final Context.Key<TransConstantMethods> transConstantsKey = new Context.Key<>();

    public static TransConstantMethods instance(Context context) {
        TransConstantMethods instance = context.get(transConstantsKey);
        if (instance == null)
            instance = new TransConstantMethods(context);
        return instance;
    }

    private final Names names;
    private final Symtab syms;
    private TreeMaker make;
    private Resolve rs;
    private Target target;
    private Types types;
    private boolean needsLambdaToMethod;

    @SuppressWarnings("this-escape")
    protected TransConstantMethods(Context context) {
        context.put(transConstantsKey, this);
        names = Names.instance(context);
        syms = Symtab.instance(context);
        make = TreeMaker.instance(context);
        rs = Resolve.instance(context);
        target = Target.instance(context);
        types = Types.instance(context);
    }

    /** The currently enclosing class.
     */
    JCClassDecl currentClass;

    /** Environment for symbol lookup, set by translateTopLevelClass.
     */
    Env<AttrContext> attrEnv;

    ListBuffer<JCTree> pendingClassDefs;

    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        boolean isConstantMethod = (tree.sym.flags() & CONST_METHOD) != 0;
        if (isConstantMethod) {
            MethodSymbol initSym = dupToStaticInit(tree);
            if (tree.sym.isStatic()) {
                // condy
                VarSymbol dynSym = makeDynamicRef(tree, initSym);
                JCStatement retStat = make.Return(make.Ident(dynSym));
                tree.body = make.Block(0, List.of(retStat));
                result = tree;
            } else {
                // generate a CC field and return CC::get
                VarSymbol ccSym = makeComputedConstantField(tree, initSym);
                JCFieldAccess recv = make.Select(make.Ident(ccSym), names.get);
                recv.sym = syms.computedConstantGet;
                recv.type = tree.type;
                JCMethodInvocation ccGet = make.App(recv);
                ccGet.type = syms.objectType;
                JCStatement retStat = make.Return(make.TypeCast(types.boxedTypeOrType(tree.type.getReturnType()), ccGet));
                tree.body = make.Block(0, List.of(retStat));
                result = tree;
            }
        } else {
            super.visitMethodDef(tree);
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

    private MethodSymbol dupToStaticInit(JCMethodDecl tree) {
        // create synthetic init symbol
        // invariant: the constant method is non-void, non-generic, and 0-ary
        MethodSymbol initSym = new MethodSymbol(
                (tree.sym.isStatic() ? STATIC : 0) | SYNTHETIC | PRIVATE,
                tree.name.append('$', names.fromString("init")),
                tree.sym.type,
                currentClass.sym);
        enterSynthetic(tree.pos(), initSym, currentClass.sym.members());
        // create synthetic method tree
        JCMethodDecl initDef = make.MethodDef(initSym, translate(tree.body));
        pendingClassDefs.add(initDef);
        return initSym;
    }

    private VarSymbol makeDynamicRef(DiagnosticPosition pos, MethodSymbol symbol) {
        // drop original init
        List<Type> lazyInit_staticArgTypes = List.of(syms.methodHandleLookupType,
                syms.stringType,
                syms.classType,
                syms.methodHandleType);

        MethodSymbol bsm = rs.resolveInternalMethod(pos, attrEnv, syms.constantBootstrapsType,
                names.invoke, lazyInit_staticArgTypes, List.nil());

        // set a constant value that points to a dynamic symbol, so that Gen can emit the correct ldc
        DynamicVarSymbol condySym = new DynamicVarSymbol(symbol.name, currentClass.sym, bsm.asHandle(), symbol.type.getReturnType(),
                new LoadableConstant[] { symbol.asHandle() });
        return condySym;
    }

    private VarSymbol makeComputedConstantField(DiagnosticPosition pos, MethodSymbol initSymbol) {
        // create synthetic init symbol
        VarSymbol liftedSym = new VarSymbol(
                SYNTHETIC | PRIVATE | FINAL,
                makeSyntheticName(initSymbol.name.append('$', names.fromString("cc")), currentClass.sym.members()),
                syms.computedConstantType, currentClass.sym);
        enterSynthetic(pos, liftedSym, currentClass.sym.members());
        // create

        JCLiteral initHandle = make.Literal(TypeTag.BOT, null);
        initHandle.setType(syms.methodHandleType.constType(initSymbol.asHandle()));
        JCFieldAccess bindToRecv = make.Select(initHandle, names.bindTo);
        bindToRecv.type = syms.methodHandleBindTo.type;
        bindToRecv.sym = syms.methodHandleBindTo;
        JCMethodInvocation bindToCall = make.App(bindToRecv, List.of(make.This(currentClass.type)));
        bindToCall.type = syms.methodHandleType;

        VarSymbol constantTypeSym = new VarSymbol(
                STATIC | PUBLIC | FINAL, names._class,
                syms.classType, initSymbol.type.getReturnType().tsym);
        JCFieldAccess constantType = make.Select(make.Type(initSymbol.type.getReturnType()), constantTypeSym);

        JCFieldAccess constrRecv = make.Select(make.Ident(syms.computedConstantType.tsym), names.of);
        constrRecv.type = new MethodType(List.of(syms.classType, syms.methodHandleType), syms.computedConstantType, List.nil(), syms.methodClass);
        constrRecv.sym = syms.computedConstantOf;
        JCMethodInvocation constrCall = make.App(constrRecv, List.of(constantType, bindToCall));
        constrCall.type = syms.computedConstantType;
        JCVariableDecl liftedDecl = make.VarDef(liftedSym, constrCall);
        pendingClassDefs.add(liftedDecl);
        needsLambdaToMethod = true;
        return liftedSym;
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
    public Pair<JCTree, Boolean> translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        try {
            attrEnv = env;
            this.make = make;
            currentClass = null;
            needsLambdaToMethod = false;
            JCTree translated = translate(cdef);
            return new Pair<>(translated, needsLambdaToMethod);
        } finally {
            // note that recursive invocations of this method fail hard
            attrEnv = null;
            this.make = null;
            currentClass = null;
            needsLambdaToMethod = false;
        }
    }
}
