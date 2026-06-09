package io.roastedroot.treesitter;

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
