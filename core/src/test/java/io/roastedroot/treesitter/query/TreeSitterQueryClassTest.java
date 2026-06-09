package io.roastedroot.treesitter.query;

import io.roastedroot.treesitter.Language;
import io.roastedroot.treesitter.TreeSitter;
import io.roastedroot.treesitter.TreeSitterNode;
import io.roastedroot.treesitter.TreeSitterQueryResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TreeSitterQueryClassTest {

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

                List<TreeSitterQueryResult> captures = query.exec(rootNode, source);
                assertFalse(captures.isEmpty());

                TreeSitterQueryResult capture = captures.get(0);
                assertEquals("class_name", capture.name());
                assertEquals("identifier", capture.node().type());

                String className = source.substring(
                        capture.node().startByte(),
                        capture.node().endByte());
                assertEquals("HelloWorldApp", className);
            }
        }
    }
}