package io.roastedroot.treesitter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TreeSitterPoolTest {

    static final String JAVA_SOURCE = """
            package com.example;

            import java.util.List;
            import java.util.Map;
            import java.util.stream.Collectors;

            public class Customer {
                private Long id;
                private String name;
                private String email;
                private int orderCount;
                private double totalSpent;

                public Customer() {}

                public Customer(String name, String email) {
                    this.name = name;
                    this.email = email;
                }

                public Long getId() { return id; }
                public String getName() { return name; }
                public String getEmail() { return email; }

                public boolean isVip() {
                    return totalSpent > 1000.0 || orderCount > 50;
                }

                public static Map<Boolean, List<Customer>> partitionByVip(List<Customer> customers) {
                    return customers.stream().collect(Collectors.partitioningBy(Customer::isVip));
                }

                @Override
                public String toString() {
                    return "Customer{id=" + id + ", name='" + name + "'}";
                }
            }
            """;

    @Test
    void borrowAndReturn() throws Exception {
        try (TreeSitterPool pool = TreeSitterPool.create(2)) {
            try (TreeSitterPool.Loan loan = pool.borrow()) {
                try (TreeSitterParser parser = loan.instance().newParser(Language.JAVA);
                     TreeSitterTree tree = parser.parseString(JAVA_SOURCE)) {
                    assertNotNull(tree);
                    assertEquals("program", tree.rootNode().type());
                }
            }
        }
    }

    @Test
    void instanceReuse() throws Exception {
        try (TreeSitterPool pool = TreeSitterPool.create(1)) {
            Object first;
            try (TreeSitterPool.Loan loan = pool.borrow()) {
                first = loan.instance();
            }
            try (TreeSitterPool.Loan loan = pool.borrow()) {
                assertSame(first, loan.instance(), "Pool should reuse returned instances");
            }
        }
    }

    @Test
    void discardPreventsReuse() throws Exception {
        try (TreeSitterPool pool = TreeSitterPool.create(1)) {
            Object first;
            try (TreeSitterPool.Loan loan = pool.borrow()) {
                first = loan.instance();
                loan.discard();
            }
            try (TreeSitterPool.Loan loan = pool.borrow()) {
                assertNotSame(first, loan.instance(), "Discarded instance must not be reused");
            }
        }
    }

    @Test
    void closedPoolRejectsBorrow() throws Exception {
        TreeSitterPool pool = TreeSitterPool.create(1);
        pool.close();
        assertThrows(IllegalStateException.class, pool::borrow);
    }

    @Test
    void doubleCloseIsSafe() {
        TreeSitterPool pool = TreeSitterPool.create(1);
        pool.close();
        assertDoesNotThrow(pool::close);
    }

    @Test
    void loanAfterReturnThrows() throws Exception {
        try (TreeSitterPool pool = TreeSitterPool.create(1)) {
            TreeSitterPool.Loan loan = pool.borrow();
            loan.close();
            assertThrows(IllegalStateException.class, loan::instance);
        }
    }

    @Test
    void parallelParsingWithPool() throws Exception {
        int poolSize = 4;
        int taskCount = 100;

        try (TreeSitterPool pool = TreeSitterPool.create(poolSize)) {
            ExecutorService executor = Executors.newFixedThreadPool(poolSize);
            try {
                List<Future<String>> futures = new ArrayList<>();
                for (int i = 0; i < taskCount; i++) {
                    futures.add(executor.submit(() -> {
                        try (TreeSitterPool.Loan loan = pool.borrow();
                             TreeSitterParser parser = loan.instance().newParser(Language.JAVA);
                             TreeSitterTree tree = parser.parseString(JAVA_SOURCE)) {
                            assertNotNull(tree);
                            return tree.rootNode().type();
                        }
                    }));
                }

                for (Future<String> f : futures) {
                    assertEquals("program", f.get());
                }
            } finally {
                executor.shutdown();
                assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void poolLimitsMaxConcurrency() throws Exception {
        int poolSize = 2;
        int threadCount = 8;
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(threadCount);

        try (TreeSitterPool pool = TreeSitterPool.create(poolSize)) {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < threadCount; i++) {
                    futures.add(executor.submit(() -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        try (TreeSitterPool.Loan loan = pool.borrow();
                             TreeSitterParser parser = loan.instance().newParser(Language.JAVA);
                             TreeSitterTree tree = parser.parseString(JAVA_SOURCE)) {
                            int c = concurrent.incrementAndGet();
                            maxConcurrent.accumulateAndGet(c, Math::max);
                            Thread.sleep(50);
                            concurrent.decrementAndGet();
                        }
                        return null;
                    }));
                }

                for (Future<?> f : futures) {
                    f.get(30, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdown();
                assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
            }
        }

        assertTrue(maxConcurrent.get() <= poolSize,
                "Max concurrent borrows (" + maxConcurrent.get() + ") should not exceed pool size (" + poolSize + ")");
    }
}
