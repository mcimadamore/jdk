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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;


final class SQLTemplateSupport {
    private SQLTemplateSupport() {}

    public interface Fn<T, R> { R apply(T x) throws SQLException; }
    public interface Sup<T> { T get() throws SQLException; }

    public static PreparedStatement prepareStatement(StringTemplate st, Connection conn, Fn<String, ? extends PreparedStatement> f)
        throws SQLException {
        var ps = getPrepareStatement(st, conn, () -> f.apply(query(st)));
        setValues(ps, st.values());
        return ps;
    }

    public static boolean call(StringTemplate st, Connection conn, Fn<String, CallableStatement> f)
        throws SQLException {
        var cs = (CallableStatement)prepareStatement(st, conn, f);
        boolean res = cs.execute();
        if (res)
            SQLTemplateSupport.getValues(cs, st.values());
        return res;
    }

    private static String query(StringTemplate st) {
        return String.join("?", st.fragments());
    }

    private static PreparedStatement getPrepareStatement(StringTemplate st, Connection conn, Sup<PreparedStatement> supplier)
        throws SQLException {
        var map = st.getMetaData(SQLTemplateSupport.class, () ->
            Collections.synchronizedMap(new HashMap<Connection, PreparedStatement>()));

        if (map == null)
            return supplier.get();

        try {
            return map.computeIfAbsent(conn, k -> {
                try {
                    return supplier.get();
                } catch (SQLException sqlEx) {
                    throw new RuntimeException(sqlEx);
                }
            });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof SQLException sqlEx) throw sqlEx;
            throw ex;
        }
    }

    private static void setValues(PreparedStatement ps, List<?> values) throws SQLException {
        int i = 1;
        for (Object value : values) {
            if (value instanceof SQLArgument<?> sa) {
                if (ps instanceof CallableStatement cs)
                    cs.registerOutParameter(i, sa.targetSqlType());
                if (!sa.in())
                    continue;
            }
            if (value instanceof SQLValue<?> sv) {
                if (sv.hasScaleOrLength())
                    ps.setObject(i, sv.value(), sv.targetSqlType(), sv.scaleOrLength());
                else if (sv.targetSqlType() != null)
                    ps.setObject(i, sv.value(), sv.targetSqlType());
                else
                    ps.setObject(i, sv.value());
            } else {
                ps.setObject(i, value);
            }
            i++;
        }
    }

    @SuppressWarnings("unchecked")
    private static void getValues(CallableStatement cs, List<?> values) throws SQLException {
        int i = 1;
        for (Object value : values) {
            if (value instanceof SQLArgument<?> arg) {
                if (arg.targetClass() != null)
                    ((SQLArgument<Object>)arg).setValue(cs.getObject(i, arg.targetClass()));
                else
                    ((SQLArgument<Object>)arg).setValue(cs.getObject(i));
            }
            i++;
        }
    }
}