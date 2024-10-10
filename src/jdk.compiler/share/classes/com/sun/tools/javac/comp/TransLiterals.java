/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.DynamicMethodSymbol;
import com.sun.tools.javac.code.Symbol.DynamicVarSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.stream.Collectors;

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
        names = Names.instance(context);
    }

    JCExpression makeLit(Type type, Object value) {
        return make.Literal(type.getTag(), value).setType(type.constType(value));
    }

    JCExpression makeString(String string) {
        return makeLit(syms.stringType, string);
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

    final class TransStringTemplate {
        final JCStringTemplate tree;
        final List<String> fragments;
        final List<JCExpression> expressions;
        final List<Type> expressionTypes;
        final List<List<Attribute.Compound>> annotations;

        TransStringTemplate(JCStringTemplate tree) {
            this.tree = tree;
            this.fragments = tree.fragments;
            this.expressions = translate(tree.expressions);
            this.expressionTypes = expressions.stream()
                    .map(arg -> arg.type == syms.botType ? syms.objectType : arg.type)
                    .collect(List.collector());
            this.annotations = tree.annotationTargets.map(t -> t.getAnnotationMirrors());
        }

        JCExpression bsmCall(Name name, Name bootstrapName, Type type,
                             List<JCExpression> args,
                             List<Type> argTypes,
                             List<LoadableConstant> staticArgValues,
                             List<Type> staticArgsTypes) {
            List<Type> lookupArgtypes = staticArgsTypes.prependList(
                    List.of(syms.methodHandleLookupType, syms.stringType, syms.methodTypeType));
            Symbol bsm = rs.resolveQualifiedMethod(tree.pos(), env,
                    syms.templateRuntimeType, bootstrapName, lookupArgtypes, List.nil());
            MethodType indyType = new MethodType(argTypes, type, List.nil(), syms.methodClass);
            DynamicMethodSymbol dynSym = new DynamicMethodSymbol(
                    name,
                    syms.noSymbol,
                    ((MethodSymbol)bsm).asHandle(),
                    indyType,
                    staticArgValues.toArray(LoadableConstant[]::new)
            );
            JCFieldAccess qualifier = make.Select(make.Type(syms.templateRuntimeType), dynSym.name);
            qualifier.sym = dynSym;
            qualifier.type = type;
            JCMethodInvocation apply = make.Apply(List.nil(), qualifier, args);
            apply.type = type;
            return apply;
        }

        DynamicVarSymbol bsmConstantSymbol(Name name, Name bootstrapName, Type type,
                                 List<LoadableConstant> staticArgValues,
                                 List<Type> staticArgsTypes) {
            List<Type> lookupArgtypes = staticArgsTypes.prependList(
                    List.of(syms.methodHandleLookupType, syms.stringType, types.erasure(syms.classType)));
            Symbol bsm = rs.resolveQualifiedMethod(tree.pos(), env,
                    syms.constantBootstrapsType, bootstrapName, lookupArgtypes, List.nil());
            return new DynamicVarSymbol(
                    name,
                    syms.noSymbol,
                    ((MethodSymbol)bsm).asHandle(),
                    type,
                    staticArgValues.toArray(LoadableConstant[]::new)
            );
        }

        JCExpression newStringTemplate() {
            List<LoadableConstant> staticArgValues = List.nil();
            List<Type> staticArgTypes = List.nil();
            staticArgValues = staticArgValues.append(makeAnnotations());
            staticArgTypes = staticArgTypes.append(
                    types.makeArrayType(types.makeArrayType(syms.annotationType)));
            for (String fragment : fragments) {
                staticArgValues = staticArgValues.append(LoadableConstant.String(fragment));
                staticArgTypes = staticArgTypes.append(syms.stringType);
            }
            return bsmCall(names.process, names.newStringTemplate, syms.stringTemplateType,
                    expressions, expressionTypes, staticArgValues, staticArgTypes);
        }

        LoadableConstant makeAnnotations() {
            ListBuffer<LoadableConstant> allAnnotations = new ListBuffer<>();
            for (List<Attribute.Compound> valueAnnotations : annotations) {
                allAnnotations.add(makeArray(types.makeArrayType(syms.annotationType),
                        valueAnnotations.map(this::makeAnnotation)));
            }
            return makeArray(
                    types.makeArrayType(types.makeArrayType(syms.annotationType)),
                    allAnnotations.toList()
            );
        }

        LoadableConstant makeAnnotation(Attribute.Compound annotation) {
            ListBuffer<LoadableConstant> staticArgValues = new ListBuffer<>();
            ListBuffer<Type> staticArgTypes = new ListBuffer<>();
            String attributeNames = annotation.getElementValues().keySet().stream()
                            .map(sym -> sym.name.toString())
                            .collect(Collectors.joining(";"));
            staticArgValues.add(LoadableConstant.String(attributeNames));
            staticArgTypes.add(syms.stringType);
            for (Attribute attribute : annotation.getElementValues().values()) {
                staticArgValues.add(makeAttributeValue(attribute));
                staticArgTypes.add(types.boxedTypeOrType(attribute.type));
            }
            return bsmConstantSymbol(names.fromString("annotation"), names.fromString("makeAnnotation"), annotation.type,
                    staticArgValues.toList(), staticArgTypes.toList());
        }

        LoadableConstant makeAttributeValue(Attribute attribute) {
            if (attribute instanceof Attribute.Constant constantAttribute) {
                return switch (attribute.type.getTag()) {
                    case BOOLEAN, CHAR, SHORT, INT -> LoadableConstant.Int((Integer)constantAttribute.value);
                    case LONG -> LoadableConstant.Long((Long)constantAttribute.value);
                    case FLOAT -> LoadableConstant.Float((Float)constantAttribute.value);
                    case DOUBLE -> LoadableConstant.Double((Double)constantAttribute.value);
                    case CLASS -> LoadableConstant.String((String)constantAttribute.value);
                    default -> throw new IllegalStateException("Cannot get here: " + attribute);
                };
            } else if (attribute instanceof Attribute.Class classAttribute) {
                return (ClassType)classAttribute.classType;
            } else if (attribute instanceof Attribute.Enum enumAttribute) {
                return bsmConstantSymbol(enumAttribute.value.name, names.fromString("enumConstant"), enumAttribute.type,
                        List.nil(), List.nil());
            } else if (attribute instanceof Attribute.Compound compoundAttribute) {
                return makeAnnotation(compoundAttribute);
            } else if (attribute instanceof Attribute.Array arrayAttribute) {
                List<LoadableConstant> elementAttributes =
                        arrayAttribute.getValue().map(this::makeAttributeValue);
                return makeArray(arrayAttribute.type, elementAttributes);
            } else {
                throw new UnsupportedOperationException("Unsupported attribute: " + attribute);
            }
        }

        LoadableConstant makeArray(Type arrayType, List<LoadableConstant> elements) {
            return bsmConstantSymbol(names.fromString("array"), names.fromString("makeArray"), arrayType,
                    elements, List.fill(elements.length(), types.elemtype(arrayType)));
        }

        JCExpression visit() {
            make.at(tree.pos);
            return newStringTemplate();
        }
    }

    public void visitStringTemplate(JCStringTemplate tree) {
        int prevPos = make.pos;
        try {
            tree.expressions = translate(tree.expressions);
            TransStringTemplate transStringTemplate = new TransStringTemplate(tree);
            result = transStringTemplate.visit();
        } catch (Throwable ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            make.at(prevPos);
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
