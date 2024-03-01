/*
 * @test /nodynamiccopyright/
 * @summary smoke test for string -> string template conversion
 * @enablePreview
 * @compile/fail/ref=StringConversionTest.out -XDrawDiagnostics StringConversionTest.java
 */
class StringConversionTest {
    static void testAssign(String prefix) {
        StringTemplate st1 = "Hello" + prefix; // bad
        StringTemplate st2 = "Hello"t; // ok
        StringTemplate st3 = "{prefix}hello"t; // ok
    }

    static void testCast(String prefix) {
        StringTemplate st1 = (StringTemplate) "Hello" + prefix; // bad
        StringTemplate st2 = (StringTemplate) "Hello"t; // ok
        StringTemplate st3 = (StringTemplate) "{prefix}hello"t; // ok
    }

    static void testCall(String prefix) {
        m("Hello" + prefix); // bad
        m("Hello"t); // ok
        m("\{prefix}Hello"t); // ok
    }

    static void testSwitch(String prefix) {
        switch ("Hello" + prefix) {
            case StringTemplate st -> { } // bad
        };
        switch ("Hello"t) {
            case StringTemplate st -> { } // ok
        };
        switch ("\{prefix}Hello"t) {
            case StringTemplate st -> { } // ok
        };
    }

    static void m(StringTemplate st) { }
}
