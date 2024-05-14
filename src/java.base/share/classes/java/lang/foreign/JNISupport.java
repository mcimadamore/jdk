/*
 *  Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package java.lang.foreign;

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.MemorySessionImpl;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * This class provides useful functionalities to interact with native methods using the {@link Linker#nativeLinker() native
 * linker}.
 */
public class JNISupport {

    private JNISupport() {}

    /**
     * Obtains the {@code JNIEnv} associated with the current thread.
     * The returned segment is a zero-length native segment, associated with the global scope,
     * and accessible from the current thread.
     *
     * @return a zero-length native segment pointing at the {@code JNIEnv} associated with the current thread.
     */
    public static MemorySegment getJNIEnv() {
        long addr = getEnv0();
        return ((AbstractMemorySegmentImpl)MemorySegment.ofAddress(addr))
                .reinterpretInternal(JNISupport.class, 0L, MemorySessionImpl.createConfined(Thread.currentThread()), null);
    }

    /**
     * Create a new global JNI reference
     *
     * @param o the object to create a reference for
     * @param arena the arena in which the reference is created
     * @return the newly create reference
     */
    @SuppressWarnings("restricted")
    public static MemorySegment newGlobalRef(Object o, Arena arena) {
        long addr = newGlobalRef0(o);
        return MemorySegment.ofAddress(addr).reinterpret(arena, ms -> deleteGlobalRef0(ms.address()));
    }

    /**
     * Obtains the native method name associated with a native method declaration
     * @param clazz the class in which the native method is declared
     * @param name the native method name
     * @param methodType the native method type
     * @return the native method name associated with a native method declaration
     * @throws IllegalArgumentException if no native method with given name and type could be found in the provided class
     */
    public static String nativeMethodName(Class<?> clazz, String name, MethodType methodType) {
        int matches = 0;
        boolean found = false;
        for (Method m : clazz.getDeclaredMethods()) {
            if ((m.getModifiers() & Modifier.NATIVE) == 0) continue;
            if (m.getName().equals(name)) {
                matches++;
                if (List.of(m.getParameterTypes()).equals(methodType.parameterList()) &&
                        m.getReturnType().equals(methodType.returnType())) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            throw new IllegalArgumentException(
                    String.format("Native method %s.%s(%s) not found",
                            clazz.getName(), name, String.join(", ",
                                    methodType.parameterList().stream().map(Class::getName).toList())));
        }
        return matches > 1 ?
                longJNIMethodName(clazz, name, methodType) :
                shortJNIMethodName(clazz, name);
    }

    /**
     * Resolve a global JNI reference obtained from {@link #newGlobalRef}
     *
     * @param ref the JNI reference to resolve
     * @return the resolved object
     */
    public static Object resolveGlobalRef(MemorySegment ref) {
        MemorySessionImpl.checkValidState(ref);
        return resolveGlobalRef0(ref.address());
    }

    private static native void registerNatives();
    static {
        registerNatives();
    }

    private static native long getEnv0();
    private static native long newGlobalRef0(Object o);
    private static native Object resolveGlobalRef0(long addr);
    private static native void deleteGlobalRef0(long addr);

    private static String shortJNIMethodName(Class<?> clazz, String name) {
        String classDesc = clazz.descriptorString();
        return String.format("Java_%s_%s", escapeJNIString(classDesc.substring(1, classDesc.length() - 1)), escapeJNIString(name));
    }

    private static String longJNIMethodName(Class<?> clazz, String name, MethodType type) {
        String desc = type.descriptorString();
        return String.format("%s__%s", shortJNIMethodName(clazz, name),
                escapeJNIString(desc.substring(1, desc.indexOf(')'))));
    }

    // ported from nativeLookup.cpp
    private static String escapeJNIString(String string) {
        StringBuilder buf = new StringBuilder();
        boolean checkEscape = false;
        for (int i = 0 ; i < string.length() ; i++) {
            char c = string.charAt(i);
            if (c <= 0x7f && (Character.isAlphabetic(c) || Character.isDigit(c))) {
                if (checkEscape && (c >= '0' && c <= '3')) {
                    // This is a non-Java identifier and we won't escape it to
                    // ensure no name collisions with a Java identifier.
                    return null;
                }
                buf.append(c);
                checkEscape = false;
            } else {
                checkEscape = false;
                if (c == '_') buf.append("_1");
                else if (c == '/') {
                    buf.append("_");
                    // Following a / we must have non-escape character
                    checkEscape = true;
                }
                else if (c == ';') buf.append("_2");
                else if (c == '[') buf.append("_3");
                else               buf.append(String.format("_%x", (short)c));
            }
        }
        return buf.toString();
    }
}
