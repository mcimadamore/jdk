/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.DynamicVarSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.Tag;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.InvalidUtfException;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

import static com.sun.tools.javac.code.Flags.CACHED;
import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.PARAMETER;
import static com.sun.tools.javac.code.Flags.PRIVATE;
import static com.sun.tools.javac.code.Flags.STATIC;
import static com.sun.tools.javac.code.Flags.SYNTHETIC;

public class TransCachedMethods extends TreeTranslator {

    protected static final Context.Key<TransCachedMethods> transConstantsKey = new Context.Key<>();

    public static TransCachedMethods instance(Context context) {
        TransCachedMethods instance = context.get(transConstantsKey);
        if (instance == null)
            instance = new TransCachedMethods(context);
        return instance;
    }

    private final Names names;
    private final Symtab syms;
    private TreeMaker make;
    private final Resolve rs;
    private final Operators operators;
    private final Types types;
    private final boolean compactCachedStatic;

    @SuppressWarnings("this-escape")
    protected TransCachedMethods(Context context) {
        context.put(transConstantsKey, this);
        names = Names.instance(context);
        syms = Symtab.instance(context);
        make = TreeMaker.instance(context);
        rs = Resolve.instance(context);
        operators = Operators.instance(context);
        types = Types.instance(context);
        compactCachedStatic = Options.instance(context).isSet("compactCachedStatic");
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
        boolean isConstantMethod = (tree.sym.flags() & CACHED) != 0;
        if (isConstantMethod) {
            MethodSymbol initSym = dupToSyntheticInit(tree);
            if (compactCachedStatic && tree.sym.isStatic()) {
                VarSymbol dynSym = makeCompactDynamicRef(tree, initSym);
                tree.body = make.Block(0, List.of(make.Return(make.Ident(dynSym))));
                result = tree;
                return;
            }
            VarSymbol cacheSym = makeCachedFieldSymbol(tree);
            VarSymbol accessorSym = makeDynamicAccessor(tree, cacheSym, decorateName(tree.name, "cached"));
            VarSymbol tempRes = new VarSymbol(0, decorateName(tree.name, "tmp"), tree.type.getReturnType(), tree.sym);
            Symbol getterSym = rs.resolveInternalMethod(tree, attrEnv, accessorSym.type, names.fromString("get"), List.of(syms.objectType), List.nil());
            JCFieldAccess accessorMethodSelect = make.Select(make.Ident(accessorSym), getterSym);
            JCExpression receiver = tree.sym.isStatic() ? makeNull() : makeThis(currentClass.type, tree.sym);
            JCMethodInvocation accessorMethodApply = make.Apply(List.nil(), accessorMethodSelect, List.of(receiver)).setType(cacheSym.type);
            JCMethodInvocation initCall = make.Apply(List.nil(), make.Ident(initSym), List.nil()).setType(cacheSym.type);
            JCStatement thenPart = make.Block(0, List.of(make.Return(make.Assign(make.Ident(cacheSym), initCall).setType(cacheSym.type))));
            JCStatement elsePart = make.Block(0, List.of(make.Return(make.Ident(tempRes))));
            JCStatement ifBody = make.If(makeBinary(Tag.EQ, make.Ident(tempRes), defaultFor(tree.sym.type.getReturnType())), thenPart, elsePart);
            JCVariableDecl variableDecl = make.VarDef(tempRes, accessorMethodApply);
            tree.body = make.Block(0, List.of(variableDecl, ifBody));
            result = tree;
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

    // from TransPatterns
    JCBinary makeBinary(JCTree.Tag optag, JCExpression lhs, JCExpression rhs) {
        JCBinary tree = make.Binary(optag, lhs, rhs);
        tree.operator = operators.resolveBinary(tree, optag, lhs.type, rhs.type);
        tree.type = tree.operator.type.getReturnType();
        return tree;
    }

    private VarSymbol makeCachedFieldSymbol(JCMethodDecl tree) {
        VarSymbol cacheSym = new VarSymbol((tree.sym.isStatic() ? STATIC : 0) | PRIVATE | SYNTHETIC,
                decorateName(tree.name, "cache"), tree.type.getReturnType(), currentClass.sym);
        currentClass.sym.members().enter(cacheSym);
        return cacheSym;
    }

    private MethodSymbol dupToSyntheticInit(JCMethodDecl tree) {
        // create synthetic init symbol
        // invariant: the constant method is non-void, non-generic, and 0-ary
        MethodSymbol initSym = new MethodSymbol(
                (tree.sym.isStatic() ? STATIC : 0) | SYNTHETIC | PRIVATE,
                decorateName(tree.name, "init"),
                tree.sym.type,
                currentClass.sym);
        currentClass.sym.members().enter(initSym);
        // create synthetic method tree
        JCMethodDecl initDef = make.MethodDef(initSym, translate(tree.body));
        pendingClassDefs.add(initDef);
        return initSym;
    }

    private Name decorateName(Name base, String prefix) {
        return base.append('$', names.fromString(prefix));
    }

    private VarSymbol makeDynamicAccessor(DiagnosticPosition pos, VarSymbol cacheSym, Name name) {
        List<Type> lazyInit_staticArgTypes = List.of(syms.methodHandleLookupType,
                syms.stringType,
                syms.classType,
                syms.classType,
                syms.stringType,
                syms.stringType);

        MethodSymbol bsm = rs.resolveInternalMethod(pos, attrEnv, syms.stableAccessorType,
                names.fromString("of"), lazyInit_staticArgTypes, List.nil());

        CachedMethodSignatureGenerator cachedMethodSignatureGenerator = new CachedMethodSignatureGenerator(false);
        cachedMethodSignatureGenerator.assembleSig(cacheSym.type);
        String cacheTypeSig = cachedMethodSignatureGenerator.toString();

        // set a constant value that points to a dynamic symbol, so that Gen can emit the correct ldc
        return new DynamicVarSymbol(name, currentClass.sym, bsm.asHandle(),
                syms.stableAccessorConcreteTypes[cacheSym.type.getTag().ordinal()],
                new LoadableConstant[] {
                        (ClassType)currentClass.type,
                        LoadableConstant.String(cacheSym.name.toString()),
                        LoadableConstant.String(cacheTypeSig)
                });
    }

    private VarSymbol makeCompactDynamicRef(DiagnosticPosition pos, MethodSymbol symbol) {
        List<Type> bsmArgTypes = List.of(syms.methodHandleLookupType,
                syms.stringType,
                syms.classType,
                syms.methodHandleType);

        MethodSymbol bsm = rs.resolveInternalMethod(pos, attrEnv, syms.constantBootstrapsType,
                names.fromString("invoke"), bsmArgTypes, List.nil());

        return new DynamicVarSymbol(symbol.name, currentClass.sym, bsm.asHandle(), symbol.type.getReturnType(),
                new LoadableConstant[] { symbol.asHandle() });
    }

    private JCExpression makeLit(Type type, Object value) {
        return make.Literal(type.getTag(), value)
                .setType(type.constType(value));
    }

    private JCExpression makeNull() {
        return makeLit(syms.botType, null);
    }

    private JCIdent makeThis(Type type, Symbol owner) {
        VarSymbol _this = new VarSymbol(PARAMETER | FINAL | SYNTHETIC,
                names._this,
                type,
                owner);
        return make.Ident(_this);
    }

    private JCExpression defaultFor(Type type) {
        return switch (type.getTag()) {
            case BOOLEAN -> make.Literal(TypeTag.BOOLEAN, 0).setType(type.constType(0));
            case BYTE, SHORT, CHAR, INT -> makeLit(type, 0);
            case LONG -> makeLit(type, 0L);
            case FLOAT -> makeLit(type, 0.0f);
            case DOUBLE -> makeLit(type, 0.0d);
            case CLASS, ARRAY, TYPEVAR, WILDCARD, BOT, ERROR -> makeNull();
            default -> throw new AssertionError("unexpected type for default value: " + type);
        };
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

    /**
     * Signature Generation
     */
    private class CachedMethodSignatureGenerator extends Types.SignatureGenerator {

        /**
         * An output buffer for type signatures.
         */
        StringBuilder sb = new StringBuilder();

        CachedMethodSignatureGenerator(boolean allowIllegalSignatures) {
            types.super();
        }

        @Override
        protected void append(char ch) {
            sb.append(ch);
        }

        @Override
        protected void append(byte[] ba) {
            Name name;
            try {
                name = names.fromUtf(ba);
            } catch (InvalidUtfException e) {
                throw new AssertionError(e);
            }
            sb.append(name.toString());
        }

        @Override
        protected void append(Name name) {
            sb.append(name.toString());
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
