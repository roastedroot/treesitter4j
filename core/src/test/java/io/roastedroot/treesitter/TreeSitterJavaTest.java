package io.roastedroot.treesitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TreeSitterJavaTest {

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

    @Test
    void convenienceParser() {
        String code = "class Foo {\n  int x;\n}";
        try (var ts = TreeSitter.create();
             var parser = ts.newParser(Language.JAVA);
             var tree = parser.parseString(code)) {

            TreeSitterNode root = tree.rootNode();
            assertEquals("program", root.type());

            TreeSitterNode classDecl = root.namedChild(0);
            TreeSitterNode className = classDecl.child(1);
            assertEquals("Foo", className.text(code));

            assertEquals(0, classDecl.startRow());
            assertEquals(0, classDecl.startColumn());
            assertEquals(2, classDecl.endRow());
            assertEquals(1, classDecl.endColumn());
        }
    }
}
