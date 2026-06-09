package io.roastedroot.treesitter.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.treesitter.Language;
import io.roastedroot.treesitter.TreeSitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ASTJsonRoundTripTest {

    private static final String JAVA_SOURCE =
            "class Hello { void main() { System.out.println(\"Hello\"); } }";

    private static final String JSON_SOURCE =
            "{\"name\": \"test\", \"value\": 42}";

    private static final String PROPERTIES_SOURCE =
            "app.name=MyApp\napp.version=1.0";

    private static final String XML_SOURCE =
            "<?xml version=\"1.0\"?><root><child attr=\"val\">text</child></root>";

    private static final String YAML_SOURCE =
            "name: test\nversion: 1.0\nitems:\n  - one\n  - two";

    private static final String HTML_SOURCE =
            "<html><head><title>Test</title></head><body><p>Hello</p></body></html>";


    @Test
    void roundTripJava() throws IOException {
        assertRoundTrip(Language.JAVA, JAVA_SOURCE, "program", "Hello.java");
    }

    @Test
    void roundTripJson() throws IOException {
        assertRoundTrip(Language.JSON, JSON_SOURCE, "document", "data.json");
    }

    @Test
    void roundTripProperties() throws IOException {
        assertRoundTrip(Language.PROPERTIES, PROPERTIES_SOURCE, "file", "app.properties");
    }

    @Test
    void roundTripXml() throws IOException {
        assertRoundTrip(Language.XML, XML_SOURCE, "document", "config.xml");
    }

    @Test
    void roundTripYaml() throws IOException {
        assertRoundTrip(Language.YAML, YAML_SOURCE, "stream", "config.yaml");
    }

    @Test
    void roundTripHtml() throws IOException {
        assertRoundTrip(Language.HTML, HTML_SOURCE, "document", "index.html");
    }

    @Test
    void fileBasedRoundTrip(@TempDir Path tempDir) throws IOException {
        try (var ts = TreeSitter.create();
                var parser = ts.newParser()) {
            parser.setLanguage(Language.JAVA);
            try (var tree = parser.parseString(JAVA_SOURCE)) {
                ASTTree exported = ASTExporter.export(tree, Language.JAVA, JAVA_SOURCE, "Hello.java");

                Path jsonFile = tempDir.resolve("ast.json");
                ASTJsonSerializer.toJson(exported, jsonFile);

                assertTrue(Files.exists(jsonFile));
                assertTrue(Files.size(jsonFile) > 0);

                ASTTree imported = ASTJsonSerializer.fromJson(jsonFile);
                assertTreesEqual(exported, imported);
            }
        }
    }

    @Test
    void prettyPrintContainsNewlines() throws IOException {
        try (var ts = TreeSitter.create();
                var parser = ts.newParser()) {
            parser.setLanguage(Language.JAVA);
            try (var tree = parser.parseString(JAVA_SOURCE)) {
                ASTTree exported = ASTExporter.export(tree, Language.JAVA, JAVA_SOURCE);

                String compact = ASTJsonSerializer.toJson(exported);
                String pretty = ASTJsonSerializer.toJson(exported, true);

                assertFalse(compact.contains("\n"));
                assertTrue(pretty.contains("\n"));

                // Both should deserialize to the same thing
                ASTTree fromCompact = ASTJsonSerializer.fromJson(compact);
                ASTTree fromPretty = ASTJsonSerializer.fromJson(pretty);
                assertTreesEqual(fromCompact, fromPretty);
            }
        }
    }

    @Test
    void exportWithoutSourceFile() throws IOException {
        try (var ts = TreeSitter.create();
                var parser = ts.newParser()) {
            parser.setLanguage(Language.JAVA);
            try (var tree = parser.parseString(JAVA_SOURCE)) {
                ASTTree exported = ASTExporter.export(tree, Language.JAVA, JAVA_SOURCE);
                assertNull(exported.getSourceFile());

                String json = ASTJsonSerializer.toJson(exported);
                ASTTree imported = ASTJsonSerializer.fromJson(json);
                assertNull(imported.getSourceFile());
                assertTreesEqual(exported, imported);
            }
        }
    }

    private void assertRoundTrip(Language language, String sourceCode,
            String expectedRootType, String sourceFile) throws IOException {
        try (var ts = TreeSitter.create();
                var parser = ts.newParser()) {
            parser.setLanguage(language);
            try (var tree = parser.parseString(sourceCode)) {
                ASTTree exported = ASTExporter.export(tree, language, sourceCode, sourceFile);

                // Verify export
                assertEquals(language.name(), exported.getLanguage());
                assertEquals(sourceFile, exported.getSourceFile());
                assertEquals(sourceCode, exported.getSourceCode());
                assertNotNull(exported.getRoot());
                assertEquals(expectedRootType, exported.getRoot().getType());
                assertTrue(exported.getRoot().isNamed());

                // Serialize and deserialize
                String json = ASTJsonSerializer.toJson(exported, true);
                assertNotNull(json);
                assertFalse(json.isEmpty());

                ASTTree imported = ASTJsonSerializer.fromJson(json);
                assertTreesEqual(exported, imported);
            }
        }
    }

    private void assertTreesEqual(ASTTree expected, ASTTree actual) {
        assertEquals(expected.getLanguage(), actual.getLanguage());
        assertEquals(expected.getSourceFile(), actual.getSourceFile());
        assertEquals(expected.getSourceCode(), actual.getSourceCode());
        assertNodesEqual(expected.getRoot(), actual.getRoot());
    }

    private void assertNodesEqual(ASTNode expected, ASTNode actual) {
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getStartByte(), actual.getStartByte());
        assertEquals(expected.getEndByte(), actual.getEndByte());
        assertEquals(expected.isNamed(), actual.isNamed());
        assertEquals(expected.getText(), actual.getText());
        assertEquals(expected.getChildren().size(), actual.getChildren().size());

        for (int i = 0; i < expected.getChildren().size(); i++) {
            assertNodesEqual(expected.getChildren().get(i), actual.getChildren().get(i));
        }
    }
}
