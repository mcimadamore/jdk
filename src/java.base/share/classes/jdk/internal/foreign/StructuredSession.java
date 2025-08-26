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

    private int asyncReleaseCount = 0;
    private int asyncAcquireCount = 0;

    static final VarHandle ASYNC_RELEASE_COUNT= MhUtil.findVarHandle(MethodHandles.lookup(), "asyncReleaseCount", int.class);
    static final VarHandle ASYNC_ACQUIRE_COUNT= MhUtil.findVarHandle(MethodHandles.lookup(), "asyncAcquireCount", int.class);

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
        if (Thread.currentThread() == owner) {
            if (acquireCount == MAX_FORKS) {
                throw tooManyAcquires();
            }
            acquireCount++;
        } else {
            // It is possible to end up here in two cases: this session was kept alive by some other confined session
            // which is implicitly released (in which case the release call comes from the cleaner thread). Or,
            // this session might be kept alive by a shared session, which means the release call can come from any
            // thread.
            int old = (int)ASYNC_ACQUIRE_COUNT.getAndAdd(this, 1);
            if (old == MAX_FORKS) {
                ASYNC_ACQUIRE_COUNT.getAndAdd(this, -1);
                throw tooManyAcquires();
            }
        }
    }

    @Override
    @ForceInline
    public void release0() {
        if (Thread.currentThread() == owner) {
            acquireCount--;
        } else {
            // It is possible to end up here in two cases: this session was kept alive by some other confined session
            // which is implicitly released (in which case the release call comes from the cleaner thread). Or,
            // this session might be kept alive by a shared session, which means the release call can come from any
            // thread.
            ASYNC_RELEASE_COUNT.getAndAdd(this, 1);
        }
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
        int asyncReleaseCount = (int)ASYNC_RELEASE_COUNT.getVolatile(this);
        int asyncAcquireCount = (int)ASYNC_ACQUIRE_COUNT.getVolatile(this);
        int acquire = acquireCount - asyncReleaseCount + asyncAcquireCount;
        if (acquire == 0) {
            state = CLOSED;
            flock.close();
        }
    }
}
