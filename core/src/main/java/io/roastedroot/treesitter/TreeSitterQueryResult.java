package io.roastedroot.treesitter;

public final class TreeSitterQueryResult {

    private final String name;
    private final TreeSitterNode node;

    TreeSitterQueryResult(String name, TreeSitterNode node) {
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
