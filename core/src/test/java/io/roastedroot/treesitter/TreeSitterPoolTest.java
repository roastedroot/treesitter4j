package io.roastedroot.treesitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class TreeSitterPoolTest {

    @Test
    void poolSizeIsReported() {
        try (var pool = new TreeSitterPool(4)) {
            assertEquals(4, pool.size());
        }
    }

    @Test
    void rejectsNonPositiveSize() {
        assertThrows(IllegalArgumentException.class, () -> new TreeSitterPool(0));
        assertThrows(IllegalArgumentException.class, () -> new TreeSitterPool(-1));
    }

    @Test
    void singleThreadParsing() throws Exception {
        long start = System.nanoTime();
        try (var pool = new TreeSitterPool(1)) {
            String sexp = pool.execute(ts -> {
                try (var parser = ts.newParser()) {
                    parser.setLanguage(Language.JAVA);
                    try (var tree = parser.parseString("class Foo {}")) {
                        return tree.rootNode().toSexp();
                    }
                }
            });
            assertNotNull(sexp);
            assertTrue(sexp.contains("class_declaration"));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("singleThreadParsing: " + elapsedMs + " ms (pool=1, tasks=1)");
    }

    @Test
    void concurrentParsingWithVirtualThreads() throws Exception {
        int poolSize = 4;
        int taskCount = 20;

        List<String> sources = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            sources.add("class Task" + i + " { void run() {} }");
        }

        AtomicInteger successCount = new AtomicInteger();

        long start = System.nanoTime();
        try (var pool = new TreeSitterPool(poolSize);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<String>> futures = new ArrayList<>();
            for (String source : sources) {
                futures.add(executor.submit(() ->
                    pool.execute(ts -> {
                        try (var parser = ts.newParser()) {
                            parser.setLanguage(Language.JAVA);
                            try (var tree = parser.parseString(source)) {
                                successCount.incrementAndGet();
                                return tree.rootNode().toSexp();
                            }
                        }
                    })
                ));
            }

            for (Future<String> future : futures) {
                String sexp = future.get();
                assertNotNull(sexp);
                assertTrue(sexp.contains("class_declaration"));
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("concurrentParsingWithVirtualThreads: " + elapsedMs + " ms (pool=" + poolSize + ", tasks=" + taskCount + ")");

        assertEquals(taskCount, successCount.get());
    }

    @Test
    void sequentialParsingBaseline() throws Exception {
        int taskCount = 20;

        List<String> sources = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            sources.add("class Task" + i + " { void run() {} }");
        }

        long start = System.nanoTime();
        try (var ts = TreeSitter.create();
             var parser = ts.newParser()) {
            parser.setLanguage(Language.JAVA);
            for (String source : sources) {
                try (var tree = parser.parseString(source)) {
                    assertNotNull(tree.rootNode().toSexp());
                }
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("sequentialParsingBaseline: " + elapsedMs + " ms (no pool, tasks=" + taskCount + ")");
    }

    @Test
    void voidExecuteWorks() throws Exception {
        AtomicInteger counter = new AtomicInteger();

        long start = System.nanoTime();
        try (var pool = new TreeSitterPool(2)) {
            pool.execute(ts -> {
                try (var parser = ts.newParser()) {
                    parser.setLanguage(Language.JSON);
                    try (var tree = parser.parseString("{\"key\": 1}")) {
                        assertNotNull(tree.rootNode());
                        counter.incrementAndGet();
                    }
                }
            });
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("voidExecuteWorks: " + elapsedMs + " ms (pool=2, tasks=1)");

        assertEquals(1, counter.get());
    }
}