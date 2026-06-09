package io.roastedroot.treesitter.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ASTNode {

    private String type;
    private int startByte;
    private int endByte;
    private boolean named;
    private String text;
    private List<ASTNode> children;

    public ASTNode() {
    }

    public ASTNode(String type, int startByte, int endByte, boolean named, String text, List<ASTNode> children) {
        this.type = type;
        this.startByte = startByte;
        this.endByte = endByte;
        this.named = named;
        this.text = text;
        this.children = children != null ? new ArrayList<>(children) : new ArrayList<>();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getStartByte() {
        return startByte;
    }

    public void setStartByte(int startByte) {
        this.startByte = startByte;
    }

    public int getEndByte() {
        return endByte;
    }

    public void setEndByte(int endByte) {
        this.endByte = endByte;
    }

    public boolean isNamed() {
        return named;
    }

    public void setNamed(boolean named) {
        this.named = named;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<ASTNode> getChildren() {
        return children != null ? children : Collections.emptyList();
    }

    public void setChildren(List<ASTNode> children) {
        this.children = children;
    }
}
