package jdk.internal.foreign;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.ConfinedSession.ConfinedResourceList;
import jdk.internal.foreign.MemorySessionImpl.ResourceList.ResourceCleanup;
import jdk.internal.misc.ThreadFlock;
import jdk.internal.vm.annotation.ForceInline;

import java.util.Objects;

public final class StructuredSession extends MemorySessionImpl {
    private final ThreadFlock flock;

    public StructuredSession(ThreadFlock flock) {
        super(flock.owner(), new ConfinedResourceList());
        this.flock = flock;
        this.state = NONCLOSEABLE;
    }

    public boolean isAccessibleBy(Thread thread) {
        Objects.requireNonNull(thread);
        return flock.containsThread(thread);
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
        if (current != owner && !flock.containsThread(current)) {
            throw WRONG_THREAD;
        }
    }

    void justClose() {
        throw nonCloseable();
    }

    public void closeInternal() {
        resourceList.cleanup();
    }
}
