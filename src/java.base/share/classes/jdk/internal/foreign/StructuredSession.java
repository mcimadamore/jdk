package jdk.internal.foreign;

import jdk.internal.foreign.ConfinedSession.ConfinedResourceList;
import jdk.internal.foreign.MemorySessionImpl.ResourceList.ResourceCleanup;
import jdk.internal.invoke.MhUtil;
import jdk.internal.misc.ThreadFlock;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
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
        if (Thread.currentThread() == owner) {
            if (acquireCount == MAX_FORKS) {
                throw tooManyAcquires();
            }
            acquireCount++;
        }
        // if the acquiring thread is some other thread nested in this session' flock
        // we don't need to do anything -- after all we cannot close this session
        // until all the threads nested in this flock have completed
    }

    @Override
    @ForceInline
    public void release0() {
        if (Thread.currentThread() == owner) {
            acquireCount--;
        }
        // if the releasing thread is some other thread nested in this session' flock
        // we don't need to do anything -- after all we cannot close this session
        // until all the threads nested in this flock have completed
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
        checkOwner();
        if (flock.threads().findAny().isPresent()) {
            throw new IllegalStateException("Cannot close");
        }
        if (acquireCount == 0) {
            state = CLOSED;
            flock.close();
        }
    }
}
