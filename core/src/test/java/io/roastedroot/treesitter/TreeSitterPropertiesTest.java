package io.roastedroot.treesitter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TreeSitterPropertiesTest {

    @Test
    void propertiesExample() {
        try (var ts = TreeSitter.create();
                var parser = ts.newParser()) {

            parser.setLanguage(Language.PROPERTIES);

            String properties = """
                    server.port=8080
                    """;

            try (var tree = parser.parseString(properties)) {
                TreeSitterNode rootNode = tree.rootNode();
                String sexp = rootNode.toSexp();
                assertNotNull(sexp);

                System.out.println("Syntax tree: " + sexp);
            }
        }
    }
}
