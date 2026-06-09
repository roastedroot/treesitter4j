package io.roastedroot.treesitter.ast;

import io.roastedroot.treesitter.Language;
import io.roastedroot.treesitter.TreeSitterNode;
import io.roastedroot.treesitter.TreeSitterTree;

import java.util.ArrayList;
import java.util.List;

public class ASTExporter {

    public static ASTTree export(TreeSitterTree tree, Language language, String sourceCode) {
        return export(tree, language, sourceCode, null);
    }

    public static ASTTree export(TreeSitterTree tree, Language language, String sourceCode, String sourceFile) {
        TreeSitterNode rootNode = tree.rootNode();
        ASTNode root = convertNode(rootNode, sourceCode);
        return new ASTTree(language.name(), sourceFile, sourceCode, root);
    }

    private static ASTNode convertNode(TreeSitterNode node, String sourceCode) {
        String type = node.type();
        int startByte = node.startByte();
        int endByte = node.endByte();
        boolean named = node.isNamed();

        int namedChildCount = node.namedChildCount();
        List<ASTNode> children = new ArrayList<>();

        for (int i = 0; i < namedChildCount; i++) {
            TreeSitterNode child = node.namedChild(i);
            if (child != null) {
                children.add(convertNode(child, sourceCode));
            }
        }

        // Store text only for leaf nodes (no named children)
        String text = null;
        if (namedChildCount == 0) {
            text = sourceCode.substring(startByte, endByte);
        }

        return new ASTNode(type, startByte, endByte, named, text, children);
    }
}
