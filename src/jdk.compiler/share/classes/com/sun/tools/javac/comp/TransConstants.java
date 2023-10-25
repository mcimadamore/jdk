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

import com.sun.tools.javac.code.Symbol.DynamicVarSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCConstExpr;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import static com.sun.tools.javac.code.Flags.PRIVATE;
import static com.sun.tools.javac.code.Flags.STATIC;
import static com.sun.tools.javac.code.Flags.SYNTHETIC;

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

    ListBuffer<JCTree> pendingClassDefs;

    @Override
    public void visitConstExpr(JCConstExpr tree) {
        result = splitConstant(tree);
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

    private JCExpression splitConstant(JCConstExpr tree) {
        Name constName = names.fromString("const")
                              .append('$', names.fromString(String.valueOf(pendingClassDefs.size())));
        // create synthetic init symbol
        MethodSymbol initSym = new MethodSymbol(
                STATIC | SYNTHETIC | PRIVATE,
                constName.append('$', names.fromString("init")),
                new MethodType(List.nil(), tree.type, List.nil(), syms.methodClass),
                currentClass.sym);
        currentClass.sym.members().enter(initSym);
        // create synthetic init tree
        JCExpression initExpr = translate(tree.expr);
        JCMethodDecl initDef = make.MethodDef(initSym, make.Block(0, List.of(make.Return(initExpr))));
        pendingClassDefs.add(initDef);

        List<Type> lazyInit_staticArgTypes = List.of(syms.methodHandleLookupType,
                syms.stringType,
                syms.classType,
                syms.methodHandleType);

        MethodSymbol bsm = rs.resolveInternalMethod(tree, attrEnv, syms.constantBootstraps,
                names.invoke, lazyInit_staticArgTypes, List.nil());

        // set a constant value that points to a dynamic symbol, so that Gen can emit the correct ldc
        DynamicVarSymbol condySym = new DynamicVarSymbol(constName, currentClass.sym, bsm.asHandle(), tree.type,
                new LoadableConstant[] { initSym.asHandle() });
        return make.Ident(condySym);
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
