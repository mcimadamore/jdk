/*
 * @test
 * @enablePreview
 * @summary Smoke test for cached methods
 */
public class SmokeTest {

    static int static_init_count = 0;
    static int instance_init_count = 0;

    record Test(int x) {

        cached static int m_s() {
            static_init_count++;
            return 42;
        }

        cached int m_i() {
            instance_init_count++;
            return x + 42;
        }
    }

    public static void main(String[] args) {
        assertEquals(Test.m_s(), 42);
        assertEquals(Test.m_s(), 42);
        Test test = new Test(10);
        assertEquals(test.m_i(), 52);
        assertEquals(test.m_i(), 52);
        assertEquals(static_init_count, 1);
        assertEquals(instance_init_count, 1);
    }

    static void assertEquals(int i, int e) {
        if (i != e) {
            throw new AssertionError("expected " + i + ", got " + e);
        }
    }
}
