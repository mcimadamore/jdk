/*
 * @test /nodynamiccopyright/
 * @enablePreview
 * @summary Check declaration sites for cached methods.
 * @compile/fail/ref=CachedMethodDeclarations.out -XDrawDiagnostics CachedMethodDeclarations.java
 */

class CachedMethodDeclarations {
    class C {
        cached int instanceMethod() { return 1; } // yep
        static cached int staticMethod() { return 2; } // yep
        native cached int instanceMethod(); // nope
    }

    abstract class AC {
        cached int instanceMethod() { return 1; } // yep
        static cached int staticMethod() { return 2; } // yep
        abstract int abstractMethod(); // nope
    }

    record R(int i) {
        cached int instanceMethod() { return i; } // yep
        static cached int staticMethod() { return 2; } // yep
    }

    enum E {
        A;

        cached int instanceMethod() { return ordinal(); } // yep
        static cached int staticMethod() { return 2; } // yep
    }

    interface I {
        default cached int defaultMethod() { return 1; } // nope
        private cached int instanceMethod() { return 1; } // nope
        static cached int staticMethod() { return 2; } // nope
    }

    @interface A {
        cached int instanceMethod(); // nope
    }
}
