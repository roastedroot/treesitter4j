package io.roastedroot.treesitter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class TreeSitterParallelTest {

    static final String JAVA_CODE = "class Hello { void greet() {} }";
    static final String JSON_CODE = "{\"key\": \"value\"}";
    static final String YAML_CODE = "name: test\nversion: 1";

    record ParseTask(Language language, String code, String expectedRootType) {}

    static final List<ParseTask> TASKS = List.of(
            new ParseTask(Language.JAVA, JAVA_CODE, "program"),
            new ParseTask(Language.JSON, JSON_CODE, "document"),
            new ParseTask(Language.YAML, YAML_CODE, "stream"));

    private void runParallelParsing(ExecutorService executor, int tasksPerLanguage) throws Exception {
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < tasksPerLanguage; i++) {
            for (ParseTask task : TASKS) {
                futures.add(executor.submit(() -> {
                    try (var ts = TreeSitter.create();
                         var parser = ts.newParser(task.language());
                         var tree = parser.parseString(task.code())) {

                        var root = tree.rootNode();
                        assertEquals(task.expectedRootType(), root.type());
                        assertTrue(root.childCount() > 0);
                        return root.type();
                    }
                }));
            }
        }

        for (Future<String> f : futures) {
            assertNotNull(f.get());
        }
    }

    @Test
    void parallelWithPlatformThreads() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            runParallelParsing(executor, 10);
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void parallelWithVirtualThreads() throws Exception {
        Method method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
        ExecutorService executor = (ExecutorService) method.invoke(null);
        try {
            runParallelParsing(executor, 10);
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
        }
    }

    @Test
    void instanceCreationIsCheap() {
        int warmup = 3;
        for (int i = 0; i < warmup; i++) {
            try (var ts = TreeSitter.create()) {
                ts.newParser().close();
            }
        }

        int iterations = 20;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            try (var ts = TreeSitter.create();
                 var parser = ts.newParser(Language.JAVA);
                 var tree = parser.parseString(JAVA_CODE)) {
                assertNotNull(tree.rootNode());
            }
        }
        long elapsed = System.nanoTime() - start;
        double avgMs = (elapsed / 1e6) / iterations;

        System.out.printf("Average instance create+parse: %.2f ms%n", avgMs);
        assertTrue(avgMs < 2000, "Instance creation + parse should be under 2s, was " + avgMs + "ms");
    }
}
