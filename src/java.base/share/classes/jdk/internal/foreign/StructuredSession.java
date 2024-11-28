package jdk.internal.foreign;

import jdk.internal.foreign.ConfinedSession.ConfinedResourceList;
import jdk.internal.vm.StackableScope;
import jdk.internal.vm.annotation.ForceInline;

import java.util.Objects;

public final class StructuredSession extends MemorySessionImpl {

    private final StackableScope stackableScope = new StackableScope() { };

    public StructuredSession(Thread owner) {
        super(owner, new ConfinedResourceList());
        stackableScope.push();
    }

    public boolean isAccessibleBy(Thread thread) {
        Objects.requireNonNull(thread);
        return StackableScope.contains(stackableScope);
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
        if (Thread.currentThread() != owner &&
                !StackableScope.contains(stackableScope)) {
            throw WRONG_THREAD;
        }
    }

    void justClose() {
        checkValidState();
        if (!stackableScope.tryPop()) {
            throw new IllegalStateException("Cannot close");
        }
    }
}
