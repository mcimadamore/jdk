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

package java.lang.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.TrustFinalFields;

import static java.util.Objects.requireNonNull;

/** Stable field accessor. */
@TrustFinalFields
public abstract class StableAccessor {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    final boolean isStatic;
    final Object base;
    final long offset;

    private StableAccessor(boolean isStatic, Object base, long offset) {
        this.isStatic = isStatic;
        this.base = base;
        this.offset = offset;
    }

    /**
     * Creates a stable accessor.
     * @param lookup the caller lookup
     * @param unusedName ignored bootstrap name
     * @param unusedAccessorType ignored bootstrap type
     * @param owner the field owner
     * @param fieldName the field name
     * @param fieldDescriptor the field descriptor
     * @return the accessor
     * @throws NoSuchFieldException if the field cannot be found
     * @throws IllegalAccessException if the field cannot be accessed
     */
    public static StableAccessor of(MethodHandles.Lookup lookup,
                                    String unusedName,
                                    Class<?> unusedAccessorType,
                                    Class<?> owner,
                                    String fieldName,
                                    String fieldDescriptor)
            throws NoSuchFieldException, IllegalAccessException {
        requireNonNull(owner);
        requireNonNull(fieldName);
        requireNonNull(fieldDescriptor);
        requireNonNull(lookup);

        Class<?> fieldType = fieldType(fieldDescriptor, owner.getClassLoader());

        Field field = owner.getDeclaredField(fieldName);
        if (field.getType() != fieldType) {
            throw new NoSuchFieldException(fieldName);
        }

        boolean isStatic = Modifier.isStatic(field.getModifiers());
        Object base;
        long offset;
        if (isStatic) {
            lookup.findStaticGetter(owner, fieldName, fieldType);
            base = UNSAFE.staticFieldBase(field);
            offset = UNSAFE.staticFieldOffset(field);
        } else {
            lookup.findGetter(owner, fieldName, fieldType);
            base = null;
            offset = UNSAFE.objectFieldOffset(field);
        }

        if (fieldType == boolean.class) {
            return new OfBoolean(isStatic, base, offset);
        } else if (fieldType == byte.class) {
            return new OfByte(isStatic, base, offset);
        } else if (fieldType == short.class) {
            return new OfShort(isStatic, base, offset);
        } else if (fieldType == char.class) {
            return new OfChar(isStatic, base, offset);
        } else if (fieldType == int.class) {
            return new OfInt(isStatic, base, offset);
        } else if (fieldType == long.class) {
            return new OfLong(isStatic, base, offset);
        } else if (fieldType == float.class) {
            return new OfFloat(isStatic, base, offset);
        } else if (fieldType == double.class) {
            return new OfDouble(isStatic, base, offset);
        } else {
            return new OfReference<>(fieldType, isStatic, base, offset);
        }
    }

    @ForceInline
    final Object resolveBase(Object receiver) {
        if (isStatic) {
            return base;
        } else {
            return requireNonNull(receiver);
        }
    }

    private static Class<?> fieldType(String fieldDescriptor, ClassLoader loader) {
        return MethodType.fromMethodDescriptorString("(" + fieldDescriptor + ")V", loader)
                .parameterType(0);
    }

    /** Boolean accessor. */
    public static final class OfBoolean extends StableAccessor {
        private OfBoolean(boolean isStatic, Object base, long offset) {
            super(isStatic, base, offset);
        }

        /**
         * Returns the field value.
         * @param receiver the receiver, ignored for static fields
         * @return the field value
         */
        @ForceInline
        public boolean get(Object receiver) {
            return UNSAFE.getBooleanStable(resolveBase(receiver), offset);
        }
    }

    /** Byte accessor. */
    public static final class OfByte extends StableAccessor {
        private OfByte(boolean isStatic, Object base, long offset) {
            super(isStatic, base, offset);
        }

        /**
         * Returns the field value.
         * @param receiver the receiver, ignored for static fields
         * @return the field value
         */
        @ForceInline
        public byte get(Object receiver) {
            return UNSAFE.getByteStable(resolveBase(receiver), offset);
        }
    }

    /** Short accessor. */
    public static final class OfShort extends StableAccessor {
        private OfShort(boolean isStatic, Object base, long offset) {
            super(isStatic, base, offset);
        }

        /**
         * Returns the field value.
         * @param receiver the receiver, ignored for static fields
         * @return the field value
         */
        @ForceInline
        public short get(Object receiver) {
            return UNSAFE.getShortStable(resolveBase(receiver), offset);
        }
    }

    /** Char accessor. */
    public static final class OfChar extends StableAccessor {
        private OfChar(boolean isStatic, Object base, long offset) {
            super(isStatic, base, offset);
        }

        /**
         * Returns the field value.
         * @param receiver the receiver, ignored for static fields
         * @return the field value
         */
        @ForceInline
        public char get(Object receiver) {
            return UNSAFE.getCharStable(resolveBase(receiver), offset);
        }
    }

    /** Int accessor. */
    public static final class OfInt extends StableAccessor {
        private OfInt(boolean isStatic, Object base, long offset) {
            super(isStatic, base, offset);
        }

        /**
         * Returns the field value.
         * @param receiver the receiver, ignored for static fields
         * @return the field value
         */
        @ForceInline
        public int get(Object receiver) {
            return UNSAFE.getIntStable(resolveBase(receiver), offset);
        }
    }

    /** Long accessor. */
    public static final class OfLong extends StableAccessor {
        private OfLong(boolean isStatic, Object base, long offset) {
            super(isStatic, base, offset);
        }

        /**
         * Returns the field value.
         * @param receiver the receiver, ignored for static fields
         * @return the field value
         */
        @ForceInline
        public long get(Object receiver) {
            return UNSAFE.getLongStable(resolveBase(receiver), offset);
        }
    }

    /** Float accessor. */
    public static final class OfFloat extends StableAccessor {
        private OfFloat(boolean isStatic, Object base, long offset) {
            super(isStatic, base, offset);
        }

        /**
         * Returns the field value.
         * @param receiver the receiver, ignored for static fields
         * @return the field value
         */
        @ForceInline
        public float get(Object receiver) {
            return UNSAFE.getFloatStable(resolveBase(receiver), offset);
        }
    }

    /** Double accessor. */
    public static final class OfDouble extends StableAccessor {
        private OfDouble(boolean isStatic, Object base, long offset) {
            super(isStatic, base, offset);
        }

        /**
         * Returns the field value.
         * @param receiver the receiver, ignored for static fields
         * @return the field value
         */
        @ForceInline
        public double get(Object receiver) {
            return UNSAFE.getDoubleStable(resolveBase(receiver), offset);
        }
    }

    /**
     * Reference accessor.
     * @param <T> the reference type
     */
    public static final class OfReference<T> extends StableAccessor {
        private final Class<T> referenceType;

        @SuppressWarnings("unchecked")
        private OfReference(Class<?> fieldType, boolean isStatic, Object base, long offset) {
            super(isStatic, base, offset);
            this.referenceType = (Class<T>) fieldType;
        }

        /**
         * Returns the field value.
         * @param receiver the receiver, ignored for static fields
         * @return the field value
         */
        @ForceInline
        public T get(Object receiver) {
            return referenceType.cast(UNSAFE.getReferenceStable(resolveBase(receiver), offset));
        }
    }
}
