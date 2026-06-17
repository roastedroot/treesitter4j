package io.roastedroot.treesitter;

import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.wasi.WasiOptions;
import run.endive.wasi.WasiPreview1;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TreeSitter implements AutoCloseable {

    private final TreeSitter_ModuleExports exports;

    private TreeSitter() {
        Instance instance = Instance.builder(TreeSitterModule.load())
                .withMachineFactory(TreeSitterModule::create)
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

    public TreeSitterParser newParser(Language language) {
        TreeSitterParser parser = newParser();
        parser.setLanguage(language);
        return parser;
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

    public TreeSitterQuery newQuery(Language language, String pattern) {
        byte[] patternBytes = pattern.getBytes(StandardCharsets.UTF_8);
        int ptr = exports.alloc(patternBytes.length);
        try {
            exports.memory().write(ptr, patternBytes);
            int handle = exports.queryNew(language.id(), ptr, patternBytes.length);
            if (handle < 0) {
                throw new TreeSitterException("Failed to create query for language: " + language);
            }
            return new TreeSitterQuery(handle, this);
        } finally {
            exports.dealloc(ptr, patternBytes.length);
        }
    }

    void deleteQuery(int handle) {
        exports.queryDelete(handle);
    }

    int queryPatternCount(int handle) {
        return exports.queryPatternCount(handle);
    }

    int queryCaptureCount(int handle) {
        return exports.queryCaptureCount(handle);
    }

    String queryCaptureName(int queryHandle, int nameIndex) {
        int rc = exports.queryCaptureName(queryHandle, nameIndex);
        if (rc < 0) {
            throw new TreeSitterException("Failed to get capture name at index: " + nameIndex);
        }
        return readResult();
    }

    List<TreeSitterQueryResult> queryExec(int queryHandle, int nodeHandle, String source) {
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        int sourcePtr = exports.alloc(sourceBytes.length);
        try {
            exports.memory().write(sourcePtr, sourceBytes);
            int cursorHandle = exports.queryCursorNew();
            try {
                int captureCount = exports.queryCursorExec(
                        cursorHandle, queryHandle, nodeHandle, sourcePtr, sourceBytes.length);
                if (captureCount < 0) {
                    throw new TreeSitterException("Failed to execute query");
                }
                List<TreeSitterQueryResult> captures = new ArrayList<>(captureCount);
                for (int i = 0; i < captureCount; i++) {
                    int captureNodeHandle = exports.queryCursorCaptureNode(cursorHandle, i);
                    int captureNameId = exports.queryCursorCaptureNameId(cursorHandle, i);

                    TreeSitterNode captureNode = new TreeSitterNode(captureNodeHandle, this);

                    int rc = exports.queryCaptureName(queryHandle, captureNameId);
                    String captureName = (rc == 0) ? readResult() : "unknown";

                    captures.add(new TreeSitterQueryResult(source, captureName, captureNode));
                }
                return captures;
            } finally {
                exports.queryCursorDelete(cursorHandle);
            }
        } finally {
            exports.dealloc(sourcePtr, sourceBytes.length);
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

    int nodeStartRow(int nodeHandle) {
        return exports.nodeStartRow(nodeHandle);
    }

    int nodeStartColumn(int nodeHandle) {
        return exports.nodeStartColumn(nodeHandle);
    }

    int nodeEndRow(int nodeHandle) {
        return exports.nodeEndRow(nodeHandle);
    }

    int nodeEndColumn(int nodeHandle) {
        return exports.nodeEndColumn(nodeHandle);
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
    }
}
