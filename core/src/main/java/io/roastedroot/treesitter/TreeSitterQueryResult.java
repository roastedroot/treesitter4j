package io.roastedroot.treesitter;

public final class TreeSitterQueryResult {

    private final String name;
    private final TreeSitterNode node;
    private final String source;

    TreeSitterQueryResult(String source, String name, TreeSitterNode node) {
        this.source = source;
        this.name = name;
        this.node = node;
    }

    public String source() {
        return source;
    }

    public String name() {
        return name;
    }

    public TreeSitterNode node() {
        return node;
    }
}
