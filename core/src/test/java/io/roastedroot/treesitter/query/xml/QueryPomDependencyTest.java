package io.roastedroot.treesitter.query.xml;

import io.roastedroot.treesitter.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class QueryPomDependencyTest {

    private static TreeSitter ts;
    private static TreeSitterParser parser;

    public record Dependency(String blockText, String groupId, String artifactId, String version) {}

    @BeforeAll
    static void init() {
        ts = TreeSitter.create();
        parser = ts.newParser(Language.XML);
    }

    @AfterAll
    static void cleanup() {
        ts.close();
    }

    String source = """
            <?xml version="1.0" encoding="UTF-8"?>
             <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
             	<modelVersion>4.0.0</modelVersion>
             	<groupId>com.todo</groupId>
             	<artifactId>app</artifactId>
             	<version>0.0.1-SNAPSHOT</version>
             	<name>app</name>
             	<description>Demo project for Spring Boot</description>
             	<properties>
             		<java.version>21</java.version>
             	</properties>
             	<parent>
             		<groupId>org.springframework.boot</groupId>
             		<artifactId>spring-boot-starter-parent</artifactId>
             		<version>3.5.3</version>
             		<relativePath/> <!-- lookup parent from repository -->
             	</parent>
             	<dependencies>
             		<dependency>
             			<groupId>org.springframework.boot</groupId>
             			<artifactId>spring-boot-starter-data-jpa</artifactId>
             		</dependency>
             		<dependency>
             			<groupId>org.springframework.boot</groupId>
             			<artifactId>spring-boot-starter-thymeleaf</artifactId>
             		</dependency>
             		<dependency>
             			<groupId>org.springframework.boot</groupId>
             			<artifactId>spring-boot-starter-web</artifactId>
             		</dependency>
            
             		<dependency>
             			<groupId>org.springframework.boot</groupId>
             			<artifactId>spring-boot-devtools</artifactId>
             			<scope>runtime</scope>
             			<optional>true</optional>
             		</dependency>
             		<dependency>
             			<groupId>com.mysql</groupId>
             			<artifactId>mysql-connector-j</artifactId>
             			<scope>runtime</scope>
             		</dependency>
             		<dependency>
             			<groupId>org.springframework.boot</groupId>
             			<artifactId>spring-boot-starter-test</artifactId>
             			<scope>test</scope>
             		</dependency>
            
             		<!--
             		<dependency>
             			<groupId>org.springframework.boot</groupId>
             			<artifactId>spring-boot-starter-security</artifactId>
             		</dependency>
             		-->
             		<dependency>
             				<groupId>io.jsonwebtoken</groupId>
             				<artifactId>jjwt</artifactId>
             				<version>0.9.1</version>
             		</dependency>
             	</dependencies>
            
             	<build>
             		<plugins>
             			<plugin>
             				<groupId>org.springframework.boot</groupId>
             				<artifactId>spring-boot-maven-plugin</artifactId>
             			</plugin>
             		</plugins>
             	</build>
             </project>
            """;

    // Elapsed time is about: 20-22s :-(
    @Test
    void queryPomDependencyAndVersion() {
        String query = """
                (element
                  (STag . (Name) @tag.dep (#match? @tag.dep "(dependency|parent)"))
                  (content
                    (element
                      (STag . (Name) @tag.g (#eq? @tag.g "groupId"))
                      (content . (CharData) @groupId))
                    (element
                      (STag . (Name) @tag.a (#eq? @tag.a "artifactId"))
                      (content . (CharData) @artifactId))
                    (element
                      (STag . (Name) @tag.o (#match? @tag.o "(version|scope|optional)"))
                      (content . (CharData) @version))?
                  )) @dependency.block
                """;

        long startTime = System.nanoTime();

        var tree = parser.parseString(source);
        var treeQuery = ts.newQuery(Language.XML, query);

        TreeSitterNode rootNode = tree.rootNode();

        assertEquals(1, treeQuery.patternCount());
        assertEquals(8, treeQuery.captureCount());
        assertEquals("tag.dep", treeQuery.captureName(0));

        List<TreeSitterQueryResult> captures = treeQuery.exec(rootNode, source);
        assertFalse(captures.isEmpty());

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        System.out.println("Elapsed: " + elapsedMs + " ms");

        TreeSitterQueryResult capture = captures.get(0);
        assertEquals("dependency.block", capture.name());
        assertEquals("element", capture.node().type());

        showCaptures(captures);

        /*
        capture = captures.get(26);
        String pom = source.substring(
                capture.node().startByte(),
                capture.node().endByte());
        assertTrue(pom.contains("<groupId>io.jsonwebtoken</groupId>"));
        assertTrue(pom.contains("<artifactId>jjwt</artifactId"));
        assertTrue(pom.contains("<version>0.9.1</version"));
         */

        boolean hasFullJjwtDependency = captures.stream()
                // 1. Isolate just the parent wrapper blocks
                .filter(c -> "dependency.block".equals(c.name()))

                // 2. Extract the string from the source
                .map(c -> {
                    int start = c.node().startByte();
                    int end = c.node().endByte();
                    // Note: Use getBytes() if you are still working around byte-index drift bugs
                    return source.substring(start, end);
                })

                // 3. Ensure the single block text cluster satisfies ALL your criteria simultaneously
                .anyMatch(blockText ->
                        blockText.contains("<groupId>io.jsonwebtoken</groupId>") &&
                                blockText.contains("<artifactId>jjwt</artifactId>") &&
                                blockText.contains("<version>0.9.1</version>")
                );

        assertTrue(hasFullJjwtDependency);

    }

    @Test
    void queryPomDependency() {
        String query = """
                (element
                  (STag . (Name) @tag.dep (#eq? @tag.dep "dependency"))
                  (content
                    (element
                      (STag . (Name) @tag.g (#eq? @tag.g "groupId"))
                      (content . (CharData) @group.id))
                    (element
                      (STag . (Name) @tag.a (#eq? @tag.a "artifactId"))
                      (content . (CharData) @artifact.id))
                  )) @dependency.block
                """;

        long startTime = System.nanoTime();

        var tree = parser.parseString(source);
        var treeQuery = ts.newQuery(Language.XML, query);

        TreeSitterNode rootNode = tree.rootNode();

        assertEquals(1, treeQuery.patternCount());
        assertEquals(6, treeQuery.captureCount());
        assertEquals("tag.dep", treeQuery.captureName(0));

        List<TreeSitterQueryResult> captures = treeQuery.exec(rootNode, source);
        assertFalse(captures.isEmpty());

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        System.out.println("Elapsed: " + elapsedMs + " ms");

        TreeSitterQueryResult capture = captures.get(0);
        assertEquals("dependency.block", capture.name());
        assertEquals("element", capture.node().type());

        String pom = source.substring(
                capture.node().startByte(),
                capture.node().endByte());
        assertTrue(pom.contains("<groupId>org.springframework.boot</groupId>"));
    }

    private void showCaptures(List<TreeSitterQueryResult> captures) {
        AtomicInteger counter = new AtomicInteger();
        captures.forEach(c -> {
                    System.out.println(counter.getAndIncrement());
                    System.out.println(c.name());
                    System.out.println(source.substring(
                            c.node().startByte(),
                            c.node().endByte()));
                });
    }
}