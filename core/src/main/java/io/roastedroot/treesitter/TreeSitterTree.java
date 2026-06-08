package io.roastedroot.treesitter;

public final class TreeSitterTree implements AutoCloseable {

    private final int handle;
    private final TreeSitter ts;

    TreeSitterTree(int handle, TreeSitter ts) {
        this.handle = handle;
        this.ts = ts;
    }

    public TreeSitterNode rootNode() {
        return ts.rootNode(handle);
    }

    @Override
    public void close() {
        ts.deleteTree(handle);
    }
}
