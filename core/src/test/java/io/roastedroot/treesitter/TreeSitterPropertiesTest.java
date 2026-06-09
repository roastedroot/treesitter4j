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
                assertEquals("file",rootNode.type());

                var propertyNode = rootNode.child(0);
                assertEquals("property",propertyNode.type());
                assertEquals(3,propertyNode.childCount());

                var key = propertyNode.child(0);
                assertEquals("key",key.type());
                assertEquals("server.port", properties.substring(key.startByte(), key.endByte()));

                var separator = propertyNode.child(1);
                assertEquals("=",separator.type());

                var value = propertyNode.child(2);
                assertEquals("value",value.type());
                assertEquals("8080", properties.substring(value.startByte(), value.endByte()));
            }
        }
    }
}
