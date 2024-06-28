/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 0000000
 * @summary Exercise format builder.
 * @enablePreview true
 */

import java.util.Objects;
import java.util.Locale;
import java.util.MissingFormatArgumentException;
import java.util.UnknownFormatConversionException;

public class FormatterBuilder {
    public static void main(String... args) {
        Locale.setDefault(Locale.US);
        suite(Locale.US);
        Locale thai = Locale.forLanguageTag("th-TH-u-nu-thai");
        Locale.setDefault(thai);
        suite(thai);
    }

    static void test(String a, StringTemplate b) {
        Locale l = Locale.getDefault();
        String r = String.format(l, b);
        if (!Objects.equals(a, r)) {
            throw new RuntimeException("format and String.format do not match: " + a + " : " + r);
        }
    }

    public interface Executable {
        void execute() throws Throwable;
    }

    static <T extends Throwable> void assertThrows(Class<T> expectedType, Executable executable, String message) {
        Throwable actualException = null;
        try {
            executable.execute();
        } catch (Throwable e) {
            actualException = e;
        }
        if (actualException == null) {
            throw new RuntimeException("Expected " + expectedType + " to be thrown, but nothing was thrown.");
        }
        if (!expectedType.isInstance(actualException)) {
            throw new RuntimeException("Expected " + expectedType + " to be thrown, but was thrown " + actualException.getClass());
        }
        if (message != null && !message.equals(actualException.getMessage())) {
            throw new RuntimeException("Expected " + message + " to be thrown, but was thrown " + actualException.getMessage());
        }
    }

    static void suite(Locale l) {
        Object nullObject = null;
        test(String.format(l, "%b", false), "%b\{false}");
        test(String.format(l, "%b", true), "%b\{true}");
        test(String.format(l, "%10b", false), "%10b\{false}");
        test(String.format(l, "%10b", true), "%10b\{true}");
        test(String.format(l, "%-10b", false), "%-10b\{false}");
        test(String.format(l, "%-10b", true), "%-10b\{true}");
        test(String.format(l, "%B", false), "%B\{false}");
        test(String.format(l, "%B", true), "%B\{true}");
        test(String.format(l, "%10B", false), "%10B\{false}");
        test(String.format(l, "%10B", true), "%10B\{true}");
        test(String.format(l, "%-10B", false), "%-10B\{false}");
        test(String.format(l, "%-10B", true), "%-10B\{true}");

        // utf16
        test(String.format(l, "\u8336%b", false), "\u8336%b\{false}");
        test(String.format(l, "\u8336%b", true), "\u8336%b\{true}");
        test(String.format(l, "\u8336%10b", false), "\u8336%10b\{false}");
        test(String.format(l, "\u8336%10b", true), "\u8336%10b\{true}");
        test(String.format(l, "\u8336%-10b", false), "\u8336%-10b\{false}");
        test(String.format(l, "\u8336%-10b", true), "\u8336%-10b\{true}");
        test(String.format(l, "\u8336%B", false), "\u8336%B\{false}");
        test(String.format(l, "\u8336%B", true), "\u8336%B\{true}");
        test(String.format(l, "\u8336%10B", false), "\u8336%10B\{false}");
        test(String.format(l, "\u8336%10B", true), "\u8336%10B\{true}");
        test(String.format(l, "\u8336%-10B", false), "\u8336%-10B\{false}");
        test(String.format(l, "\u8336%-10B", true), "\u8336%-10B\{true}");

        test(String.format(l, "%h", 12345), "%h\{12345}");
        test(String.format(l, "%h", 0xABCDE), "%h\{0xABCDE}");
        test(String.format(l, "%10h", 12345), "%10h\{12345}");
        test(String.format(l, "%10h", 0xABCDE), "%10h\{0xABCDE}");
        test(String.format(l, "%-10h", 12345), "%-10h\{12345}");
        test(String.format(l, "%-10h", 0xABCDE), "%-10h\{0xABCDE}");
        test(String.format(l, "%H", 12345), "%H\{12345}");
        test(String.format(l, "%H", 0xABCDE), "%H\{0xABCDE}");
        test(String.format(l, "%10H", 12345), "%10H\{12345}");
        test(String.format(l, "%10H", 0xABCDE), "%10H\{0xABCDE}");
        test(String.format(l, "%-10H", 12345), "%-10H\{12345}");
        test(String.format(l, "%-10H", 0xABCDE), "%-10H\{0xABCDE}");

        test(String.format(l, "%s", (byte)0xFF), "%s\{(byte)0xFF}");
        test(String.format(l, "%s", (short)0xFFFF), "%s\{(short)0xFFFF}");
        test(String.format(l, "%s", 12345), "%s\{12345}");
        test(String.format(l, "%s", 12345L), "%s\{12345L}");
        test(String.format(l, "%s", 1.33f), "%s\{1.33f}");
        test(String.format(l, "%s", 1.33), "%s\{1.33}");
        test(String.format(l, "%s", "abcde"), "%s\{"abcde"}");
        test(String.format(l, "%s", nullObject), "%s\{nullObject}");
        test(String.format(l, "\u8336%s", nullObject), "\u8336%s\{nullObject}"); // utf16
        test(String.format(l, "%10s", (byte)0xFF), "%10s\{(byte)0xFF}");
        test(String.format(l, "%10s", (short)0xFFFF), "%10s\{(short)0xFFFF}");
        test(String.format(l, "%10s", 12345), "%10s\{12345}");
        test(String.format(l, "%10s", 12345L), "%10s\{12345L}");
        test(String.format(l, "%10s", 1.33f), "%10s\{1.33f}");
        test(String.format(l, "%10s", 1.33), "%10s\{1.33}");
        test(String.format(l, "%10s", "abcde"), "%10s\{"abcde"}");
        test(String.format(l, "%10s", nullObject), "%10s\{nullObject}");
        test(String.format(l, "%-10s", (byte)0xFF), "%-10s\{(byte)0xFF}");
        test(String.format(l, "%-10s", (short)0xFFFF), "%-10s\{(short)0xFFFF}");
        test(String.format(l, "%-10s", 12345), "%-10s\{12345}");
        test(String.format(l, "%-10s", 12345L), "%-10s\{12345L}");
        test(String.format(l, "%-10s", 1.33f), "%-10s\{1.33f}");
        test(String.format(l, "%-10s", 1.33), "%-10s\{1.33}");
        test(String.format(l, "%-10s", "abcde"), "%-10s\{"abcde"}");
        test(String.format(l, "%-10s", nullObject), "%-10s\{nullObject}");
        test(String.format(l, "%S", (byte)0xFF), "%S\{(byte)0xFF}");
        test(String.format(l, "%S", (short)0xFFFF), "%S\{(short)0xFFFF}");
        test(String.format(l, "%S", 12345), "%S\{12345}");
        test(String.format(l, "%S", 12345L), "%S\{12345L}");
        test(String.format(l, "%S", 1.33f), "%S\{1.33f}");
        test(String.format(l, "%S", 1.33), "%S\{1.33}");
        test(String.format(l, "%S", "abcde"), "%S\{"abcde"}");
        test(String.format(l, "%S", nullObject), "%S\{nullObject}");
        test(String.format(l, "%10S", (byte)0xFF), "%10S\{(byte)0xFF}");
        test(String.format(l, "%10S", (short)0xFFFF), "%10S\{(short)0xFFFF}");
        test(String.format(l, "%10S", 12345), "%10S\{12345}");
        test(String.format(l, "%10S", 12345L), "%10S\{12345L}");
        test(String.format(l, "%10S", 1.33f), "%10S\{1.33f}");
        test(String.format(l, "%10S", 1.33), "%10S\{1.33}");
        test(String.format(l, "%10S", "abcde"), "%10S\{"abcde"}");
        test(String.format(l, "%10S", nullObject), "%10S\{nullObject}");
        test(String.format(l, "%-10S", (byte)0xFF), "%-10S\{(byte)0xFF}");
        test(String.format(l, "%-10S", (short)0xFFFF), "%-10S\{(short)0xFFFF}");
        test(String.format(l, "%-10S", 12345), "%-10S\{12345}");
        test(String.format(l, "%-10S", 12345L), "%-10S\{12345L}");
        test(String.format(l, "%-10S", 1.33f), "%-10S\{1.33f}");
        test(String.format(l, "%-10S", 1.33), "%-10S\{1.33}");
        test(String.format(l, "%-10S", "abcde"), "%-10S\{"abcde"}");
        test(String.format(l, "%-10S", nullObject), "%-10S\{nullObject}");

        test(String.format(l, "%c", 'a'), "%c\{'a'}");
        test(String.format(l, "\u8336%c", 'a'), "\u8336%c\{'a'}"); // utf16
        test(String.format(l, "%10c", 'a'), "%10c\{'a'}");
        test(String.format(l, "%-10c", 'a'), "%-10c\{'a'}");
        test(String.format(l, "%C", 'a'), "%C\{'a'}");
        test(String.format(l, "%10C", 'a'), "%10C\{'a'}");
        test(String.format(l, "%-10C", 'a'), "%-10C\{'a'}");

        test(String.format(l, "%d", -12345), "%d\{-12345}");
        test(String.format(l, "%d", 0), "%d\{0}");
        test(String.format(l, "%d", 12345), "%d\{12345}");
        test(String.format(l, "%10d", -12345), "%10d\{-12345}");
        test(String.format(l, "\u8336%10d", -12345), "\u8336%10d\{-12345}"); // utf16
        test(String.format(l, "%10d", 0), "%10d\{0}");
        test(String.format(l, "%10d", 12345), "%10d\{12345}");
        test(String.format(l, "%-10d", -12345), "%-10d\{-12345}");
        test(String.format(l, "%-10d", 0), "%-10d\{0}");
        test(String.format(l, "%-10d", 12345), "%-10d\{12345}");
        test(String.format(l, "%,d", -12345), "%,d\{-12345}");
        test(String.format(l, "%,d", 0), "%,d\{0}");
        test(String.format(l, "%,d", 12345), "%,d\{12345}");
        test(String.format(l, "%,10d", -12345), "%,10d\{-12345}");
        test(String.format(l, "%,10d", 0), "%,10d\{0}");
        test(String.format(l, "%,10d", 12345), "%,10d\{12345}");
        test(String.format(l, "%,-10d", -12345), "%,-10d\{-12345}");
        test(String.format(l, "%,-10d", 0), "%,-10d\{0}");
        test(String.format(l, "%,-10d", 12345), "%,-10d\{12345}");
        test(String.format(l, "%010d", -12345), "%010d\{-12345}");
        test(String.format(l, "%010d", 0), "%010d\{0}");
        test(String.format(l, "%010d", 12345), "%010d\{12345}");
        test(String.format(l, "%,010d", -12345), "%,010d\{-12345}");
        test(String.format(l, "%,010d", 0), "%,010d\{0}");
        test(String.format(l, "%,010d", 12345), "%,010d\{12345}");

        test(String.format(l, "%d", -12345), "%d\{-12345}");
        test(String.format(l, "%d", 0), "%d\{0}");
        test(String.format(l, "%d", 12345), "%d\{12345}");
        test(String.format(l, "%10d", -12345), "%10d\{-12345}");
        test(String.format(l, "%10d", 0), "%10d\{0}");
        test(String.format(l, "%10d", 12345), "%10d\{12345}");
        test(String.format(l, "%-10d", -12345), "%-10d\{-12345}");
        test(String.format(l, "%-10d", 0), "%-10d\{0}");
        test(String.format(l, "%-10d", 12345), "%-10d\{12345}");
        test(String.format(l, "%,d", -12345), "%,d\{-12345}");
        test(String.format(l, "%,d", 0), "%,d\{0}");
        test(String.format(l, "%,d", 12345), "%,d\{12345}");
        test(String.format(l, "%,10d", -12345), "%,10d\{-12345}");
        test(String.format(l, "%,10d", 0), "%,10d\{0}");
        test(String.format(l, "%,10d", 12345), "%,10d\{12345}");
        test(String.format(l, "%,-10d", -12345), "%,-10d\{-12345}");
        test(String.format(l, "%,-10d", 0), "%,-10d\{0}");
        test(String.format(l, "%,-10d", 12345), "%,-10d\{12345}");
        test(String.format(l, "% d", -12345), "% d\{-12345}");
        test(String.format(l, "% d", 0), "% d\{0}");
        test(String.format(l, "% d", 12345), "% d\{12345}");
        test(String.format(l, "% 10d", -12345), "% 10d\{-12345}");
        test(String.format(l, "% 10d", 0), "% 10d\{0}");
        test(String.format(l, "% 10d", 12345), "% 10d\{12345}");
        test(String.format(l, "% -10d", -12345), "% -10d\{-12345}");
        test(String.format(l, "% -10d", 0), "% -10d\{0}");
        test(String.format(l, "% -10d", 12345), "% -10d\{12345}");
        test(String.format(l, "%, d", -12345), "%, d\{-12345}");
        test(String.format(l, "%, d", 0), "%, d\{0}");
        test(String.format(l, "%, d", 12345), "%, d\{12345}");
        test(String.format(l, "%, 10d", -12345), "%, 10d\{-12345}");
        test(String.format(l, "%, 10d", 0), "%, 10d\{0}");
        test(String.format(l, "%, 10d", 12345), "%, 10d\{12345}");
        test(String.format(l, "%, -10d", -12345), "%, -10d\{-12345}");
        test(String.format(l, "%, -10d", 0), "%, -10d\{0}");
        test(String.format(l, "%, -10d", 12345), "%, -10d\{12345}");
        test(String.format(l, "%010d", -12345), "%010d\{-12345}");
        test(String.format(l, "%010d", 0), "%010d\{0}");
        test(String.format(l, "%010d", 12345), "%010d\{12345}");
        test(String.format(l, "%,010d", -12345), "%,010d\{-12345}");
        test(String.format(l, "%,010d", 0), "%,010d\{0}");
        test(String.format(l, "%,010d", 12345), "%,010d\{12345}");
        test(String.format(l, "% 010d", -12345), "% 010d\{-12345}");
        test(String.format(l, "% 010d", 0), "% 010d\{0}");
        test(String.format(l, "% 010d", 12345), "% 010d\{12345}");
        test(String.format(l, "%, 010d", -12345), "%, 010d\{-12345}");
        test(String.format(l, "%, 010d", 0), "%, 010d\{0}");
        test(String.format(l, "%, 010d", 12345), "%, 010d\{12345}");

        test(String.format(l, "%d", -12345), "%d\{-12345}");
        test(String.format(l, "%d", 0), "%d\{0}");
        test(String.format(l, "%d", 12345), "%d\{12345}");
        test(String.format(l, "%10d", -12345), "%10d\{-12345}");
        test(String.format(l, "%10d", 0), "%10d\{0}");
        test(String.format(l, "%10d", 12345), "%10d\{12345}");
        test(String.format(l, "%-10d", -12345), "%-10d\{-12345}");
        test(String.format(l, "%-10d", 0), "%-10d\{0}");
        test(String.format(l, "%-10d", 12345), "%-10d\{12345}");
        test(String.format(l, "%,d", -12345), "%,d\{-12345}");
        test(String.format(l, "%,d", 0), "%,d\{0}");
        test(String.format(l, "%,d", 12345), "%,d\{12345}");
        test(String.format(l, "%,10d", -12345), "%,10d\{-12345}");
        test(String.format(l, "%,10d", 0), "%,10d\{0}");
        test(String.format(l, "%,10d", 12345), "%,10d\{12345}");
        test(String.format(l, "%,-10d", -12345), "%,-10d\{-12345}");
        test(String.format(l, "%,-10d", 0), "%,-10d\{0}");
        test(String.format(l, "%,-10d", 12345), "%,-10d\{12345}");
        test(String.format(l, "%+d", -12345), "%+d\{-12345}");
        test(String.format(l, "%+d", 0), "%+d\{0}");
        test(String.format(l, "%+d", 12345), "%+d\{12345}");
        test(String.format(l, "%+10d", -12345), "%+10d\{-12345}");
        test(String.format(l, "%+10d", 0), "%+10d\{0}");
        test(String.format(l, "%+10d", 12345), "%+10d\{12345}");
        test(String.format(l, "%+-10d", -12345), "%+-10d\{-12345}");
        test(String.format(l, "%+-10d", 0), "%+-10d\{0}");
        test(String.format(l, "%+-10d", 12345), "%+-10d\{12345}");
        test(String.format(l, "%,+d", -12345), "%,+d\{-12345}");
        test(String.format(l, "%,+d", 0), "%,+d\{0}");
        test(String.format(l, "%,+d", 12345), "%,+d\{12345}");
        test(String.format(l, "%,+10d", -12345), "%,+10d\{-12345}");
        test(String.format(l, "%,+10d", 0), "%,+10d\{0}");
        test(String.format(l, "%,+10d", 12345), "%,+10d\{12345}");
        test(String.format(l, "%,+-10d", -12345), "%,+-10d\{-12345}");
        test(String.format(l, "%,+-10d", 0), "%,+-10d\{0}");
        test(String.format(l, "%,+-10d", 12345), "%,+-10d\{12345}");
        test(String.format(l, "%010d", -12345), "%010d\{-12345}");
        test(String.format(l, "%010d", 0), "%010d\{0}");
        test(String.format(l, "%010d", 12345), "%010d\{12345}");
        test(String.format(l, "%,010d", -12345), "%,010d\{-12345}");
        test(String.format(l, "%,010d", 0), "%,010d\{0}");
        test(String.format(l, "%,010d", 12345), "%,010d\{12345}");
        test(String.format(l, "%+010d", -12345), "%+010d\{-12345}");
        test(String.format(l, "%+010d", 0), "%+010d\{0}");
        test(String.format(l, "%+010d", 12345), "%+010d\{12345}");
        test(String.format(l, "%,+010d", -12345), "%,+010d\{-12345}");
        test(String.format(l, "%,+010d", 0), "%,+010d\{0}");
        test(String.format(l, "%,+010d", 12345), "%,+010d\{12345}");

        test(String.format(l, "%d", -12345), "%d\{-12345}");
        test(String.format(l, "%d", 0), "%d\{0}");
        test(String.format(l, "%d", 12345), "%d\{12345}");
        test(String.format(l, "%10d", -12345), "%10d\{-12345}");
        test(String.format(l, "%10d", 0), "%10d\{0}");
        test(String.format(l, "%10d", 12345), "%10d\{12345}");
        test(String.format(l, "%-10d", -12345), "%-10d\{-12345}");
        test(String.format(l, "%-10d", 0), "%-10d\{0}");
        test(String.format(l, "%-10d", 12345), "%-10d\{12345}");
        test(String.format(l, "%,d", -12345), "%,d\{-12345}");
        test(String.format(l, "%,d", 0), "%,d\{0}");
        test(String.format(l, "%,d", 12345), "%,d\{12345}");
        test(String.format(l, "%,10d", -12345), "%,10d\{-12345}");
        test(String.format(l, "%,10d", 0), "%,10d\{0}");
        test(String.format(l, "%,10d", 12345), "%,10d\{12345}");
        test(String.format(l, "%,-10d", -12345), "%,-10d\{-12345}");
        test(String.format(l, "%,-10d", 0), "%,-10d\{0}");
        test(String.format(l, "%,-10d", 12345), "%,-10d\{12345}");
        test(String.format(l, "%(d", -12345), "%(d\{-12345}");
        test(String.format(l, "%(d", 0), "%(d\{0}");
        test(String.format(l, "%(d", 12345), "%(d\{12345}");
        test(String.format(l, "%(10d", -12345), "%(10d\{-12345}");
        test(String.format(l, "%(10d", 0), "%(10d\{0}");
        test(String.format(l, "%(10d", 12345), "%(10d\{12345}");
        test(String.format(l, "%(-10d", -12345), "%(-10d\{-12345}");
        test(String.format(l, "%(-10d", 0), "%(-10d\{0}");
        test(String.format(l, "%(-10d", 12345), "%(-10d\{12345}");
        test(String.format(l, "%,(d", -12345), "%,(d\{-12345}");
        test(String.format(l, "%,(d", 0), "%,(d\{0}");
        test(String.format(l, "%,(d", 12345), "%,(d\{12345}");
        test(String.format(l, "%,(10d", -12345), "%,(10d\{-12345}");
        test(String.format(l, "%,(10d", 0), "%,(10d\{0}");
        test(String.format(l, "%,(10d", 12345), "%,(10d\{12345}");
        test(String.format(l, "%,(-10d", -12345), "%,(-10d\{-12345}");
        test(String.format(l, "%,(-10d", 0), "%,(-10d\{0}");
        test(String.format(l, "%,(-10d", 12345), "%,(-10d\{12345}");
        test(String.format(l, "%010d", -12345), "%010d\{-12345}");
        test(String.format(l, "%010d", 0), "%010d\{0}");
        test(String.format(l, "%010d", 12345), "%010d\{12345}");
        test(String.format(l, "%,010d", -12345), "%,010d\{-12345}");
        test(String.format(l, "%,010d", 0), "%,010d\{0}");
        test(String.format(l, "%,010d", 12345), "%,010d\{12345}");
        test(String.format(l, "%(010d", -12345), "%(010d\{-12345}");
        test(String.format(l, "%(010d", 0), "%(010d\{0}");
        test(String.format(l, "%(010d", 12345), "%(010d\{12345}");
        test(String.format(l, "%,(010d", -12345), "%,(010d\{-12345}");
        test(String.format(l, "%,(010d", 0), "%,(010d\{0}");
        test(String.format(l, "%,(010d", 12345), "%,(010d\{12345}");

        test(String.format(l, "%o", -12345), "%o\{-12345}");
        test(String.format(l, "\u8336%o", -12345), "\u8336%o\{-12345}"); // utf16
        test(String.format(l, "%o", 0), "%o\{0}");
        test(String.format(l, "%o", 12345), "%o\{12345}");
        test(String.format(l, "%10o", -12345), "%10o\{-12345}");
        test(String.format(l, "%10o", 0), "%10o\{0}");
        test(String.format(l, "%10o", 12345), "%10o\{12345}");
        test(String.format(l, "%-10o", -12345), "%-10o\{-12345}");
        test(String.format(l, "%-10o", 0), "%-10o\{0}");
        test(String.format(l, "%-10o", 12345), "%-10o\{12345}");
        test(String.format(l, "%#o", -12345), "%#o\{-12345}");
        test(String.format(l, "%#o", 0), "%#o\{0}");
        test(String.format(l, "%#o", 12345), "%#o\{12345}");
        test(String.format(l, "%#10o", -12345), "%#10o\{-12345}");
        test(String.format(l, "%#10o", 0), "%#10o\{0}");
        test(String.format(l, "%#10o", 12345), "%#10o\{12345}");
        test(String.format(l, "%#-10o", -12345), "%#-10o\{-12345}");
        test(String.format(l, "%#-10o", 0), "%#-10o\{0}");
        test(String.format(l, "%#-10o", 12345), "%#-10o\{12345}");
        test(String.format(l, "%010o", -12345), "%010o\{-12345}");
        test(String.format(l, "%010o", 0), "%010o\{0}");
        test(String.format(l, "%010o", 12345), "%010o\{12345}");
        test(String.format(l, "%#010o", -12345), "%#010o\{-12345}");
        test(String.format(l, "%#010o", 0), "%#010o\{0}");
        test(String.format(l, "%#010o", 12345), "%#010o\{12345}");

        test(String.format(l, "%x", -12345), "%x\{-12345}");
        test(String.format(l, "\u8336%x", -12345), "\u8336%x\{-12345}");
        test(String.format(l, "%x", 0), "%x\{0}");
        test(String.format(l, "%x", 12345), "%x\{12345}");
        test(String.format(l, "%10x", -12345), "%10x\{-12345}");
        test(String.format(l, "%10x", 0), "%10x\{0}");
        test(String.format(l, "%10x", 12345), "%10x\{12345}");
        test(String.format(l, "%-10x", -12345), "%-10x\{-12345}");
        test(String.format(l, "%-10x", 0), "%-10x\{0}");
        test(String.format(l, "%-10x", 12345), "%-10x\{12345}");
        test(String.format(l, "%X", -12345), "%X\{-12345}");
        test(String.format(l, "%X", 0), "%X\{0}");
        test(String.format(l, "%X", 12345), "%X\{12345}");
        test(String.format(l, "%10X", -12345), "%10X\{-12345}");
        test(String.format(l, "%10X", 0), "%10X\{0}");
        test(String.format(l, "%10X", 12345), "%10X\{12345}");
        test(String.format(l, "%-10X", -12345), "%-10X\{-12345}");
        test(String.format(l, "%-10X", 0), "%-10X\{0}");
        test(String.format(l, "%-10X", 12345), "%-10X\{12345}");
        test(String.format(l, "%#x", -12345), "%#x\{-12345}");
        test(String.format(l, "%#x", 0), "%#x\{0}");
        test(String.format(l, "%#x", 12345), "%#x\{12345}");
        test(String.format(l, "%#10x", -12345), "%#10x\{-12345}");
        test(String.format(l, "%#10x", 0), "%#10x\{0}");
        test(String.format(l, "%#10x", 12345), "%#10x\{12345}");
        test(String.format(l, "%#-10x", -12345), "%#-10x\{-12345}");
        test(String.format(l, "%#-10x", 0), "%#-10x\{0}");
        test(String.format(l, "%#-10x", 12345), "%#-10x\{12345}");
        test(String.format(l, "%#X", -12345), "%#X\{-12345}");
        test(String.format(l, "%#X", 0), "%#X\{0}");
        test(String.format(l, "%#X", 12345), "%#X\{12345}");
        test(String.format(l, "%#10X", -12345), "%#10X\{-12345}");
        test(String.format(l, "%#10X", 0), "%#10X\{0}");
        test(String.format(l, "%#10X", 12345), "%#10X\{12345}");
        test(String.format(l, "%#-10X", -12345), "%#-10X\{-12345}");
        test(String.format(l, "%#-10X", 0), "%#-10X\{0}");
        test(String.format(l, "%#-10X", 12345), "%#-10X\{12345}");
        test(String.format(l, "%010x", -12345), "%010x\{-12345}");
        test(String.format(l, "%010x", 0), "%010x\{0}");
        test(String.format(l, "%010x", 12345), "%010x\{12345}");
        test(String.format(l, "%010X", -12345), "%010X\{-12345}");
        test(String.format(l, "%010X", 0), "%010X\{0}");
        test(String.format(l, "%010X", 12345), "%010X\{12345}");
        test(String.format(l, "%#010x", -12345), "%#010x\{-12345}");
        test(String.format(l, "%#010x", 0), "%#010x\{0}");
        test(String.format(l, "%#010x", 12345), "%#010x\{12345}");
        test(String.format(l, "%#010X", -12345), "%#010X\{-12345}");
        test(String.format(l, "%#010X", 0), "%#010X\{0}");
        test(String.format(l, "%#010X", 12345), "%#010X\{12345}");

        test(String.format(l, "%f", -12345.6), "%f\{-12345.6}");
        test(String.format(l, "%f", 0.0), "%f\{0.0}");
        test(String.format(l, "%f", 12345.6), "%f\{12345.6}");
        test(String.format(l, "%10f", -12345.6), "%10f\{-12345.6}");
        test(String.format(l, "%10f", 0.0), "%10f\{0.0}");
        test(String.format(l, "%10f", 12345.6), "%10f\{12345.6}");
        test(String.format(l, "%-10f", -12345.6), "%-10f\{-12345.6}");
        test(String.format(l, "%-10f", 0.0), "%-10f\{0.0}");
        test(String.format(l, "%-10f", 12345.6), "%-10f\{12345.6}");
        test(String.format(l, "%,f", -12345.6), "%,f\{-12345.6}");
        test(String.format(l, "%,f", 0.0), "%,f\{0.0}");
        test(String.format(l, "%,f", 12345.6), "%,f\{12345.6}");
        test(String.format(l, "%,10f", -12345.6), "%,10f\{-12345.6}");
        test(String.format(l, "%,10f", 0.0), "%,10f\{0.0}");
        test(String.format(l, "%,10f", 12345.6), "%,10f\{12345.6}");
        test(String.format(l, "%,-10f", -12345.6), "%,-10f\{-12345.6}");
        test(String.format(l, "%,-10f", 0.0), "%,-10f\{0.0}");
        test(String.format(l, "%,-10f", 12345.6), "%,-10f\{12345.6}");

        test(String.format(l, "%f", -12345.6), "%f\{-12345.6}");
        test(String.format(l, "%f", 0.0), "%f\{0.0}");
        test(String.format(l, "%f", 12345.6), "%f\{12345.6}");
        test(String.format(l, "%10f", -12345.6), "%10f\{-12345.6}");
        test(String.format(l, "%10f", 0.0), "%10f\{0.0}");
        test(String.format(l, "%10f", 12345.6), "%10f\{12345.6}");
        test(String.format(l, "%-10f", -12345.6), "%-10f\{-12345.6}");
        test(String.format(l, "%-10f", 0.0), "%-10f\{0.0}");
        test(String.format(l, "%-10f", 12345.6), "%-10f\{12345.6}");
        test(String.format(l, "%,f", -12345.6), "%,f\{-12345.6}");
        test(String.format(l, "%,f", 0.0), "%,f\{0.0}");
        test(String.format(l, "%,f", 12345.6), "%,f\{12345.6}");
        test(String.format(l, "%,10f", -12345.6), "%,10f\{-12345.6}");
        test(String.format(l, "%,10f", 0.0), "%,10f\{0.0}");
        test(String.format(l, "%,10f", 12345.6), "%,10f\{12345.6}");
        test(String.format(l, "%,-10f", -12345.6), "%,-10f\{-12345.6}");
        test(String.format(l, "%,-10f", 0.0), "%,-10f\{0.0}");
        test(String.format(l, "%,-10f", 12345.6), "%,-10f\{12345.6}");
        test(String.format(l, "% f", -12345.6), "% f\{-12345.6}");
        test(String.format(l, "% f", 0.0), "% f\{0.0}");
        test(String.format(l, "% f", 12345.6), "% f\{12345.6}");
        test(String.format(l, "% 10f", -12345.6), "% 10f\{-12345.6}");
        test(String.format(l, "% 10f", 0.0), "% 10f\{0.0}");
        test(String.format(l, "% 10f", 12345.6), "% 10f\{12345.6}");
        test(String.format(l, "% -10f", -12345.6), "% -10f\{-12345.6}");
        test(String.format(l, "% -10f", 0.0), "% -10f\{0.0}");
        test(String.format(l, "% -10f", 12345.6), "% -10f\{12345.6}");
        test(String.format(l, "%, f", -12345.6), "%, f\{-12345.6}");
        test(String.format(l, "%, f", 0.0), "%, f\{0.0}");
        test(String.format(l, "%, f", 12345.6), "%, f\{12345.6}");
        test(String.format(l, "%, 10f", -12345.6), "%, 10f\{-12345.6}");
        test(String.format(l, "%, 10f", 0.0), "%, 10f\{0.0}");
        test(String.format(l, "%, 10f", 12345.6), "%, 10f\{12345.6}");
        test(String.format(l, "%, -10f", -12345.6), "%, -10f\{-12345.6}");
        test(String.format(l, "%, -10f", 0.0), "%, -10f\{0.0}");
        test(String.format(l, "%, -10f", 12345.6), "%, -10f\{12345.6}");

        test(String.format(l, "%f", -12345.6), "%f\{-12345.6}");
        test(String.format(l, "%f", 0.0), "%f\{0.0}");
        test(String.format(l, "%f", 12345.6), "%f\{12345.6}");
        test(String.format(l, "%10f", -12345.6), "%10f\{-12345.6}");
        test(String.format(l, "%10f", 0.0), "%10f\{0.0}");
        test(String.format(l, "%10f", 12345.6), "%10f\{12345.6}");
        test(String.format(l, "%-10f", -12345.6), "%-10f\{-12345.6}");
        test(String.format(l, "%-10f", 0.0), "%-10f\{0.0}");
        test(String.format(l, "%-10f", 12345.6), "%-10f\{12345.6}");
        test(String.format(l, "%,f", -12345.6), "%,f\{-12345.6}");
        test(String.format(l, "%,f", 0.0), "%,f\{0.0}");
        test(String.format(l, "%,f", 12345.6), "%,f\{12345.6}");
        test(String.format(l, "%,10f", -12345.6), "%,10f\{-12345.6}");
        test(String.format(l, "%,10f", 0.0), "%,10f\{0.0}");
        test(String.format(l, "%,10f", 12345.6), "%,10f\{12345.6}");
        test(String.format(l, "%,-10f", -12345.6), "%,-10f\{-12345.6}");
        test(String.format(l, "%,-10f", 0.0), "%,-10f\{0.0}");
        test(String.format(l, "%,-10f", 12345.6), "%,-10f\{12345.6}");
        test(String.format(l, "%+f", -12345.6), "%+f\{-12345.6}");
        test(String.format(l, "%+f", 0.0), "%+f\{0.0}");
        test(String.format(l, "%+f", 12345.6), "%+f\{12345.6}");
        test(String.format(l, "%+10f", -12345.6), "%+10f\{-12345.6}");
        test(String.format(l, "%+10f", 0.0), "%+10f\{0.0}");
        test(String.format(l, "%+10f", 12345.6), "%+10f\{12345.6}");
        test(String.format(l, "%+-10f", -12345.6), "%+-10f\{-12345.6}");
        test(String.format(l, "%+-10f", 0.0), "%+-10f\{0.0}");
        test(String.format(l, "%+-10f", 12345.6), "%+-10f\{12345.6}");
        test(String.format(l, "%,+f", -12345.6), "%,+f\{-12345.6}");
        test(String.format(l, "%,+f", 0.0), "%,+f\{0.0}");
        test(String.format(l, "%,+f", 12345.6), "%,+f\{12345.6}");
        test(String.format(l, "%,+10f", -12345.6), "%,+10f\{-12345.6}");
        test(String.format(l, "%,+10f", 0.0), "%,+10f\{0.0}");
        test(String.format(l, "%,+10f", 12345.6), "%,+10f\{12345.6}");
        test(String.format(l, "%,+-10f", -12345.6), "%,+-10f\{-12345.6}");
        test(String.format(l, "%,+-10f", 0.0), "%,+-10f\{0.0}");
        test(String.format(l, "%,+-10f", 12345.6), "%,+-10f\{12345.6}");

        test(String.format(l, "%f", -12345.6), "%f\{-12345.6}");
        test(String.format(l, "%f", 0.0), "%f\{0.0}");
        test(String.format(l, "%f", 12345.6), "%f\{12345.6}");
        test(String.format(l, "%10f", -12345.6), "%10f\{-12345.6}");
        test(String.format(l, "%10f", 0.0), "%10f\{0.0}");
        test(String.format(l, "%10f", 12345.6), "%10f\{12345.6}");
        test(String.format(l, "%-10f", -12345.6), "%-10f\{-12345.6}");
        test(String.format(l, "%-10f", 0.0), "%-10f\{0.0}");
        test(String.format(l, "%-10f", 12345.6), "%-10f\{12345.6}");
        test(String.format(l, "%,f", -12345.6), "%,f\{-12345.6}");
        test(String.format(l, "%,f", 0.0), "%,f\{0.0}");
        test(String.format(l, "%,f", 12345.6), "%,f\{12345.6}");
        test(String.format(l, "%,10f", -12345.6), "%,10f\{-12345.6}");
        test(String.format(l, "%,10f", 0.0), "%,10f\{0.0}");
        test(String.format(l, "%,10f", 12345.6), "%,10f\{12345.6}");
        test(String.format(l, "%,-10f", -12345.6), "%,-10f\{-12345.6}");
        test(String.format(l, "%,-10f", 0.0), "%,-10f\{0.0}");
        test(String.format(l, "%,-10f", 12345.6), "%,-10f\{12345.6}");
        test(String.format(l, "%(f", -12345.6), "%(f\{-12345.6}");
        test(String.format(l, "%(f", 0.0), "%(f\{0.0}");
        test(String.format(l, "%(f", 12345.6), "%(f\{12345.6}");
        test(String.format(l, "%(10f", -12345.6), "%(10f\{-12345.6}");
        test(String.format(l, "%(10f", 0.0), "%(10f\{0.0}");
        test(String.format(l, "%(10f", 12345.6), "%(10f\{12345.6}");
        test(String.format(l, "%(-10f", -12345.6), "%(-10f\{-12345.6}");
        test(String.format(l, "%(-10f", 0.0), "%(-10f\{0.0}");
        test(String.format(l, "%(-10f", 12345.6), "%(-10f\{12345.6}");
        test(String.format(l, "%,(f", -12345.6), "%,(f\{-12345.6}");
        test(String.format(l, "%,(f", 0.0), "%,(f\{0.0}");
        test(String.format(l, "%,(f", 12345.6), "%,(f\{12345.6}");
        test(String.format(l, "%,(10f", -12345.6), "%,(10f\{-12345.6}");
        test(String.format(l, "%,(10f", 0.0), "%,(10f\{0.0}");
        test(String.format(l, "%,(10f", 12345.6), "%,(10f\{12345.6}");
        test(String.format(l, "%,(-10f", -12345.6), "%,(-10f\{-12345.6}");
        test(String.format(l, "%,(-10f", 0.0), "%,(-10f\{0.0}");
        test(String.format(l, "%,(-10f", 12345.6), "%,(-10f\{12345.6}");
        test(String.format(l, "%+f", -12345.6), "%+f\{-12345.6}");
        test(String.format(l, "%+f", 0.0), "%+f\{0.0}");
        test(String.format(l, "%+f", 12345.6), "%+f\{12345.6}");
        test(String.format(l, "%+10f", -12345.6), "%+10f\{-12345.6}");
        test(String.format(l, "%+10f", 0.0), "%+10f\{0.0}");
        test(String.format(l, "%+10f", 12345.6), "%+10f\{12345.6}");
        test(String.format(l, "%+-10f", -12345.6), "%+-10f\{-12345.6}");
        test(String.format(l, "%+-10f", 0.0), "%+-10f\{0.0}");
        test(String.format(l, "%+-10f", 12345.6), "%+-10f\{12345.6}");
        test(String.format(l, "%,+f", -12345.6), "%,+f\{-12345.6}");
        test(String.format(l, "%,+f", 0.0), "%,+f\{0.0}");
        test(String.format(l, "%,+f", 12345.6), "%,+f\{12345.6}");
        test(String.format(l, "%,+10f", -12345.6), "%,+10f\{-12345.6}");
        test(String.format(l, "%,+10f", 0.0), "%,+10f\{0.0}");
        test(String.format(l, "%,+10f", 12345.6), "%,+10f\{12345.6}");
        test(String.format(l, "%,+-10f", -12345.6), "%,+-10f\{-12345.6}");
        test(String.format(l, "%,+-10f", 0.0), "%,+-10f\{0.0}");
        test(String.format(l, "%,+-10f", 12345.6), "%,+-10f\{12345.6}");
        test(String.format(l, "%(+f", -12345.6), "%(+f\{-12345.6}");
        test(String.format(l, "%(+f", 0.0), "%(+f\{0.0}");
        test(String.format(l, "%(+f", 12345.6), "%(+f\{12345.6}");
        test(String.format(l, "%(+10f", -12345.6), "%(+10f\{-12345.6}");
        test(String.format(l, "%(+10f", 0.0), "%(+10f\{0.0}");
        test(String.format(l, "%(+10f", 12345.6), "%(+10f\{12345.6}");
        test(String.format(l, "%(+-10f", -12345.6), "%(+-10f\{-12345.6}");
        test(String.format(l, "%(+-10f", 0.0), "%(+-10f\{0.0}");
        test(String.format(l, "%(+-10f", 12345.6), "%(+-10f\{12345.6}");
        test(String.format(l, "%,(+f", -12345.6), "%,(+f\{-12345.6}");
        test(String.format(l, "%,(+f", 0.0), "%,(+f\{0.0}");
        test(String.format(l, "%,(+f", 12345.6), "%,(+f\{12345.6}");
        test(String.format(l, "%,(+10f", -12345.6), "%,(+10f\{-12345.6}");
        test(String.format(l, "%,(+10f", 0.0), "%,(+10f\{0.0}");
        test(String.format(l, "%,(+10f", 12345.6), "%,(+10f\{12345.6}");
        test(String.format(l, "%,(+-10f", -12345.6), "%,(+-10f\{-12345.6}");
        test(String.format(l, "%,(+-10f", 0.0), "%,(+-10f\{0.0}");
        test(String.format(l, "%,(+-10f", 12345.6), "%,(+-10f\{12345.6}");

        test(String.format(l, "%e", -12345.6), "%e\{-12345.6}");
        test(String.format(l, "%e", 0.0), "%e\{0.0}");
        test(String.format(l, "%e", 12345.6), "%e\{12345.6}");
        test(String.format(l, "%10e", -12345.6), "%10e\{-12345.6}");
        test(String.format(l, "%10e", 0.0), "%10e\{0.0}");
        test(String.format(l, "%10e", 12345.6), "%10e\{12345.6}");
        test(String.format(l, "%-10e", -12345.6), "%-10e\{-12345.6}");
        test(String.format(l, "%-10e", 0.0), "%-10e\{0.0}");
        test(String.format(l, "%-10e", 12345.6), "%-10e\{12345.6}");
        test(String.format(l, "%E", -12345.6), "%E\{-12345.6}");
        test(String.format(l, "%E", 0.0), "%E\{0.0}");
        test(String.format(l, "%E", 12345.6), "%E\{12345.6}");
        test(String.format(l, "%10E", -12345.6), "%10E\{-12345.6}");
        test(String.format(l, "%10E", 0.0), "%10E\{0.0}");
        test(String.format(l, "%10E", 12345.6), "%10E\{12345.6}");
        test(String.format(l, "%-10E", -12345.6), "%-10E\{-12345.6}");
        test(String.format(l, "%-10E", 0.0), "%-10E\{0.0}");
        test(String.format(l, "%-10E", 12345.6), "%-10E\{12345.6}");

        test(String.format(l, "%g", -12345.6), "%g\{-12345.6}");
        test(String.format(l, "%g", 0.0), "%g\{0.0}");
        test(String.format(l, "%g", 12345.6), "%g\{12345.6}");
        test(String.format(l, "%10g", -12345.6), "%10g\{-12345.6}");
        test(String.format(l, "%10g", 0.0), "%10g\{0.0}");
        test(String.format(l, "%10g", 12345.6), "%10g\{12345.6}");
        test(String.format(l, "%-10g", -12345.6), "%-10g\{-12345.6}");
        test(String.format(l, "%-10g", 0.0), "%-10g\{0.0}");
        test(String.format(l, "%-10g", 12345.6), "%-10g\{12345.6}");
        test(String.format(l, "%G", -12345.6), "%G\{-12345.6}");
        test(String.format(l, "%G", 0.0), "%G\{0.0}");
        test(String.format(l, "%G", 12345.6), "%G\{12345.6}");
        test(String.format(l, "%10G", -12345.6), "%10G\{-12345.6}");
        test(String.format(l, "%10G", 0.0), "%10G\{0.0}");
        test(String.format(l, "%10G", 12345.6), "%10G\{12345.6}");
        test(String.format(l, "%-10G", -12345.6), "%-10G\{-12345.6}");
        test(String.format(l, "%-10G", 0.0), "%-10G\{0.0}");
        test(String.format(l, "%-10G", 12345.6), "%-10G\{12345.6}");
        test(String.format(l, "%,g", -12345.6), "%,g\{-12345.6}");
        test(String.format(l, "%,g", 0.0), "%,g\{0.0}");
        test(String.format(l, "%,g", 12345.6), "%,g\{12345.6}");
        test(String.format(l, "%,10g", -12345.6), "%,10g\{-12345.6}");
        test(String.format(l, "%,10g", 0.0), "%,10g\{0.0}");
        test(String.format(l, "%,10g", 12345.6), "%,10g\{12345.6}");
        test(String.format(l, "%,-10g", -12345.6), "%,-10g\{-12345.6}");
        test(String.format(l, "%,-10g", 0.0), "%,-10g\{0.0}");
        test(String.format(l, "%,-10g", 12345.6), "%,-10g\{12345.6}");
        test(String.format(l, "%,G", -12345.6), "%,G\{-12345.6}");
        test(String.format(l, "%,G", 0.0), "%,G\{0.0}");
        test(String.format(l, "%,G", 12345.6), "%,G\{12345.6}");
        test(String.format(l, "%,10G", -12345.6), "%,10G\{-12345.6}");
        test(String.format(l, "%,10G", 0.0), "%,10G\{0.0}");
        test(String.format(l, "%,10G", 12345.6), "%,10G\{12345.6}");
        test(String.format(l, "%,-10G", -12345.6), "%,-10G\{-12345.6}");
        test(String.format(l, "%,-10G", 0.0), "%,-10G\{0.0}");
        test(String.format(l, "%,-10G", 12345.6), "%,-10G\{12345.6}");

        test(String.format(l, "%g", -12345.6), "%g\{-12345.6}");
        test(String.format(l, "%g", 0.0), "%g\{0.0}");
        test(String.format(l, "%g", 12345.6), "%g\{12345.6}");
        test(String.format(l, "%10g", -12345.6), "%10g\{-12345.6}");
        test(String.format(l, "%10g", 0.0), "%10g\{0.0}");
        test(String.format(l, "%10g", 12345.6), "%10g\{12345.6}");
        test(String.format(l, "%-10g", -12345.6), "%-10g\{-12345.6}");
        test(String.format(l, "%-10g", 0.0), "%-10g\{0.0}");
        test(String.format(l, "%-10g", 12345.6), "%-10g\{12345.6}");
        test(String.format(l, "%G", -12345.6), "%G\{-12345.6}");
        test(String.format(l, "%G", 0.0), "%G\{0.0}");
        test(String.format(l, "%G", 12345.6), "%G\{12345.6}");
        test(String.format(l, "%10G", -12345.6), "%10G\{-12345.6}");
        test(String.format(l, "%10G", 0.0), "%10G\{0.0}");
        test(String.format(l, "%10G", 12345.6), "%10G\{12345.6}");
        test(String.format(l, "%-10G", -12345.6), "%-10G\{-12345.6}");
        test(String.format(l, "%-10G", 0.0), "%-10G\{0.0}");
        test(String.format(l, "%-10G", 12345.6), "%-10G\{12345.6}");
        test(String.format(l, "%,g", -12345.6), "%,g\{-12345.6}");
        test(String.format(l, "%,g", 0.0), "%,g\{0.0}");
        test(String.format(l, "%,g", 12345.6), "%,g\{12345.6}");
        test(String.format(l, "%,10g", -12345.6), "%,10g\{-12345.6}");
        test(String.format(l, "%,10g", 0.0), "%,10g\{0.0}");
        test(String.format(l, "%,10g", 12345.6), "%,10g\{12345.6}");
        test(String.format(l, "%,-10g", -12345.6), "%,-10g\{-12345.6}");
        test(String.format(l, "%,-10g", 0.0), "%,-10g\{0.0}");
        test(String.format(l, "%,-10g", 12345.6), "%,-10g\{12345.6}");
        test(String.format(l, "%,G", -12345.6), "%,G\{-12345.6}");
        test(String.format(l, "%,G", 0.0), "%,G\{0.0}");
        test(String.format(l, "%,G", 12345.6), "%,G\{12345.6}");
        test(String.format(l, "%,10G", -12345.6), "%,10G\{-12345.6}");
        test(String.format(l, "%,10G", 0.0), "%,10G\{0.0}");
        test(String.format(l, "%,10G", 12345.6), "%,10G\{12345.6}");
        test(String.format(l, "%,-10G", -12345.6), "%,-10G\{-12345.6}");
        test(String.format(l, "%,-10G", 0.0), "%,-10G\{0.0}");
        test(String.format(l, "%,-10G", 12345.6), "%,-10G\{12345.6}");
        test(String.format(l, "% g", -12345.6), "% g\{-12345.6}");
        test(String.format(l, "% g", 0.0), "% g\{0.0}");
        test(String.format(l, "% g", 12345.6), "% g\{12345.6}");
        test(String.format(l, "% 10g", -12345.6), "% 10g\{-12345.6}");
        test(String.format(l, "% 10g", 0.0), "% 10g\{0.0}");
        test(String.format(l, "% 10g", 12345.6), "% 10g\{12345.6}");
        test(String.format(l, "% -10g", -12345.6), "% -10g\{-12345.6}");
        test(String.format(l, "% -10g", 0.0), "% -10g\{0.0}");
        test(String.format(l, "% -10g", 12345.6), "% -10g\{12345.6}");
        test(String.format(l, "% G", -12345.6), "% G\{-12345.6}");
        test(String.format(l, "% G", 0.0), "% G\{0.0}");
        test(String.format(l, "% G", 12345.6), "% G\{12345.6}");
        test(String.format(l, "% 10G", -12345.6), "% 10G\{-12345.6}");
        test(String.format(l, "% 10G", 0.0), "% 10G\{0.0}");
        test(String.format(l, "% 10G", 12345.6), "% 10G\{12345.6}");
        test(String.format(l, "% -10G", -12345.6), "% -10G\{-12345.6}");
        test(String.format(l, "% -10G", 0.0), "% -10G\{0.0}");
        test(String.format(l, "% -10G", 12345.6), "% -10G\{12345.6}");
        test(String.format(l, "%, g", -12345.6), "%, g\{-12345.6}");
        test(String.format(l, "%, g", 0.0), "%, g\{0.0}");
        test(String.format(l, "%, g", 12345.6), "%, g\{12345.6}");
        test(String.format(l, "%, 10g", -12345.6), "%, 10g\{-12345.6}");
        test(String.format(l, "%, 10g", 0.0), "%, 10g\{0.0}");
        test(String.format(l, "%, 10g", 12345.6), "%, 10g\{12345.6}");
        test(String.format(l, "%, -10g", -12345.6), "%, -10g\{-12345.6}");
        test(String.format(l, "%, -10g", 0.0), "%, -10g\{0.0}");
        test(String.format(l, "%, -10g", 12345.6), "%, -10g\{12345.6}");
        test(String.format(l, "%, G", -12345.6), "%, G\{-12345.6}");
        test(String.format(l, "%, G", 0.0), "%, G\{0.0}");
        test(String.format(l, "%, G", 12345.6), "%, G\{12345.6}");
        test(String.format(l, "%, 10G", -12345.6), "%, 10G\{-12345.6}");
        test(String.format(l, "%, 10G", 0.0), "%, 10G\{0.0}");
        test(String.format(l, "%, 10G", 12345.6), "%, 10G\{12345.6}");
        test(String.format(l, "%, -10G", -12345.6), "%, -10G\{-12345.6}");
        test(String.format(l, "%, -10G", 0.0), "%, -10G\{0.0}");
        test(String.format(l, "%, -10G", 12345.6), "%, -10G\{12345.6}");

        test(String.format(l, "%g", -12345.6), "%g\{-12345.6}");
        test(String.format(l, "%g", 0.0), "%g\{0.0}");
        test(String.format(l, "%g", 12345.6), "%g\{12345.6}");
        test(String.format(l, "%10g", -12345.6), "%10g\{-12345.6}");
        test(String.format(l, "%10g", 0.0), "%10g\{0.0}");
        test(String.format(l, "%10g", 12345.6), "%10g\{12345.6}");
        test(String.format(l, "%-10g", -12345.6), "%-10g\{-12345.6}");
        test(String.format(l, "%-10g", 0.0), "%-10g\{0.0}");
        test(String.format(l, "%-10g", 12345.6), "%-10g\{12345.6}");
        test(String.format(l, "%G", -12345.6), "%G\{-12345.6}");
        test(String.format(l, "%G", 0.0), "%G\{0.0}");
        test(String.format(l, "%G", 12345.6), "%G\{12345.6}");
        test(String.format(l, "%10G", -12345.6), "%10G\{-12345.6}");
        test(String.format(l, "%10G", 0.0), "%10G\{0.0}");
        test(String.format(l, "%10G", 12345.6), "%10G\{12345.6}");
        test(String.format(l, "%-10G", -12345.6), "%-10G\{-12345.6}");
        test(String.format(l, "%-10G", 0.0), "%-10G\{0.0}");
        test(String.format(l, "%-10G", 12345.6), "%-10G\{12345.6}");
        test(String.format(l, "%,g", -12345.6), "%,g\{-12345.6}");
        test(String.format(l, "%,g", 0.0), "%,g\{0.0}");
        test(String.format(l, "%,g", 12345.6), "%,g\{12345.6}");
        test(String.format(l, "%,10g", -12345.6), "%,10g\{-12345.6}");
        test(String.format(l, "%,10g", 0.0), "%,10g\{0.0}");
        test(String.format(l, "%,10g", 12345.6), "%,10g\{12345.6}");
        test(String.format(l, "%,-10g", -12345.6), "%,-10g\{-12345.6}");
        test(String.format(l, "%,-10g", 0.0), "%,-10g\{0.0}");
        test(String.format(l, "%,-10g", 12345.6), "%,-10g\{12345.6}");
        test(String.format(l, "%,G", -12345.6), "%,G\{-12345.6}");
        test(String.format(l, "%,G", 0.0), "%,G\{0.0}");
        test(String.format(l, "%,G", 12345.6), "%,G\{12345.6}");
        test(String.format(l, "%,10G", -12345.6), "%,10G\{-12345.6}");
        test(String.format(l, "%,10G", 0.0), "%,10G\{0.0}");
        test(String.format(l, "%,10G", 12345.6), "%,10G\{12345.6}");
        test(String.format(l, "%,-10G", -12345.6), "%,-10G\{-12345.6}");
        test(String.format(l, "%,-10G", 0.0), "%,-10G\{0.0}");
        test(String.format(l, "%,-10G", 12345.6), "%,-10G\{12345.6}");
        test(String.format(l, "%+g", -12345.6), "%+g\{-12345.6}");
        test(String.format(l, "%+g", 0.0), "%+g\{0.0}");
        test(String.format(l, "%+g", 12345.6), "%+g\{12345.6}");
        test(String.format(l, "%+10g", -12345.6), "%+10g\{-12345.6}");
        test(String.format(l, "%+10g", 0.0), "%+10g\{0.0}");
        test(String.format(l, "%+10g", 12345.6), "%+10g\{12345.6}");
        test(String.format(l, "%+-10g", -12345.6), "%+-10g\{-12345.6}");
        test(String.format(l, "%+-10g", 0.0), "%+-10g\{0.0}");
        test(String.format(l, "%+-10g", 12345.6), "%+-10g\{12345.6}");
        test(String.format(l, "%+G", -12345.6), "%+G\{-12345.6}");
        test(String.format(l, "%+G", 0.0), "%+G\{0.0}");
        test(String.format(l, "%+G", 12345.6), "%+G\{12345.6}");
        test(String.format(l, "%+10G", -12345.6), "%+10G\{-12345.6}");
        test(String.format(l, "%+10G", 0.0), "%+10G\{0.0}");
        test(String.format(l, "%+10G", 12345.6), "%+10G\{12345.6}");
        test(String.format(l, "%+-10G", -12345.6), "%+-10G\{-12345.6}");
        test(String.format(l, "%+-10G", 0.0), "%+-10G\{0.0}");
        test(String.format(l, "%+-10G", 12345.6), "%+-10G\{12345.6}");
        test(String.format(l, "%,+g", -12345.6), "%,+g\{-12345.6}");
        test(String.format(l, "%,+g", 0.0), "%,+g\{0.0}");
        test(String.format(l, "%,+g", 12345.6), "%,+g\{12345.6}");
        test(String.format(l, "%,+10g", -12345.6), "%,+10g\{-12345.6}");
        test(String.format(l, "%,+10g", 0.0), "%,+10g\{0.0}");
        test(String.format(l, "%,+10g", 12345.6), "%,+10g\{12345.6}");
        test(String.format(l, "%,+-10g", -12345.6), "%,+-10g\{-12345.6}");
        test(String.format(l, "%,+-10g", 0.0), "%,+-10g\{0.0}");
        test(String.format(l, "%,+-10g", 12345.6), "%,+-10g\{12345.6}");
        test(String.format(l, "%,+G", -12345.6), "%,+G\{-12345.6}");
        test(String.format(l, "%,+G", 0.0), "%,+G\{0.0}");
        test(String.format(l, "%,+G", 12345.6), "%,+G\{12345.6}");
        test(String.format(l, "%,+10G", -12345.6), "%,+10G\{-12345.6}");
        test(String.format(l, "%,+10G", 0.0), "%,+10G\{0.0}");
        test(String.format(l, "%,+10G", 12345.6), "%,+10G\{12345.6}");
        test(String.format(l, "%,+-10G", -12345.6), "%,+-10G\{-12345.6}");
        test(String.format(l, "%,+-10G", 0.0), "%,+-10G\{0.0}");
        test(String.format(l, "%,+-10G", 12345.6), "%,+-10G\{12345.6}");

        test(String.format(l, "%g", -12345.6), "%g\{-12345.6}");
        test(String.format(l, "%g", 0.0), "%g\{0.0}");
        test(String.format(l, "%g", 12345.6), "%g\{12345.6}");
        test(String.format(l, "%10g", -12345.6), "%10g\{-12345.6}");
        test(String.format(l, "%10g", 0.0), "%10g\{0.0}");
        test(String.format(l, "%10g", 12345.6), "%10g\{12345.6}");
        test(String.format(l, "%-10g", -12345.6), "%-10g\{-12345.6}");
        test(String.format(l, "%-10g", 0.0), "%-10g\{0.0}");
        test(String.format(l, "%-10g", 12345.6), "%-10g\{12345.6}");
        test(String.format(l, "%G", -12345.6), "%G\{-12345.6}");
        test(String.format(l, "%G", 0.0), "%G\{0.0}");
        test(String.format(l, "%G", 12345.6), "%G\{12345.6}");
        test(String.format(l, "%10G", -12345.6), "%10G\{-12345.6}");
        test(String.format(l, "%10G", 0.0), "%10G\{0.0}");
        test(String.format(l, "%10G", 12345.6), "%10G\{12345.6}");
        test(String.format(l, "%-10G", -12345.6), "%-10G\{-12345.6}");
        test(String.format(l, "%-10G", 0.0), "%-10G\{0.0}");
        test(String.format(l, "%-10G", 12345.6), "%-10G\{12345.6}");
        test(String.format(l, "%,g", -12345.6), "%,g\{-12345.6}");
        test(String.format(l, "%,g", 0.0), "%,g\{0.0}");
        test(String.format(l, "%,g", 12345.6), "%,g\{12345.6}");
        test(String.format(l, "%,10g", -12345.6), "%,10g\{-12345.6}");
        test(String.format(l, "%,10g", 0.0), "%,10g\{0.0}");
        test(String.format(l, "%,10g", 12345.6), "%,10g\{12345.6}");
        test(String.format(l, "%,-10g", -12345.6), "%,-10g\{-12345.6}");
        test(String.format(l, "%,-10g", 0.0), "%,-10g\{0.0}");
        test(String.format(l, "%,-10g", 12345.6), "%,-10g\{12345.6}");
        test(String.format(l, "%,G", -12345.6), "%,G\{-12345.6}");
        test(String.format(l, "%,G", 0.0), "%,G\{0.0}");
        test(String.format(l, "%,G", 12345.6), "%,G\{12345.6}");
        test(String.format(l, "%,10G", -12345.6), "%,10G\{-12345.6}");
        test(String.format(l, "%,10G", 0.0), "%,10G\{0.0}");
        test(String.format(l, "%,10G", 12345.6), "%,10G\{12345.6}");
        test(String.format(l, "%,-10G", -12345.6), "%,-10G\{-12345.6}");
        test(String.format(l, "%,-10G", 0.0), "%,-10G\{0.0}");
        test(String.format(l, "%,-10G", 12345.6), "%,-10G\{12345.6}");
        test(String.format(l, "%(g", -12345.6), "%(g\{-12345.6}");
        test(String.format(l, "%(g", 0.0), "%(g\{0.0}");
        test(String.format(l, "%(g", 12345.6), "%(g\{12345.6}");
        test(String.format(l, "%(10g", -12345.6), "%(10g\{-12345.6}");
        test(String.format(l, "%(10g", 0.0), "%(10g\{0.0}");
        test(String.format(l, "%(10g", 12345.6), "%(10g\{12345.6}");
        test(String.format(l, "%(-10g", -12345.6), "%(-10g\{-12345.6}");
        test(String.format(l, "%(-10g", 0.0), "%(-10g\{0.0}");
        test(String.format(l, "%(-10g", 12345.6), "%(-10g\{12345.6}");
        test(String.format(l, "%(G", -12345.6), "%(G\{-12345.6}");
        test(String.format(l, "%(G", 0.0), "%(G\{0.0}");
        test(String.format(l, "%(G", 12345.6), "%(G\{12345.6}");
        test(String.format(l, "%(10G", -12345.6), "%(10G\{-12345.6}");
        test(String.format(l, "%(10G", 0.0), "%(10G\{0.0}");
        test(String.format(l, "%(10G", 12345.6), "%(10G\{12345.6}");
        test(String.format(l, "%(-10G", -12345.6), "%(-10G\{-12345.6}");
        test(String.format(l, "%(-10G", 0.0), "%(-10G\{0.0}");
        test(String.format(l, "%(-10G", 12345.6), "%(-10G\{12345.6}");
        test(String.format(l, "%,(g", -12345.6), "%,(g\{-12345.6}");
        test(String.format(l, "%,(g", 0.0), "%,(g\{0.0}");
        test(String.format(l, "%,(g", 12345.6), "%,(g\{12345.6}");
        test(String.format(l, "%,(10g", -12345.6), "%,(10g\{-12345.6}");
        test(String.format(l, "%,(10g", 0.0), "%,(10g\{0.0}");
        test(String.format(l, "%,(10g", 12345.6), "%,(10g\{12345.6}");
        test(String.format(l, "%,(-10g", -12345.6), "%,(-10g\{-12345.6}");
        test(String.format(l, "%,(-10g", 0.0), "%,(-10g\{0.0}");
        test(String.format(l, "%,(-10g", 12345.6), "%,(-10g\{12345.6}");
        test(String.format(l, "%,(G", -12345.6), "%,(G\{-12345.6}");
        test(String.format(l, "%,(G", 0.0), "%,(G\{0.0}");
        test(String.format(l, "%,(G", 12345.6), "%,(G\{12345.6}");
        test(String.format(l, "%,(10G", -12345.6), "%,(10G\{-12345.6}");
        test(String.format(l, "%,(10G", 0.0), "%,(10G\{0.0}");
        test(String.format(l, "%,(10G", 12345.6), "%,(10G\{12345.6}");
        test(String.format(l, "%,(-10G", -12345.6), "%,(-10G\{-12345.6}");
        test(String.format(l, "%,(-10G", 0.0), "%,(-10G\{0.0}");
        test(String.format(l, "%,(-10G", 12345.6), "%,(-10G\{12345.6}");
        test(String.format(l, "%+g", -12345.6), "%+g\{-12345.6}");
        test(String.format(l, "%+g", 0.0), "%+g\{0.0}");
        test(String.format(l, "%+g", 12345.6), "%+g\{12345.6}");
        test(String.format(l, "%+10g", -12345.6), "%+10g\{-12345.6}");
        test(String.format(l, "%+10g", 0.0), "%+10g\{0.0}");
        test(String.format(l, "%+10g", 12345.6), "%+10g\{12345.6}");
        test(String.format(l, "%+-10g", -12345.6), "%+-10g\{-12345.6}");
        test(String.format(l, "%+-10g", 0.0), "%+-10g\{0.0}");
        test(String.format(l, "%+-10g", 12345.6), "%+-10g\{12345.6}");
        test(String.format(l, "%+G", -12345.6), "%+G\{-12345.6}");
        test(String.format(l, "%+G", 0.0), "%+G\{0.0}");
        test(String.format(l, "%+G", 12345.6), "%+G\{12345.6}");
        test(String.format(l, "%+10G", -12345.6), "%+10G\{-12345.6}");
        test(String.format(l, "%+10G", 0.0), "%+10G\{0.0}");
        test(String.format(l, "%+10G", 12345.6), "%+10G\{12345.6}");
        test(String.format(l, "%+-10G", -12345.6), "%+-10G\{-12345.6}");
        test(String.format(l, "%+-10G", 0.0), "%+-10G\{0.0}");
        test(String.format(l, "%+-10G", 12345.6), "%+-10G\{12345.6}");
        test(String.format(l, "%,+g", -12345.6), "%,+g\{-12345.6}");
        test(String.format(l, "%,+g", 0.0), "%,+g\{0.0}");
        test(String.format(l, "%,+g", 12345.6), "%,+g\{12345.6}");
        test(String.format(l, "%,+10g", -12345.6), "%,+10g\{-12345.6}");
        test(String.format(l, "%,+10g", 0.0), "%,+10g\{0.0}");
        test(String.format(l, "%,+10g", 12345.6), "%,+10g\{12345.6}");
        test(String.format(l, "%,+-10g", -12345.6), "%,+-10g\{-12345.6}");
        test(String.format(l, "%,+-10g", 0.0), "%,+-10g\{0.0}");
        test(String.format(l, "%,+-10g", 12345.6), "%,+-10g\{12345.6}");
        test(String.format(l, "%,+G", -12345.6), "%,+G\{-12345.6}");
        test(String.format(l, "%,+G", 0.0), "%,+G\{0.0}");
        test(String.format(l, "%,+G", 12345.6), "%,+G\{12345.6}");
        test(String.format(l, "%,+10G", -12345.6), "%,+10G\{-12345.6}");
        test(String.format(l, "%,+10G", 0.0), "%,+10G\{0.0}");
        test(String.format(l, "%,+10G", 12345.6), "%,+10G\{12345.6}");
        test(String.format(l, "%,+-10G", -12345.6), "%,+-10G\{-12345.6}");
        test(String.format(l, "%,+-10G", 0.0), "%,+-10G\{0.0}");
        test(String.format(l, "%,+-10G", 12345.6), "%,+-10G\{12345.6}");
        test(String.format(l, "%(+g", -12345.6), "%(+g\{-12345.6}");
        test(String.format(l, "%(+g", 0.0), "%(+g\{0.0}");
        test(String.format(l, "%(+g", 12345.6), "%(+g\{12345.6}");
        test(String.format(l, "%(+10g", -12345.6), "%(+10g\{-12345.6}");
        test(String.format(l, "%(+10g", 0.0), "%(+10g\{0.0}");
        test(String.format(l, "%(+10g", 12345.6), "%(+10g\{12345.6}");
        test(String.format(l, "%(+-10g", -12345.6), "%(+-10g\{-12345.6}");
        test(String.format(l, "%(+-10g", 0.0), "%(+-10g\{0.0}");
        test(String.format(l, "%(+-10g", 12345.6), "%(+-10g\{12345.6}");
        test(String.format(l, "%(+G", -12345.6), "%(+G\{-12345.6}");
        test(String.format(l, "%(+G", 0.0), "%(+G\{0.0}");
        test(String.format(l, "%(+G", 12345.6), "%(+G\{12345.6}");
        test(String.format(l, "%(+10G", -12345.6), "%(+10G\{-12345.6}");
        test(String.format(l, "%(+10G", 0.0), "%(+10G\{0.0}");
        test(String.format(l, "%(+10G", 12345.6), "%(+10G\{12345.6}");
        test(String.format(l, "%(+-10G", -12345.6), "%(+-10G\{-12345.6}");
        test(String.format(l, "%(+-10G", 0.0), "%(+-10G\{0.0}");
        test(String.format(l, "%(+-10G", 12345.6), "%(+-10G\{12345.6}");
        test(String.format(l, "%,(+g", -12345.6), "%,(+g\{-12345.6}");
        test(String.format(l, "%,(+g", 0.0), "%,(+g\{0.0}");
        test(String.format(l, "%,(+g", 12345.6), "%,(+g\{12345.6}");
        test(String.format(l, "%,(+10g", -12345.6), "%,(+10g\{-12345.6}");
        test(String.format(l, "%,(+10g", 0.0), "%,(+10g\{0.0}");
        test(String.format(l, "%,(+10g", 12345.6), "%,(+10g\{12345.6}");
        test(String.format(l, "%,(+-10g", -12345.6), "%,(+-10g\{-12345.6}");
        test(String.format(l, "%,(+-10g", 0.0), "%,(+-10g\{0.0}");
        test(String.format(l, "%,(+-10g", 12345.6), "%,(+-10g\{12345.6}");
        test(String.format(l, "%,(+G", -12345.6), "%,(+G\{-12345.6}");
        test(String.format(l, "%,(+G", 0.0), "%,(+G\{0.0}");
        test(String.format(l, "%,(+G", 12345.6), "%,(+G\{12345.6}");
        test(String.format(l, "%,(+10G", -12345.6), "%,(+10G\{-12345.6}");
        test(String.format(l, "%,(+10G", 0.0), "%,(+10G\{0.0}");
        test(String.format(l, "%,(+10G", 12345.6), "%,(+10G\{12345.6}");
        test(String.format(l, "%,(+-10G", -12345.6), "%,(+-10G\{-12345.6}");
        test(String.format(l, "%,(+-10G", 0.0), "%,(+-10G\{0.0}");
        test(String.format(l, "%,(+-10G", 12345.6), "%,(+-10G\{12345.6}");

        test(String.format(l, "%a", -12345.6), "%a\{-12345.6}");
        test(String.format(l, "%a", 0.0), "%a\{0.0}");
        test(String.format(l, "%a", 12345.6), "%a\{12345.6}");
        test(String.format(l, "%10a", -12345.6), "%10a\{-12345.6}");
        test(String.format(l, "%10a", 0.0), "%10a\{0.0}");
        test(String.format(l, "%10a", 12345.6), "%10a\{12345.6}");
        test(String.format(l, "%-10a", -12345.6), "%-10a\{-12345.6}");
        test(String.format(l, "%-10a", 0.0), "%-10a\{0.0}");
        test(String.format(l, "%-10a", 12345.6), "%-10a\{12345.6}");
        test(String.format(l, "%A", -12345.6), "%A\{-12345.6}");
        test(String.format(l, "%A", 0.0), "%A\{0.0}");
        test(String.format(l, "%A", 12345.6), "%A\{12345.6}");
        test(String.format(l, "%10A", -12345.6), "%10A\{-12345.6}");
        test(String.format(l, "%10A", 0.0), "%10A\{0.0}");
        test(String.format(l, "%10A", 12345.6), "%10A\{12345.6}");
        test(String.format(l, "%-10A", -12345.6), "%-10A\{-12345.6}");
        test(String.format(l, "%-10A", 0.0), "%-10A\{0.0}");
        test(String.format(l, "%-10A", 12345.6), "%-10A\{12345.6}");

        test("aaa%false", "aaa%%%b\{false}");
        test("aaa" + System.lineSeparator() + "false", "aaa%n%b\{false}");

        assertThrows(
                MissingFormatArgumentException.class,
                () -> String.format(l, "%10ba\{ false }"),
                "Format specifier '%10b is not immediately followed by an embedded expression'");

        assertThrows(
                MissingFormatArgumentException.class,
                () -> String.format(l, "%ba\{ false }"),
                "Format specifier '%b is not immediately followed by an embedded expression'");

        assertThrows(
                MissingFormatArgumentException.class,
                () -> String.format(l, "\{}%b"),
                "Format specifier '%b is not immediately followed by an embedded expression'");
        assertThrows(
                UnknownFormatConversionException.class,
                () -> String.format(l, "\{}%0"),
                "Conversion = '0'");
    }
}
