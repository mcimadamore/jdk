package jdk.internal.foreign.abi;

import java.security.PrivilegedAction;

public class NativeTypeInfo {

    static {
        loadLibrary();
    }

    @SuppressWarnings("removal")
    private static void loadLibrary() {
        java.security.AccessController.doPrivileged(
                (PrivilegedAction<Void>) () -> {
                    System.loadLibrary("nativeTypeInfo");
                    return null;
                });
    }

    public static final int SIZEOF_SHORT = sizeof_short();
    public static final int SIZEOF_INT = sizeof_int();
    public static final int SIZEOF_LONG = sizeof_long();
    public static final int SIZEOF_WCHAR = sizeof_wchar();
    public static final boolean SIGNOF_WCHAR = signof_wchar();
    public static final boolean SIGNOF_CHAR = signof_char();
    public static final int ALIGNOF_LONG_LONG = alignof_long_long();
    public static final int ALIGNOF_DOUBLE = alignof_double();

    private static native int sizeof_short();
    private static native int sizeof_int();
    private static native int sizeof_long();
    private static native int sizeof_wchar();
    private static native boolean signof_wchar();
    private static native boolean signof_char();
    private static native int alignof_long_long();
    private static native int alignof_double();
}
