package java.util.concurrent;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.misc.ThreadFlock;

/**
 * A container for zero or more threads. A scope is guaranteed to be {@linkplain #isAlive() alive} as
 * long as it contains one or more running threads. Thread scopes can nest. That is, a thread scope TS1
 * is said to be contained by another thread scope TS2 if TS1 is nested inside TS2.
 * If that happens, then TS1 is guaranteed to remain alive until TS2 is.
 */
@PreviewFeature(feature = PreviewFeature.Feature.STRUCTURED_CONCURRENCY)
public sealed interface ThreadScope permits ThreadFlock, ThreadFlock.RootScope {
    /**
     * {@return the name of this scope, may be {@code null}}
     */
    String name();

    /**
     * {@return the parent of this container or null if this is the root container}
     */
    ThreadScope parent();

    /**
     * {@return {@code true} if this scope is contained by the provided scope}
     * @param that thread scope
     */
    boolean isContainedBy(ThreadScope that);

    /**
     * {@return {@true}, if all the threads in this scope have terminated}
     */
    boolean isAlive();

    /**
     * The thread scope root. Every thread scope is contained by the root scope.
     */
    ThreadScope ROOT = ThreadFlock.RootScope.INSTANCE;
}
