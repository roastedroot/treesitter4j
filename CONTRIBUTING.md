# Contributing

## Adding a New Language Grammar

Adding a new tree-sitter grammar requires changes in three files:

### 1. Add the crate dependency

In `wasm-build/Cargo.toml`, add the grammar crate:

```toml
[dependencies]
tree-sitter-foo = "x.y.z"
```

### 2. Register the language in Rust

In `wasm-build/src/lib.rs`, add an entry to the `define_languages!` macro.
The id must be the next sequential integer:

```rust
define_languages! {
    // ... existing entries ...
    7 => tree_sitter_foo::LANGUAGE,
}
```

> Some grammars export the language under a different name (e.g. `tree_sitter_xml::LANGUAGE_XML`).
> Check the crate's documentation for the correct symbol.

### 3. Add the Java enum variant

In `core/src/main/java/io/roastedroot/treesitter/Language.java`, add a variant with the matching id:

```java
public enum Language {
    // ... existing entries ...
    FOO(7);
}
```

### 4. Rebuild and test

```sh
cd wasm-build
make local        # builds, optimizes, and copies the wasm module
cd ..
mvn install       # regenerates Chicory classes and runs tests
```
