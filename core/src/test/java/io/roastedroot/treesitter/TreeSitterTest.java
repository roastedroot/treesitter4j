package io.roastedroot.treesitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TreeSitterTest {

    @Test
    void jsonExample() {
        try (var ts = TreeSitter.create();
                var parser = ts.newParser()) {

            parser.setLanguage(Language.JSON);

            try (var tree = parser.parseString("[1, null]")) {
                TreeSitterNode rootNode = tree.rootNode();
                TreeSitterNode arrayNode = rootNode.namedChild(0);
                TreeSitterNode numberNode = arrayNode.namedChild(0);

                assertEquals("document", rootNode.type());
                assertEquals("array", arrayNode.type());
                assertEquals("number", numberNode.type());

                assertEquals(1, rootNode.childCount());
                assertEquals(5, arrayNode.childCount());
                assertEquals(2, arrayNode.namedChildCount());
                assertEquals(0, numberNode.childCount());

                String sexp = rootNode.toSexp();
                assertNotNull(sexp);
                assertTrue(sexp.contains("document"));
                assertTrue(sexp.contains("array"));
                assertTrue(sexp.contains("number"));
                assertTrue(sexp.contains("null"));

                System.out.println("Syntax tree: " + sexp);
            }
        }
    }

    @Test
    void javaExample() {
        try (var ts = TreeSitter.create();
                var parser = ts.newParser()) {

            parser.setLanguage(Language.JAVA);

            String code = "class Hello { void main() { System.out.println(\"Hello\"); } }";
            try (var tree = parser.parseString(code)) {
                TreeSitterNode rootNode = tree.rootNode();

                assertEquals("program", rootNode.type());
                assertTrue(rootNode.childCount() > 0);

                TreeSitterNode classDecl = rootNode.namedChild(0);
                assertEquals("class_declaration", classDecl.type());

                String sexp = rootNode.toSexp();
                assertNotNull(sexp);
                assertTrue(sexp.contains("class_declaration"));

                System.out.println("Java syntax tree: " + sexp);
            }
        }
    }
}
