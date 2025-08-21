package jdk.internal.foreign;

import jdk.internal.foreign.ConfinedSession.ConfinedResourceList;
import jdk.internal.foreign.MemorySessionImpl.ResourceList.ResourceCleanup;
import jdk.internal.misc.ThreadFlock;
import jdk.internal.vm.annotation.ForceInline;

import java.util.Objects;

public final class StructuredSession extends MemorySessionImpl {

    private final ThreadFlock flock = ThreadFlock.open("Arena$" + this.hashCode());

    public StructuredSession(Thread owner) {
        super(owner, new ConfinedResourceList());
    }

    public boolean isAccessibleBy(Thread thread) {
        Objects.requireNonNull(thread);
        return thread == owner || flock.containsThread(thread);
    }

    private void checkOwner() {
        if (Thread.currentThread() != owner) {
            throw new WrongThreadException();
        }
    }

    @Override
    void addInternal(ResourceCleanup resource) {
        checkOwner();
        super.addInternal(resource);
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
        if (current != owner && !flock.containsThreadFast(current)) {
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
