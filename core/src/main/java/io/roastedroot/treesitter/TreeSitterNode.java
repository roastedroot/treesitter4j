package io.roastedroot.treesitter;

public final class TreeSitterNode {

    private final int handle;
    private final TreeSitter ts;

    TreeSitterNode(int handle, TreeSitter ts) {
        this.handle = handle;
        this.ts = ts;
    }

    public String type() {
        return ts.nodeType(handle);
    }

    public int childCount() {
        return ts.nodeChildCount(handle);
    }

    public int namedChildCount() {
        return ts.nodeNamedChildCount(handle);
    }

    public TreeSitterNode namedChild(int index) {
        return ts.nodeNamedChild(handle, index);
    }

    public TreeSitterNode child(int index) {
        return ts.nodeChild(handle, index);
    }

    public String toSexp() {
        return ts.nodeString(handle);
    }

    public int startByte() {
        return ts.nodeStartByte(handle);
    }

    public int endByte() {
        return ts.nodeEndByte(handle);
    }

    public boolean isNamed() {
        return ts.nodeIsNamed(handle);
    }

    int handle() {
        return handle;
    }
}
