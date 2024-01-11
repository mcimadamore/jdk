/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm TestGuards
 */

import java.lang.foreign.*;
import java.lang.foreign.MemoryLayout.PathElement;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.function.Function;

import org.testng.annotations.*;
import static org.testng.Assert.*;

public class TestGuards {

    static final VarHandle A_HANDLE = ValueLayout.ADDRESS.varHandle().withInvokeExactBehavior();
    static final VarHandle J_HANDLE = ValueLayout.JAVA_LONG.varHandle().withInvokeExactBehavior();

    @Test
    public void testAccess() {
        MemorySegment segment = Arena.ofAuto().allocate(100);
        A_HANDLE.set(segment, 0L, MemorySegment.NULL);
        //J_HANDLE.set(segment, 0L, 0L);
    }
}
