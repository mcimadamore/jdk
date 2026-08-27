/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @requires vm.compiler2.enabled
 * @summary Check getStable folding for var handles
 * @library /test/lib /
 * @run driver compiler.c2.irTests.stable.StableVarHandleTest
 */

package compiler.c2.irTests.stable;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class StableVarHandleTest {

    public static void main(String[] args) {
        TestFramework.run();
    }

    static class Carrier {
        static int staticField = 42;
        int field;

        public Carrier(int field) {
            this.field = field;
        }
    }

    static final VarHandle VH_INSTANCE_FIELD;
    static final VarHandle VH_STATIC_FIELD;
    static final VarHandle VH_ARRAY_ELEMENT;

    static final Carrier CARRIER = new Carrier(42);
    static final int[] ARR = { 42 };

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            VH_INSTANCE_FIELD = lookup.findVarHandle(Carrier.class, "field", int.class);
            VH_STATIC_FIELD = lookup.findStaticVarHandle(Carrier.class, "staticField", int.class);
            VH_ARRAY_ELEMENT = MethodHandles.arrayElementVarHandle(int[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static int testFoldInstanceField() {
        return (int) VH_INSTANCE_FIELD.getStable(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static int testFoldStaticField() {
        return (int) VH_STATIC_FIELD.getStable();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static int testFoldArrayElement() {
        return (int) VH_ARRAY_ELEMENT.getStable(ARR, 0);
    }
}
