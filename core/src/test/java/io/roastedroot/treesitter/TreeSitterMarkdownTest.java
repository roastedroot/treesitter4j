package io.roastedroot.treesitter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TreeSitterMarkdownTest {

    @Test
    void setLanguageMarkdown() {
        try (var ts = TreeSitter.create();
                var parser = ts.newParser()) {

            assertDoesNotThrow(() -> parser.setLanguage(Language.MARKDOWN),
                    "Setting MARKDOWN language should not throw");
        }
    }

    @Test
    void parseHeading() {
        try (var ts = TreeSitter.create();
                var parser = ts.newParser()) {

            parser.setLanguage(Language.MARKDOWN);

            String markdown = """
                    # Hello World
                    """;

            try (var tree = parser.parseString(markdown)) {
                assertNotNull(tree);
                TreeSitterNode rootNode = tree.rootNode();
                assertNotNull(rootNode);
                assertEquals("document", rootNode.type());

                // document > section > atx_heading
                var section = rootNode.namedChild(0);
                assertNotNull(section, "Expected a section node");
                assertEquals("section", section.type());

                var heading = section.namedChild(0);
                assertNotNull(heading, "Expected an atx_heading node");
                assertEquals("atx_heading", heading.type());

                String sexp = rootNode.toSexp();
                assertNotNull(sexp);
                assertTrue(sexp.contains("atx_heading"));

                System.out.println("Markdown syntax tree: " + sexp);
            }
        }
    }

    @Test
    void parseParagraph() {
        try (var ts = TreeSitter.create();
                var parser = ts.newParser()) {

            parser.setLanguage(Language.MARKDOWN);

            String markdown = """
                    This is a paragraph.
                    """;

            try (var tree = parser.parseString(markdown)) {
                TreeSitterNode rootNode = tree.rootNode();
                assertEquals("document", rootNode.type());

                var section = rootNode.namedChild(0);
                assertNotNull(section);
                assertEquals("section", section.type());

                var paragraph = section.namedChild(0);
                assertNotNull(paragraph, "Expected a paragraph node");
                assertEquals("paragraph", paragraph.type());
            }
        }
    }

    @Test
    void parseMultipleElements() {
        try (var ts = TreeSitter.create();
                var parser = ts.newParser()) {

            parser.setLanguage(Language.MARKDOWN);

            String markdown = """
                    # Title

                    Some text here.

                    ## Subtitle

                    - item one
                    - item two
                    """;

            try (var tree = parser.parseString(markdown)) {
                TreeSitterNode rootNode = tree.rootNode();
                assertEquals("document", rootNode.type());
                assertTrue(rootNode.namedChildCount() > 0);

                String sexp = rootNode.toSexp();
                assertTrue(sexp.contains("atx_heading"), "Should contain headings");
                assertTrue(sexp.contains("list"), "Should contain a list");
                assertTrue(sexp.contains("list_item"), "Should contain list items");

                System.out.println("Markdown multi-element tree: " + sexp);
            }
        }
    }

    @Test
    void parseCodeBlock() {
        try (var ts = TreeSitter.create();
                var parser = ts.newParser()) {

            parser.setLanguage(Language.MARKDOWN);

            String markdown = """
                    ```java
                    class Foo {}
                    ```
                    """;

            try (var tree = parser.parseString(markdown)) {
                TreeSitterNode rootNode = tree.rootNode();
                assertEquals("document", rootNode.type());

                String sexp = rootNode.toSexp();
                assertTrue(sexp.contains("fenced_code_block"), "Should contain a fenced code block");
                assertTrue(sexp.contains("info_string"), "Should contain language info string");

                System.out.println("Markdown code block tree: " + sexp);
            }
        }
    }
}