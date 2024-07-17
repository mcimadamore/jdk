package jdk.internal.foreign;

import jdk.internal.foreign.SharedSession.SharedResourceList;
import jdk.internal.misc.ThreadFlock;
import jdk.internal.vm.ScopedValueContainer;
import jdk.internal.vm.StackableScope;
import jdk.internal.vm.annotation.ForceInline;

import java.util.Objects;

public final class StructuredSession extends MemorySessionImpl {

    private final ThreadFlock flock = ThreadFlock.open("Arena$" + this.hashCode());

    public StructuredSession(Thread owner) {
        super(owner, new SharedResourceList());
    }

    public boolean isAccessibleBy(Thread thread) {
        Objects.requireNonNull(thread);
        return thread == owner || flock.containsThread(thread);
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
        Thread current = Thread.currentThread();
        if (current != owner && !flock.containsThread(current)) {
            throw WRONG_THREAD;
        }
    }

    void justClose() {
        checkValidState();
        if (Thread.currentThread() != ownerThread()) {
            throw wrongThread();
        }
        if (flock.threads().findAny().isPresent()) {
            throw new IllegalStateException("Cannot close");
        }
        flock.close();
    }
}
