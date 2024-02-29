/*
 * @test /nodynamiccopyright/
 * @summary smoke test for string -> string template conversion
 * @enablePreview
 * @compile/fail/ref=StringConversionTest.out -XDrawDiagnostics StringConversionTest.java
 */
class StringConversionTest {
    static void testAssign(String prefix) {
        StringTemplate st1 = "Hello" + prefix; // bad
        StringTemplate st2 = "Hello"; // ok
        StringTemplate st3 = "{prefix}hello"; // ok
    }

    static void testCast(String prefix) {
        StringTemplate st1 = (StringTemplate) "Hello" + prefix; // bad
        StringTemplate st2 = (StringTemplate) "Hello"; // ok
        StringTemplate st3 = (StringTemplate) "{prefix}hello"; // ok
    }

    static void testCall(String prefix) {
        m("Hello" + prefix); // bad
        m("Hello"); // ok
        m("\{prefix}Hello"); // ok
    }

    static void testSwitch(String prefix) {
        switch ("Hello" + prefix) {
            case StringTemplate st -> { } // bad
        };
        switch ("Hello") {
            case StringTemplate st -> { } // ok
        };
        switch ("\{prefix}Hello") {
            case StringTemplate st -> { } // ok
        };
    }

    static void m(StringTemplate st) { }
}
