# treesitter4j

tree-sitter API for Java using WebAssembly.

This library brings [tree-sitter](https://tree-sitter.github.io/tree-sitter/) parsing capabilities to Java by compiling tree-sitter and its language grammars to [WebAssembly](https://webassembly.org/) and running them through [Endive](https://github.com/bytecodealliance/endive), a pure-Java WASM runtime. No JNI or native binaries required.

## Modules

treesitter4j is a Maven multi-modules project:

### `core`

The Java module that provides the public API. It contains:

- `TreeSitter` -- factory for creating parser instances (manages the WASM module lifecycle via Endive)
- `TreeSitterParser` -- wraps a tree-sitter parser; set a language, parse a string, get a tree
- `TreeSitterTree` / `TreeSitterNode` -- navigate the resulting AST (node types, children, S-expressions, byte ranges, ...)
- `TreeSitterPool` -- thread-safe pool of `TreeSitter` instances for concurrent parsing
- `Language` -- enum of available grammars (see below)

### `wasm-build`

The Rust/WebAssembly module. It compiles tree-sitter core and the grammar crates listed in `Cargo.toml` into a single `tree-sitter.wasm` binary. The Makefile handles downloading WASI SDK and Rust, building, and optimizing the WASM output.

## Supported languages

The following tree-sitter grammars are compiled into the WASM module and exposed through the `Language` enum:

| Language   | Crate                    | Version | Repository                                                                            |
|------------|--------------------------|---------|---------------------------------------------------------------------------------------|
| core       | `tree-sitter`            | 0.26.9  | [tree-sitter](https://docs.rs/tree-sitter/0.26.9/tree_sitter/                   |
| JSON       | `tree-sitter-json`       | 0.24.8  | [tree-sitter-json](https://github.com/tree-sitter/tree-sitter-json)                   |
| Java       | `tree-sitter-java`       | 0.23.5  | [tree-sitter-java](https://github.com/tree-sitter/tree-sitter-java)                   |
| Properties | `tree-sitter-properties` | 0.3.0   | [tree-sitter-properties](https://github.com/tree-sitter-grammars/tree-sitter-properties) |
| HTML       | `tree-sitter-html`       | 0.23.2  | [tree-sitter-html](https://github.com/tree-sitter/tree-sitter-html)                   |
| XML        | `tree-sitter-xml`        | 0.7.0   | [tree-sitter-xml](https://github.com/tree-sitter-grammars/tree-sitter-xml)            |
| Markdown   | `tree-sitter-md`         | 0.5.3   | [tree-sitter-md](https://github.com/tree-sitter-grammars/tree-sitter-markdown)        |
| YAML       | `tree-sitter-yaml`       | 0.7.2   | [tree-sitter-yaml](https://github.com/tree-sitter-grammars/tree-sitter-yaml)          |

To add a new language, add the grammar crate to `wasm-build/Cargo.toml`, register it in `wasm-build/src/lib.rs` with a new ID, and add the corresponding entry to the `Language` enum in `core`.

## Usage

```java
try (TreeSitter ts = TreeSitter.create();
     TreeSitterParser parser = ts.newParser()) {

    parser.setLanguage(Language.JAVA);

    try (TreeSitterTree tree = parser.parseString("class Foo {}")) {
        TreeSitterNode root = tree.rootNode();
        System.out.println(root.toSexp());
    }
}
```

## Thread-safe pooling

Each `TreeSitter` instance owns an isolated WASM module with its own linear memory, so instances must not be shared across threads. `TreeSitterPool` caps the number of **concurrently borrowed** instances and reuses them across parse operations. The pool size limits parallelism, not languages — each borrowed instance can parse any language, so a pool of 4 can serve all supported grammars; `borrow()` simply blocks when all instances are already in use. It uses only lock-free data structures and `Semaphore` internally (no `synchronized` blocks), so it is safe to use with virtual threads.

```java
try (TreeSitterPool pool = TreeSitterPool.create(4)) {
    try (TreeSitterPool.Loan loan = pool.borrow()) {
        try (TreeSitterParser parser = loan.instance().newParser(Language.JAVA);
             TreeSitterTree tree = parser.parseString(source)) {
            System.out.println(tree.rootNode().toSexp());
        }
    }
}
```

If an error leaves the instance in a bad state, call `loan.discard()` before closing the loan to destroy it rather than returning it to the pool.

## Building

### Prerequisites

- Java 17+
- Maven 3.9+
- Rust toolchain with the `wasm32-wasip1` target (only needed to rebuild the WASM binary)

### Build treesitter4j

If `wasm-build/wasm/tree-sitter.wasm` is already present (checked into the repo), you only need to run this command:

```bash
mvn clean install
```

### Rebuild the WASM binary

If it is needed to rebuild the wasm binary file for whatever reason like to add a new grammar/language, then you will have to perform the following steps:
- Add the new grammar to the Cargo.toml file under the section `[dependencies]`, 
- Include the new language id and method to call under the rust file `wasm-build/src/lib.rs`. see section `// --- Language helpers ---`,
- Update the README.md file to add the new language `## Supported languages` 

Then, execute these commands
```bash
cd wasm-build
make all
```
Additionally, include to this new language part of the Enum `io.roastedroot.treesitter.Language` and rebuild the java core module
```java
public enum Language {
    JSON(0),
    JAVA(1),
    PROPERTIES(2),
    HTML(3),
    XML(4),
    MARKDOWN(5),
    YAML(6);
    // NEWLANGUAGE(7);
```

**NOTE**: The Makefile includes the instructions needed to install localy: wasi-sdk and Binaryen !