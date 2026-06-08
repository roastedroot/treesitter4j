package io.roastedroot.treesitter;

public enum Language {
    JSON(0),
    JAVA(1);

    private final int id;

    Language(int id) {
        this.id = id;
    }

    int id() {
        return id;
    }
}
