package io.roastedroot.treesitter;

public final class TreeSitterCapture {

    private final String name;
    private final TreeSitterNode node;

    TreeSitterCapture(String name, TreeSitterNode node) {
        this.name = name;
        this.node = node;
    }

    public String name() {
        return name;
    }

    public TreeSitterNode node() {
        return node;
    }
}