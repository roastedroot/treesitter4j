package io.roastedroot.treesitter;

public final class TreeSitterParser implements AutoCloseable {

    private final int handle;
    private final TreeSitter ts;

    TreeSitterParser(int handle, TreeSitter ts) {
        this.handle = handle;
        this.ts = ts;
    }

    public void setLanguage(Language language) {
        ts.setLanguage(handle, language);
    }

    public TreeSitterTree parseString(String source) {
        return ts.parseString(handle, source);
    }

    @Override
    public void close() {
        ts.deleteParser(handle);
    }
}
