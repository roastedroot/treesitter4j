package io.roastedroot.treesitter.ast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Path;

public class ASTJsonSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper PRETTY_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static String toJson(ASTTree tree) throws IOException {
        return MAPPER.writeValueAsString(tree);
    }

    public static String toJson(ASTTree tree, boolean pretty) throws IOException {
        if (pretty) {
            return PRETTY_MAPPER.writeValueAsString(tree);
        }
        return MAPPER.writeValueAsString(tree);
    }

    public static void toJson(ASTTree tree, Path file) throws IOException {
        PRETTY_MAPPER.writeValue(file.toFile(), tree);
    }

    public static ASTTree fromJson(String json) throws IOException {
        return MAPPER.readValue(json, ASTTree.class);
    }

    public static ASTTree fromJson(Path file) throws IOException {
        return MAPPER.readValue(file.toFile(), ASTTree.class);
    }
}
