package jdk.internal.foreign;

import jdk.internal.foreign.SharedSession.SharedResourceList;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.Arena;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class StructuredSession extends MemorySessionImpl {

    private final ScopedValue<Object> scopedValue = ScopedValue.newInstance();

    public StructuredSession(Thread owner) {
        super(owner, new SharedResourceList());
    }

    public boolean isAccessibleBy(Thread thread) {
        Objects.requireNonNull(thread);
        return scopedValue.isBound();
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
        if (!scopedValue.isBound()) {
            throw WRONG_THREAD;
        }
    }

    void justClose() {
        throw new UnsupportedOperationException();
    }

    public final <Z> Z call(Function<Arena, Z> arenaFunction) {
        return ScopedValue.where(scopedValue, new Object())
                .call(() -> arenaFunction.apply(asArena()));
    }
}
