package io.roastedroot.treesitter;

import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;

import java.nio.charset.StandardCharsets;

public final class TreeSitter implements AutoCloseable {

    private final WasiPreview1 wasi;
    private final TreeSitter_ModuleExports exports;

    private TreeSitter() {
        this.wasi = WasiPreview1.builder()
                .withOptions(WasiOptions.builder().build())
                .build();

        Instance instance = Instance.builder(TreeSitterModule.load())
                .withMachineFactory(TreeSitterModule::create)
                .withImportValues(
                        ImportValues.builder()
                                .addFunction(wasi.toHostFunctions())
                                .build())
                .build();

        this.exports = new TreeSitter_ModuleExports(instance);
    }

    public static TreeSitter create() {
        return new TreeSitter();
    }

    public TreeSitterParser newParser() {
        int handle = exports.parserNew();
        return new TreeSitterParser(handle, this);
    }

    void deleteParser(int handle) {
        exports.parserDelete(handle);
    }

    void setLanguage(int parserHandle, Language language) {
        int rc = exports.parserSetLanguage(parserHandle, language.id());
        if (rc < 0) {
            throw new TreeSitterException("Failed to set language: " + language);
        }
    }

    TreeSitterTree parseString(int parserHandle, String source) {
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        int ptr = exports.alloc(sourceBytes.length);
        try {
            exports.memory().write(ptr, sourceBytes);
            int treeHandle = exports.parserParseString(parserHandle, ptr, sourceBytes.length);
            if (treeHandle < 0) {
                throw new TreeSitterException("Failed to parse string");
            }
            return new TreeSitterTree(treeHandle, this);
        } finally {
            exports.dealloc(ptr, sourceBytes.length);
        }
    }

    TreeSitterNode rootNode(int treeHandle) {
        int nodeHandle = exports.treeRootNode(treeHandle);
        if (nodeHandle < 0) {
            throw new TreeSitterException("Failed to get root node");
        }
        return new TreeSitterNode(nodeHandle, this);
    }

    void deleteTree(int handle) {
        exports.treeDelete(handle);
    }

    // --- Node operations (package-private, called by TreeSitterNode) ---

    String nodeType(int nodeHandle) {
        int rc = exports.nodeType(nodeHandle);
        if (rc < 0) {
            throw new TreeSitterException("Failed to get node type");
        }
        return readResult();
    }

    int nodeChildCount(int nodeHandle) {
        return exports.nodeChildCount(nodeHandle);
    }

    int nodeNamedChildCount(int nodeHandle) {
        return exports.nodeNamedChildCount(nodeHandle);
    }

    TreeSitterNode nodeNamedChild(int nodeHandle, int index) {
        int childHandle = exports.nodeNamedChild(nodeHandle, index);
        if (childHandle < 0) {
            return null;
        }
        return new TreeSitterNode(childHandle, this);
    }

    TreeSitterNode nodeChild(int nodeHandle, int index) {
        int childHandle = exports.nodeChild(nodeHandle, index);
        if (childHandle < 0) {
            return null;
        }
        return new TreeSitterNode(childHandle, this);
    }

    String nodeString(int nodeHandle) {
        int rc = exports.nodeString(nodeHandle);
        if (rc < 0) {
            throw new TreeSitterException("Failed to get node s-expression");
        }
        return readResult();
    }

    int nodeStartByte(int nodeHandle) {
        return exports.nodeStartByte(nodeHandle);
    }

    int nodeEndByte(int nodeHandle) {
        return exports.nodeEndByte(nodeHandle);
    }

    boolean nodeIsNamed(int nodeHandle) {
        return exports.nodeIsNamed(nodeHandle) == 1;
    }

    private String readResult() {
        int ptr = exports.getResultPtr();
        int len = exports.getResultLen();
        return new String(exports.memory().readBytes(ptr, len));
    }

    @Override
    public void close() {
        if (wasi != null) {
            wasi.close();
        }
    }
}
