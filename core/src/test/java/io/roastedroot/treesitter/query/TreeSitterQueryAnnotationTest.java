package io.roastedroot.treesitter.query;

import io.roastedroot.treesitter.Language;
import io.roastedroot.treesitter.TreeSitter;
import io.roastedroot.treesitter.TreeSitterQueryResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TreeSitterQueryAnnotationTest {

    static final String SOURCE = """
            package org.acme.rest;

            import jakarta.ws.rs.GET;
            import jakarta.ws.rs.Path;

            @Path("")
            public class Endpoint {

                @GET
                public String hello() {
                    return "Hello, World!";
                }
            }
            """;

    @Test
    void queryAnnotationByName() {
        try (var ts = TreeSitter.create();
             var parser = ts.newParser()) {

            parser.setLanguage(Language.JAVA);

            try (var tree = parser.parseString(SOURCE);
                 var query = ts.newQuery(Language.JAVA, """
                         (marker_annotation name: (identifier) @annotation_name)
                         (annotation name: (identifier) @annotation_name)
                         """)) {

                List<TreeSitterQueryResult> results = query.exec(tree.rootNode(), SOURCE);
                assertEquals(2, results.size());

                String annotation1 = SOURCE.substring(
                        results.get(0).node().startByte(),
                        results.get(0).node().endByte());
                String annotation2 = SOURCE.substring(
                        results.get(1).node().startByte(),
                        results.get(1).node().endByte());

                assertEquals("Path", annotation1);
                assertEquals("GET", annotation2);
            }
        }
    }

    @Test
    void queryAnnotationByFullyQualifiedName() {

        try (var ts = TreeSitter.create();
             var parser = ts.newParser()) {

            parser.setLanguage(Language.JAVA);
            var tree = parser.parseString(SOURCE);
            var rootNode = tree.rootNode();

            // Step 1: Build a map of simple name -> FQN from import declarations
            // Tree-sitter parses "import jakarta.ws.rs.GET;" as an import_declaration
            // containing a scoped_identifier node
            Map<String, String> importMap = new HashMap<>();
            try (var importQuery = ts.newQuery(Language.JAVA,
                    "(import_declaration (scoped_identifier) @import_path)")) {

                List<TreeSitterQueryResult> importResults = importQuery.exec(rootNode, SOURCE);
                for (TreeSitterQueryResult result : importResults) {
                    String fqn = SOURCE.substring(
                            result.node().startByte(),
                            result.node().endByte());
                    String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
                    importMap.put(simpleName, fqn);
                }
            }

            assertEquals("jakarta.ws.rs.Path", importMap.get("Path"));
            assertEquals("jakarta.ws.rs.GET", importMap.get("GET"));

            // Step 2: Query annotations by simple name and resolve via import map
            try (var annotationQuery = ts.newQuery(Language.JAVA, """
                    (marker_annotation name: (identifier) @annotation_name)
                    (annotation name: (identifier) @annotation_name)
                    """)) {

                List<TreeSitterQueryResult> results = annotationQuery.exec(rootNode, SOURCE);
                assertEquals(2, results.size());

                String name1 = SOURCE.substring(
                        results.get(0).node().startByte(),
                        results.get(0).node().endByte());
                String name2 = SOURCE.substring(
                        results.get(1).node().startByte(),
                        results.get(1).node().endByte());

                assertEquals("jakarta.ws.rs.Path", importMap.get(name1));
                assertEquals("jakarta.ws.rs.GET", importMap.get(name2));
            }

            tree.close();
        }
    }

    @Test
    void queryAnnotationByAtPrefixedName() {
        try (var ts = TreeSitter.create();
             var parser = ts.newParser()) {

            parser.setLanguage(Language.JAVA);

            try (var tree = parser.parseString(SOURCE);
                 var query = ts.newQuery(Language.JAVA, """
                         (marker_annotation) @annotation
                         (annotation) @annotation
                         """)) {

                List<TreeSitterQueryResult> results = query.exec(tree.rootNode(), SOURCE);
                assertEquals(2, results.size());

                // Capturing the full annotation node includes the @ symbol
                String fullAnnotation1 = SOURCE.substring(
                        results.get(0).node().startByte(),
                        results.get(0).node().endByte());
                String fullAnnotation2 = SOURCE.substring(
                        results.get(1).node().startByte(),
                        results.get(1).node().endByte());

                assertEquals("@Path(\"\")", fullAnnotation1);
                assertEquals("@GET", fullAnnotation2);

                // Verify lookup by @-prefixed name
                assertTrue(fullAnnotation1.startsWith("@Path"));
                assertTrue(fullAnnotation2.startsWith("@GET"));
            }
        }
    }
}
