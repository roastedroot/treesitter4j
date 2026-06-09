package io.roastedroot.treesitter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TreeSitterQueryTest {

    @Test
    void queryClassDeclaration() {
        String source = """
                class HelloWorldApp {
                  public static void main(String[] args) {
                    System.out.println("Hello World!");
                  }
                }
                """;

        try (var ts = TreeSitter.create();
             var parser = ts.newParser()) {

            parser.setLanguage(Language.JAVA);

            try (var tree = parser.parseString(source);
                 var query = ts.newQuery(Language.JAVA,
                         "(class_declaration name: (identifier) @class_name)")) {

                TreeSitterNode rootNode = tree.rootNode();

                assertEquals(1, query.patternCount());
                assertEquals(1, query.captureCount());
                assertEquals("class_name", query.captureName(0));

                List<TreeSitterCapture> captures = query.exec(rootNode, source);
                assertFalse(captures.isEmpty());

                TreeSitterCapture capture = captures.get(0);
                assertEquals("class_name", capture.name());
                assertEquals("identifier", capture.node().type());
            }
        }
    }

    @Test
    void queryMethodDeclarations() {
        String source = """
                class Foo {
                  void bar() {}
                  int baz() { return 0; }
                }
                """;

        try (var ts = TreeSitter.create();
             var parser = ts.newParser()) {

            parser.setLanguage(Language.JAVA);

            try (var tree = parser.parseString(source);
                 var query = ts.newQuery(Language.JAVA,
                         "(method_declaration name: (identifier) @method_name)")) {

                List<TreeSitterCapture> captures = query.exec(tree.rootNode(), source);
                assertEquals(2, captures.size());

                assertEquals("method_name", captures.get(0).name());
                assertEquals("method_name", captures.get(1).name());
            }
        }
    }
}