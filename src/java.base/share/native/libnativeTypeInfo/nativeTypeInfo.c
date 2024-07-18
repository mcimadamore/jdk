/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "jdk_internal_foreign_abi_NativeTypeInfo.h"

#include <stdint.h>
#include <stdalign.h>
#include <stdlib.h>
#include <wchar.h>

#define IS_UNSIGNED(T) (T)0 < (T)-1

JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_NativeTypeInfo_sizeof_1short(JNIEnv* env, jclass cls) {
  return sizeof(short);
}

JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_NativeTypeInfo_sizeof_1int(JNIEnv* env, jclass cls) {
  return sizeof(int);
}

JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_NativeTypeInfo_sizeof_1long(JNIEnv* env, jclass cls) {
  return sizeof(long);
}

JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_NativeTypeInfo_sizeof_1wchar(JNIEnv* env, jclass cls) {
  return sizeof(wchar_t);
}

JNIEXPORT jboolean JNICALL
Java_jdk_internal_foreign_abi_NativeTypeInfo_signof_1wchar(JNIEnv* env, jclass cls) {
  return IS_UNSIGNED(wchar_t);
}

JNIEXPORT jboolean JNICALL
Java_jdk_internal_foreign_abi_NativeTypeInfo_signof_1char(JNIEnv* env, jclass cls) {
  return IS_UNSIGNED(char);
}

JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_NativeTypeInfo_alignof_1long_1long(JNIEnv* env, jclass cls) {
  return alignof(long long);
}

JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_NativeTypeInfo_alignof_1double(JNIEnv* env, jclass cls) {
  return alignof(double);
}
