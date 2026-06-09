package io.roastedroot.treesitter;

import java.util.List;

public final class TreeSitterQuery implements AutoCloseable {

    private final int handle;
    private final TreeSitter ts;

    TreeSitterQuery(int handle, TreeSitter ts) {
        this.handle = handle;
        this.ts = ts;
    }

    public int patternCount() {
        return ts.queryPatternCount(handle);
    }

    public int captureCount() {
        return ts.queryCaptureCount(handle);
    }

    public String captureName(int index) {
        return ts.queryCaptureName(handle, index);
    }

    public List<TreeSitterQueryResult> exec(TreeSitterNode node, String source) {
        return ts.queryExec(handle, node.handle(), source);
    }

    @Override
    public void close() {
        ts.deleteQuery(handle);
    }
}