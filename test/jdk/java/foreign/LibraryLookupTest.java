/*
 *  Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
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
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */

import org.testng.annotations.Test;

import java.lang.foreign.ScopedArena;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.*;

/*
 * @test
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED LibraryLookupTest
 */
public class LibraryLookupTest {

    static final Path JAVA_LIBRARY_PATH = Path.of(System.getProperty("java.library.path"));
    static final MethodHandle INC = Linker.nativeLinker().downcallHandle(FunctionDescriptor.ofVoid());
    static final Path LIB_PATH = JAVA_LIBRARY_PATH.resolve(System.mapLibraryName("LibraryLookup"));

    @Test
    void testLoadLibraryConfined() {
        try (ScopedArena arena0 = ScopedArena.openConfined()) {
            callFunc(loadLibrary(arena0));
            try (ScopedArena arena1 = ScopedArena.openConfined()) {
                callFunc(loadLibrary(arena1));
                try (ScopedArena arena2 = ScopedArena.openConfined()) {
                    callFunc(loadLibrary(arena2));
                }
            }
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    void testLoadLibraryConfinedClosed() {
        MemorySegment addr;
        try (ScopedArena arena = ScopedArena.openConfined()) {
            addr = loadLibrary(arena);
        }
        callFunc(addr);
    }

    private static MemorySegment loadLibrary(Arena session) {
        SymbolLookup lib = SymbolLookup.libraryLookup(LIB_PATH, session);
        MemorySegment addr = lib.find("inc").get();
        return addr;
    }

    private static void callFunc(MemorySegment addr) {
        try {
            INC.invokeExact(addr);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    static final int ITERATIONS = 100;
    static final int MAX_EXECUTOR_WAIT_SECONDS = 20;
    static final int NUM_ACCESSORS = Math.min(10, Runtime.getRuntime().availableProcessors());

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testBadLibraryLookupName() {
        SymbolLookup.libraryLookup("nonExistent", Arena.global());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testBadLibraryLookupPath() {
        SymbolLookup.libraryLookup(Path.of("nonExistent"), Arena.global());
    }

    @Test
    void testLoadLibraryShared() throws Throwable {
        ExecutorService accessExecutor = Executors.newCachedThreadPool();
        for (int i = 0; i < NUM_ACCESSORS ; i++) {
            accessExecutor.execute(new LibraryLoadAndAccess());
        }
        accessExecutor.shutdown();
        assertTrue(accessExecutor.awaitTermination(MAX_EXECUTOR_WAIT_SECONDS, TimeUnit.SECONDS));
    }

    static class LibraryLoadAndAccess implements Runnable {
        @Override
        public void run() {
            for (int i = 0 ; i < ITERATIONS ; i++) {
                try (ScopedArena arena = ScopedArena.openConfined()) {
                    callFunc(loadLibrary(arena));
                }
            }
        }
    }

    @Test
    void testLoadLibrarySharedClosed() throws Throwable {
        ScopedArena arena = ScopedArena.openShared();
        MemorySegment addr = loadLibrary(arena);
        ExecutorService accessExecutor = Executors.newCachedThreadPool();
        for (int i = 0; i < NUM_ACCESSORS ; i++) {
            accessExecutor.execute(new LibraryAccess(addr));
        }
        while (true) {
            try {
                arena.close();
                break;
            } catch (IllegalStateException ex) {
                // wait for addressable parameter to be released
                Thread.onSpinWait();
            }
        }
        accessExecutor.shutdown();
        assertTrue(accessExecutor.awaitTermination(MAX_EXECUTOR_WAIT_SECONDS, TimeUnit.SECONDS));
    }

    static class LibraryAccess implements Runnable {

        final MemorySegment addr;

        LibraryAccess(MemorySegment addr) {
            this.addr = addr;
        }

        @Override
        public void run() {
            for (int i = 0 ; i < ITERATIONS ; i++) {
                try {
                    callFunc(addr);
                } catch (IllegalStateException ex) {
                    // library closed
                    break;
                }
            }
        }
    }
}
