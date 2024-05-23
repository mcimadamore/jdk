
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
public sealed class SQLValue<T>
    permits SQLArgument {
    private T value;
    private final SQLType targetSqlType;
    private final int scaleOrLength;
    private final boolean hasScaleOrLength;

    /**
     * TBD
     *
     * @param value TBD
     * @param targetSqlType TBD
     * @param scaleOrLength TBD
     */
    protected SQLValue(T value, SQLType targetSqlType, int scaleOrLength) {
        this.value = value;
        this.targetSqlType = targetSqlType;
        this.scaleOrLength = scaleOrLength;
        this.hasScaleOrLength = true;
    }

    /**
     * TBD
     * @param value TBD
     * @param targetSqlType TBD
     */
    protected SQLValue(T value, SQLType targetSqlType) {
        this.value = value;
        this.targetSqlType = targetSqlType;
        this.scaleOrLength = -1;
        this.hasScaleOrLength = false;
    }

    /**
     * TBD
     *
     * @param x TBD
     * @param targetSqlType TBD
     * @param scaleOrLength TBD
     * @param <T> TBD
     * @return TBD
     */
    public static <T> SQLValue<T> of(T x, SQLType targetSqlType, int scaleOrLength) {
        return new SQLValue<>(x, targetSqlType, scaleOrLength);
    }

    /**
     * TBD
     *
     * @param x TBD
     * @param targetSqlType TBD
     * @param <T> TBD
     * @return TBD
     */
    public static <T> SQLValue<T> of(T x, SQLType targetSqlType) {
        return new SQLValue<>(x, targetSqlType);
    }

    /**
     * TBD
     * @return TBD
     */
    public T value() {
        return value;
    }

    /**
     * TBD
     * @return TBD
     */
    public SQLType targetSqlType() {
        return targetSqlType;
    }

    /**
     * TBD
     * @return TBD
     */
    public int scaleOrLength() {
        return scaleOrLength;
    }

    /**
     * TBD
     * @return TBD
     */
    public boolean hasScaleOrLength() {
        return hasScaleOrLength;
    }

    /**
     * TBD
     * @param value TBD
     */
    protected void setValue(T value) {
        this.value = value;
    }
}