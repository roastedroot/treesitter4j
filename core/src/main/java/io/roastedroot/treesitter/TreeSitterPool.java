package io.roastedroot.treesitter;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe pool of {@link TreeSitter} instances.
 *
 * <p>Each {@link TreeSitter} owns an isolated WASM {@code Instance} with its own linear memory,
 * so instances must not be shared across threads. This pool caps the number of live instances
 * and reuses them across parse operations.
 *
 * <p>Uses only lock-free data structures and {@link Semaphore} internally — no {@code synchronized}
 * blocks — so it is safe to use with virtual threads (no carrier-thread pinning).
 *
 * <pre>{@code
 * var pool = TreeSitterPool.create(4);
 *
 * try (var loan = pool.borrow()) {
 *     try (var parser = loan.instance().newParser(Language.JAVA);
 *          var tree = parser.parseString(source)) {
 *         // use tree
 *     }
 * }
 *
 * pool.close();
 * }</pre>
 */
public final class TreeSitterPool implements AutoCloseable {

    private final ConcurrentLinkedDeque<TreeSitter> idle;
    private final Semaphore permits;
    private final AtomicBoolean closed = new AtomicBoolean();

    private TreeSitterPool(int maxSize) {
        this.idle = new ConcurrentLinkedDeque<>();
        this.permits = new Semaphore(maxSize);
    }

    /**
     * Creates a pool that allows at most {@code maxSize} concurrent {@link TreeSitter} instances.
     */
    public static TreeSitterPool create(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, got: " + maxSize);
        }
        return new TreeSitterPool(maxSize);
    }

    /**
     * Borrows a {@link TreeSitter} instance from the pool, blocking if the pool is at capacity.
     *
     * <p>The returned {@link Loan} <b>must</b> be closed when done (preferably via
     * try-with-resources) to return the instance to the pool.
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @throws IllegalStateException if the pool has been closed
     */
    public Loan borrow() throws InterruptedException {
        if (closed.get()) {
            throw new IllegalStateException("Pool is closed");
        }
        permits.acquire();
        try {
            TreeSitter ts = idle.pollFirst();
            if (ts == null) {
                ts = TreeSitter.create();
            }
            return new Loan(this, ts);
        } catch (RuntimeException e) {
            permits.release();
            throw e;
        }
    }

    private void release(TreeSitter ts) {
        try {
            if (!closed.get()) {
                idle.offerFirst(ts);
            } else {
                ts.close();
            }
        } finally {
            permits.release();
        }
    }

    private void discard() {
        permits.release();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            TreeSitter ts;
            while ((ts = idle.pollFirst()) != null) {
                ts.close();
            }
        }
    }

    /**
     * A loan of a {@link TreeSitter} instance from the pool.
     *
     * <p>Must be closed to return the instance to the pool. If an error leaves the instance
     * in a bad state, call {@link #discard()} before closing to destroy it rather than
     * returning it.
     */
    public static final class Loan implements AutoCloseable {
        private final TreeSitterPool pool;
        private TreeSitter ts;
        private boolean discarded;

        Loan(TreeSitterPool pool, TreeSitter ts) {
            this.pool = pool;
            this.ts = ts;
        }

        public TreeSitter instance() {
            if (ts == null) {
                throw new IllegalStateException("Loan already returned");
            }
            return ts;
        }

        @Override
        public void close() {
            if (ts != null) {
                if (discarded) {
                    ts.close();
                    pool.discard();
                } else {
                    pool.release(ts);
                }
                ts = null;
            }
        }

        /**
         * Marks this instance for destruction rather than return to the pool.
         * The instance is destroyed when the loan is closed.
         */
        public void discard() {
            discarded = true;
        }
    }
}
