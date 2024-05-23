
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

package java.sql;

/**
 * TBD
 *
 * @param <T> TBD
 */
public final class SQLArgument<T> extends SQLValue<T> {
    /**
     * TBD
     * @param value TBD
     * @param clazz TBD
     * @param targetSqlType TBD
     * @param scaleOrLength TBD
     * @param <T> TBD
     * @return TBD     */
    public static <T> SQLArgument<T> inout(T value, Class<T> clazz, SQLType targetSqlType, int scaleOrLength) {
        return new SQLArgument<>(value, clazz, targetSqlType, scaleOrLength);
    }

    /**
     * TBD
     * @param value TBD
     * @param targetSqlType TBD
     * @param scaleOrLength TBD
     * @param <T> TBD
     * @return TBD     */
    public static <T> SQLArgument<T> inout(T value, SQLType targetSqlType, int scaleOrLength) {
        return new SQLArgument<>(value, targetSqlType, scaleOrLength);
    }

    /**
     * TBD
     * @param value TBD
     * @param clazz TBD
     * @param targetSqlType TBD
     * @param <T> TBD
     * @return TBD
     */
    public static <T> SQLArgument<T> inout(T value, Class<T> clazz, SQLType targetSqlType) {
        return new SQLArgument<>(value, clazz, targetSqlType);
    }

    /**
     * TBD
     * @param value TBD
     * @param targetSqlType TBD
     * @param <T> TBD
     * @return TBD     */
    public static <T> SQLArgument<T> inout(T value, SQLType targetSqlType) {
        return new SQLArgument<>(value, targetSqlType);
    }

    /**
     * TBD
     * @param clazz TBD
     * @param targetSqlType TBD
     * @param <T> TBD
     * @return TBD     */
    public static <T> SQLArgument<T> out(Class<T> clazz, SQLType targetSqlType) {
        return new SQLArgument<>(clazz, targetSqlType);
    }

    /**
     * TBD
     * @param targetSqlType TBD
     * @param <T> TBD
     * @return TBD
     */
    public static <T> SQLArgument<T> out(SQLType targetSqlType) {
        return new SQLArgument<>(targetSqlType);
    }

    private final boolean in;
    private final Class<T> clazz;


    private SQLArgument(T value, SQLType targetSqlType, int scaleOrLength) {
        super(value, targetSqlType, scaleOrLength);
        this.clazz = null;
        this.in = true;
    }

    private SQLArgument(T value, SQLType targetSqlType) {
        super(value, targetSqlType);
        this.clazz = null;
        this.in = true;
    }

    private SQLArgument(T value, Class<T> clazz, SQLType targetSqlType, int scaleOrLength) {
        super(value, targetSqlType, scaleOrLength);
        this.clazz = clazz;
        this.in = true;
    }

    private SQLArgument(T value, Class<T> clazz, SQLType targetSqlType) {
        super(value, targetSqlType);
        this.clazz = clazz;
        this.in = true;
    }

    private SQLArgument(Class<T> clazz, SQLType targetSqlType) {
        super(null, targetSqlType);
        this.clazz = clazz;
        this.in = false;
    }

    private SQLArgument(SQLType targetSqlType) {
        super(null, targetSqlType);
        this.clazz = null;
        this.in = false;
    }

    /**
     * TBD
     * @return TBD
     */
    public Class<T> targetClass() {
        return clazz;
    }

    /**
     * TBD
     * @return TBD
     */
    public boolean in() {
        return in;
    }
}