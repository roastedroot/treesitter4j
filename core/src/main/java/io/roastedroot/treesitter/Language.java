package io.roastedroot.treesitter;

/**
 * Tree sitter grammar/language available
 * The value id of the language must match the value defined within the wasm-build/src/rs.lib file !
 */
public enum Language {
    JSON(0),
    JAVA(1),
    PROPERTIES(2),
    HTML(3),
    XML(4),
    MARKDOWN(5),
    YAML(6);

    private final int id;

    Language(int id) {
        this.id = id;
    }

    int id() {
        return id;
    }
}
