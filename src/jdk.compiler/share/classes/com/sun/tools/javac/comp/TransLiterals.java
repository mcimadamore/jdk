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

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.DynamicMethodSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.Iterator;

/** This pass translates constructed literals (string templates, ...) to conventional Java.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public final class TransLiterals extends TreeTranslator {
    /**
     * The context key for the TransTypes phase.
     */
    protected static final Context.Key<TransLiterals> transLiteralsKey = new Context.Key<>();

    /**
     * Get the instance for this context.
     */
    public static TransLiterals instance(Context context) {
        TransLiterals instance = context.get(transLiteralsKey);
        if (instance == null)
            instance = new TransLiterals(context);
        return instance;
    }

    private final Symtab syms;
    private final Resolve rs;
    private final Types types;
    private final Operators operators;
    private final Names names;
    private TreeMaker make = null;
    private Env<AttrContext> env = null;
    private ClassSymbol currentClass = null;
    private MethodSymbol currentMethodSym = null;

    protected TransLiterals(Context context) {
        context.put(transLiteralsKey, this);
        syms = Symtab.instance(context);
        rs = Resolve.instance(context);
        make = TreeMaker.instance(context);
        types = Types.instance(context);
        operators = Operators.instance(context);
        names = Names.instance(context);
    }

    JCExpression makeLit(Type type, Object value) {
        return make.Literal(type.getTag(), value).setType(type.constType(value));
    }

    JCExpression makeString(String string) {
        return makeLit(syms.stringType, string);
    }

    List<JCExpression> makeStringList(List<String> strings) {
        List<JCExpression> exprs = List.nil();
        for (String string : strings) {
            exprs = exprs.append(makeString(string));
        }
        return exprs;
    }

    JCBinary makeBinary(JCTree.Tag optag, JCExpression lhs, JCExpression rhs) {
        JCBinary tree = make.Binary(optag, lhs, rhs);
        tree.operator = operators.resolveBinary(tree, optag, lhs.type, rhs.type);
        tree.type = tree.operator.type.getReturnType();
        return tree;
    }

    MethodSymbol lookupMethod(DiagnosticPosition pos, Name name, Type qual, List<Type> args) {
        return rs.resolveInternalMethod(pos, env, qual, name, args, List.nil());
    }

    @Override
    public void visitClassDef(JCClassDecl tree) {
        ClassSymbol prevCurrentClass = currentClass;
        try {
            currentClass = tree.sym;
            super.visitClassDef(tree);
        } finally {
            currentClass = prevCurrentClass;
        }
    }

    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        MethodSymbol prevMethodSym = currentMethodSym;
        try {
            currentMethodSym = tree.sym;
            super.visitMethodDef(tree);
        } finally {
            currentMethodSym = prevMethodSym;
        }
    }

    public void visitStringTemplate(JCStringTemplate tree) {
        tree.expressions = translate(tree.expressions);

        List<LoadableConstant> staticArgValues = List.nil();
        List<Type> staticArgsTypes =
                List.of(syms.methodHandleLookupType, syms.stringType,
                        syms.methodTypeType);
        List<Type> expressionTypes = tree.expressions.stream()
                .map(arg -> arg.type == syms.botType ? syms.objectType : arg.type)
                .collect(List.collector());
        int slots = expressionTypes.stream()
                .mapToInt(t -> types.isSameType(t, syms.longType) ||
                        types.isSameType(t, syms.doubleType) ? 2 : 1).sum();
        if (200 < slots) { // StringConcatFactory.MAX_INDY_CONCAT_ARG_SLOTS
            JCNewArray fragmentArray = make.NewArray(make.Type(syms.stringType),
                    List.nil(), makeStringList(tree.fragments));
            fragmentArray.type = new ArrayType(syms.stringType, syms.arrayClass);
            JCNewArray valuesArray = make.NewArray(make.Type(syms.objectType),
                    List.nil(), tree.expressions);
            valuesArray.type = new ArrayType(syms.objectType, syms.arrayClass);
            result = bsmCall(tree.pos(), names.process, names.newLargeStringTemplate, syms.stringTemplateType,
                    List.of(fragmentArray, valuesArray),
                    List.of(fragmentArray.type, valuesArray.type),
                    staticArgValues, staticArgsTypes);
        } else {
            for (String fragment : tree.fragments) {
                staticArgValues = staticArgValues.append(LoadableConstant.String(fragment));
                staticArgsTypes = staticArgsTypes.append(syms.stringType);
            }
            result = bsmCall(tree.pos(), names.process, names.newStringTemplate, syms.stringTemplateType,
                    tree.expressions, expressionTypes, staticArgValues, staticArgsTypes);
        }
    }

    public void visitVarDef(JCVariableDecl tree) {
        MethodSymbol prevMethodSym = currentMethodSym;
        try {
            tree.mods = translate(tree.mods);
            tree.vartype = translate(tree.vartype);
            if (currentMethodSym == null) {
                // A class or instance field initializer.
                currentMethodSym =
                        new MethodSymbol((tree.mods.flags& Flags.STATIC) | Flags.BLOCK,
                                names.empty, null,
                                currentClass);
            }
            if (tree.init != null) tree.init = translate(tree.init);
            result = tree;
        } finally {
            currentMethodSym = prevMethodSym;
        }
    }

    private JCExpression bsmCall(DiagnosticPosition pos, Name name, Name bootstrapName, Type type,
                         List<JCExpression> args,
                         List<Type> argTypes,
                         List<LoadableConstant> staticArgValues,
                         List<Type> staticArgsTypes) {
        Symbol bsm = rs.resolveQualifiedMethod(pos, env,
                syms.templateRuntimeType, bootstrapName, staticArgsTypes, List.nil());
        MethodType indyType = new MethodType(argTypes, type, List.nil(), syms.methodClass);
        DynamicMethodSymbol dynSym = new DynamicMethodSymbol(
                name,
                syms.noSymbol,
                ((MethodSymbol)bsm).asHandle(),
                indyType,
                staticArgValues.toArray(new LoadableConstant[0])
        );
        JCFieldAccess qualifier = make.Select(make.Type(syms.processorType), dynSym.name);
        qualifier.sym = dynSym;
        qualifier.type = type;
        JCMethodInvocation apply = make.Apply(List.nil(), qualifier, args);
        apply.type = type;
        return apply;
    }

    public JCTree translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        try {
            this.make = make;
            this.env = env;
            translate(cdef);
        } finally {
            this.make = null;
            this.env = null;
        }

        return cdef;
    }
}
