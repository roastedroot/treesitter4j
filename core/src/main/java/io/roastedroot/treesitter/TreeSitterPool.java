package io.roastedroot.treesitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A thread-safe pool of {@link TreeSitter} instances for concurrent usage.
 *
 * <p>Each {@link TreeSitter} wraps a Chicory {@code Instance} which is <b>not thread-safe</b>.
 * This pool manages a fixed number of {@code TreeSitter} instances and lends them to callers
 * via {@link #execute(Function)} or {@link #execute(Consumer)}. When all instances are in use,
 * callers block until one becomes available — virtual threads will park without pinning a
 * carrier thread since the pool uses {@link ArrayBlockingQueue} (backed by {@code ReentrantLock}).
 *
 * <p>The underlying Wasm {@code Module} is loaded once (singleton) and shared across all
 * instances — only the Chicory {@code Instance} (memory, globals, stack) is duplicated.
 *
 * <p>Usage with virtual threads:
 * <pre>{@code
 * try (var pool = new TreeSitterPool(Runtime.getRuntime().availableProcessors());
 *      var executor = Executors.newVirtualThreadPerTaskExecutor()) {
 *
 *     List<Future<String>> futures = files.stream()
 *         .map(file -> executor.submit(() ->
 *             pool.execute(ts -> {
 *                 try (var parser = ts.newParser()) {
 *                     parser.setLanguage(Language.JAVA);
 *                     try (var tree = parser.parseString(source)) {
 *                         return tree.rootNode().toSexp();
 *                     }
 *                 }
 *             })
 *         ))
 *         .toList();
 * }
 * }</pre>
 */
public class TreeSitterPool implements AutoCloseable {

    private final BlockingQueue<TreeSitter> pool;
    private final List<TreeSitter> allInstances;

    /**
     * Creates a pool with the given number of {@link TreeSitter} instances.
     *
     * @param poolSize the number of instances to pre-create (must be &gt; 0)
     * @throws IllegalArgumentException if poolSize is not positive
     */
    public TreeSitterPool(int poolSize) {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be positive, got: " + poolSize);
        }
        this.allInstances = new ArrayList<>(poolSize);
        this.pool = new ArrayBlockingQueue<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            TreeSitter ts = TreeSitter.create();
            allInstances.add(ts);
            pool.offer(ts);
        }
    }

    /**
     * Borrows a {@link TreeSitter} instance, applies the given function, and returns the instance
     * to the pool. Blocks if no instance is currently available.
     *
     * @param action the function to execute with a borrowed instance
     * @param <T>    the return type
     * @return the result of the function
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public <T> T execute(Function<TreeSitter, T> action) throws InterruptedException {
        TreeSitter ts = pool.take();
        try {
            return action.apply(ts);
        } finally {
            pool.offer(ts);
        }
    }

    /**
     * Borrows a {@link TreeSitter} instance, runs the given consumer, and returns the instance
     * to the pool. Blocks if no instance is currently available.
     *
     * @param action the consumer to execute with a borrowed instance
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public void execute(Consumer<TreeSitter> action) throws InterruptedException {
        TreeSitter ts = pool.take();
        try {
            action.accept(ts);
        } finally {
            pool.offer(ts);
        }
    }

    /**
     * Returns the total number of instances managed by this pool.
     */
    public int size() {
        return allInstances.size();
    }

    /**
     * Closes all pooled {@link TreeSitter} instances.
     */
    @Override
    public void close() {
        for (TreeSitter ts : allInstances) {
            ts.close();
        }
    }
}