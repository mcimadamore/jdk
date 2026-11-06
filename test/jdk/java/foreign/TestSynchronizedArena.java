/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Monitor guarded arena access and holdsLock intrinsic fallback
 * @run main/othervm TestSynchronizedArena
 * @run main/othervm -Xint TestSynchronizedArena
 * @run main/othervm -XX:-TieredCompilation TestSynchronizedArena
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:DisableIntrinsic=_holdsLock TestSynchronizedArena
 */

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.FutureTask;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public class TestSynchronizedArena {
    static final Object OTHER = new Object();

    static void check(Object monitor, boolean expected) {
        if (Thread.holdsLock(monitor) != expected) throw new AssertionError("ownership");
    }

    static void nested(Object[] monitors, int depth) {
        if (depth == monitors.length) {
            for (Object monitor : monitors) check(monitor, true);
        } else {
            synchronized (monitors[depth]) { nested(monitors, depth + 1); }
        }
    }

    static void exercise() throws Exception {
        Object monitor = new Object();
        Arena arena = Arena.ofSynchronized(monitor);
        MemorySegment segment;
        try {
            arena.allocate(4);
            throw new AssertionError("unlocked allocation");
        } catch (WrongThreadException expected) { }
        synchronized (monitor) {
            segment = arena.allocate(JAVA_INT);
            segment.set(JAVA_INT, 0, 42);
            for (int i = 0; i < 100_000; i++) {
                check(monitor, true);
                if (segment.get(JAVA_INT, 0) != 42) throw new AssertionError();
            }
            synchronized (monitor) { check(monitor, true); }
            check(OTHER, false);
            // wait inflates the monitor, exercising the runtime fallback.
            monitor.wait(1);
            for (int i = 0; i < 100_000; i++) check(monitor, true);
            System.gc();
            check(monitor, true);
        }
        check(monitor, false);
        try {
            segment.get(JAVA_INT, 0);
            throw new AssertionError("unlocked access");
        } catch (WrongThreadException expected) { }
        try {
            arena.close();
            throw new AssertionError("unlocked close");
        } catch (WrongThreadException expected) { }
        synchronized (monitor) { arena.close(); }
        Object[] monitors = new Object[20];
        for (int i = 0; i < monitors.length; i++) monitors[i] = new Object();
        for (int i = 0; i < 10_000; i++) nested(monitors, 0);
        for (Object m : monitors) check(m, false);
        try {
            Thread.holdsLock(null);
            throw new AssertionError("null");
        } catch (NullPointerException expected) { }
    }

    public static void main(String[] args) throws Exception {
        exercise();
        FutureTask<Void> task = new FutureTask<>(() -> { exercise(); return null; });
        Thread thread = Thread.ofVirtual().start(task);
        task.get();
        thread.join();
    }
}
