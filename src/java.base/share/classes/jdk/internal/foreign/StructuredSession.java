package jdk.internal.foreign;

import jdk.internal.foreign.SharedSession.SharedResourceList;
import jdk.internal.vm.ScopedValueContainer;
import jdk.internal.vm.StackableScope;
import jdk.internal.vm.annotation.ForceInline;

import java.util.Objects;

public final class StructuredSession extends MemorySessionImpl {

    private final ScopedValueContainer scopedValueContainer = new ScopedValueContainer();

    public StructuredSession(Thread owner) {
        super(owner, new SharedResourceList());
        scopedValueContainer.push();
    }

    public boolean isAccessibleBy(Thread thread) {
        Objects.requireNonNull(thread);
        return StackableScope.contains(scopedValueContainer);
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
        if (!StackableScope.contains(scopedValueContainer)) {
            throw WRONG_THREAD;
        }
    }

    void justClose() {
        checkValidState();
        if (Thread.currentThread() != ownerThread()) {
            throw wrongThread();
        }
        if (ScopedValueContainer.latest() != scopedValueContainer) {
            throw new IllegalStateException("Cannot close");
        }
        scopedValueContainer.popForcefully();
    }
}
