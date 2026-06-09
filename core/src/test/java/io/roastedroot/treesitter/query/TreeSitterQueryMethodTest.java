package io.roastedroot.treesitter.query;

import io.roastedroot.treesitter.Language;
import io.roastedroot.treesitter.TreeSitter;
import io.roastedroot.treesitter.TreeSitterQueryResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TreeSitterQueryMethodTest {

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

                List<TreeSitterQueryResult> captures = query.exec(tree.rootNode(), source);
                assertEquals(2, captures.size());

                assertEquals("method_name", captures.get(0).name());
                assertEquals("method_name", captures.get(1).name());
            }
        }
    }

    @Test
    void queryMethodVisibility() {
        String source = """
                class MyService {
                  public void doWork() {}
                  private int compute() { return 42; }
                  void packageMethod() {}
                }
                """;

        try (var ts = TreeSitter.create();
             var parser = ts.newParser()) {

            parser.setLanguage(Language.JAVA);

            try (var tree = parser.parseString(source);
                 var query = ts.newQuery(Language.JAVA,
                         "(method_declaration (modifiers) @modifiers name: (identifier) @method_name)")) {

                List<TreeSitterQueryResult> captures = query.exec(tree.rootNode(), source);

                // The query only matches methods that have modifiers,
                // so packageMethod() (no modifier) is excluded.
                // Each match produces 2 captures: @modifiers and @method_name
                assertEquals(4, captures.size());

                // First method: public void doWork()
                String modifiers1 = source.substring(
                        captures.get(0).node().startByte(),
                        captures.get(0).node().endByte());
                String methodName1 = source.substring(
                        captures.get(1).node().startByte(),
                        captures.get(1).node().endByte());
                assertEquals("modifiers", captures.get(0).name());
                assertEquals("method_name", captures.get(1).name());
                assertEquals("public", modifiers1);
                assertEquals("doWork", methodName1);

                // Second method: private int compute()
                String modifiers2 = source.substring(
                        captures.get(2).node().startByte(),
                        captures.get(2).node().endByte());
                String methodName2 = source.substring(
                        captures.get(3).node().startByte(),
                        captures.get(3).node().endByte());
                assertEquals("modifiers", captures.get(2).name());
                assertEquals("method_name", captures.get(3).name());
                assertEquals("private", modifiers2);
                assertEquals("compute", methodName2);
            }
        }
    }
}