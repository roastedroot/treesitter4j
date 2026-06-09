package io.roastedroot.treesitter.ast;

public class ASTTree {

    private String language;
    private String sourceFile;
    private String sourceCode;
    private ASTNode root;

    public ASTTree() {
    }

    public ASTTree(String language, String sourceFile, String sourceCode, ASTNode root) {
        this.language = language;
        this.sourceFile = sourceFile;
        this.sourceCode = sourceCode;
        this.root = root;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public ASTNode getRoot() {
        return root;
    }

    public void setRoot(ASTNode root) {
        this.root = root;
    }
}
