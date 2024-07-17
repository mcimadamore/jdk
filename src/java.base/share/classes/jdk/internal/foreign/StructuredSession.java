package jdk.internal.foreign;

import jdk.internal.foreign.SharedSession.SharedResourceList;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.Arena;
import java.util.Objects;
import java.util.function.Function;

public final class StructuredSession extends MemorySessionImpl {

    private static final ScopedValue<Object> KEY = ScopedValue.newInstance();
    private final Object sentinel = new Object();

    public StructuredSession(Thread owner) {
        super(owner, new SharedResourceList());
    }

    public boolean isAccessibleBy(Thread thread) {
        Objects.requireNonNull(thread);
        return KEY.get() == sentinel;
    }

    @Override
    @ForceInline
    public void acquire0() {
        checkValidState();
        // do nothing
    }

    @Override
    @ForceInline
    public void release0() {
        // do nothing
    }

    @Override
    @ForceInline
    void checkThreadRaw() {
        if (KEY.get() != sentinel) {
            throw WRONG_THREAD;
        }
    }

    void justClose() {
        throw new UnsupportedOperationException();
    }

    public final <Z> Z call(Function<Arena, Z> arenaFunction) {
        return ScopedValue.callWhere(KEY, sentinel,
                () -> arenaFunction.apply(asArena()));
    }
}
