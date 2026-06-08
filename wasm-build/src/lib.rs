use std::mem;
use std::slice;
use tree_sitter::{Language, Node, Parser, Tree};

// --- Global state (wasm is single-threaded) ---

static mut PARSERS: Vec<Option<Parser>> = Vec::new();
static mut TREES: Vec<Option<Tree>> = Vec::new();
// Nodes with lifetime erased — safe as long as the owning Tree is alive
static mut NODES: Vec<Option<Node<'static>>> = Vec::new();
// Last string result buffer
static mut RESULT_BUF: Vec<u8> = Vec::new();

// --- Memory exports for the host ---

#[no_mangle]
pub extern "C" fn alloc(size: i32) -> i32 {
    let layout = std::alloc::Layout::from_size_align(size as usize, 1).unwrap();
    unsafe { std::alloc::alloc(layout) as i32 }
}

#[no_mangle]
pub extern "C" fn dealloc(ptr: i32, size: i32) {
    let layout = std::alloc::Layout::from_size_align(size as usize, 1).unwrap();
    unsafe { std::alloc::dealloc(ptr as *mut u8, layout) }
}

// --- Result buffer ---

fn set_result(s: &[u8]) {
    unsafe {
        RESULT_BUF = s.to_vec();
    }
}

#[no_mangle]
pub extern "C" fn get_result_ptr() -> i32 {
    unsafe { RESULT_BUF.as_ptr() as i32 }
}

#[no_mangle]
pub extern "C" fn get_result_len() -> i32 {
    unsafe { RESULT_BUF.len() as i32 }
}

// --- Language helpers ---

const LANG_JSON: i32 = 0;
const LANG_JAVA: i32 = 1;

fn get_language(lang_id: i32) -> Option<Language> {
    match lang_id {
        LANG_JSON => Some(tree_sitter_json::LANGUAGE.into()),
        LANG_JAVA => Some(tree_sitter_java::LANGUAGE.into()),
        _ => None,
    }
}

// --- Parser API ---

#[no_mangle]
pub extern "C" fn parser_new() -> i32 {
    let parser = Parser::new();
    unsafe {
        let handle = PARSERS.len() as i32;
        PARSERS.push(Some(parser));
        handle
    }
}

#[no_mangle]
pub extern "C" fn parser_delete(handle: i32) {
    unsafe {
        if let Some(slot) = PARSERS.get_mut(handle as usize) {
            *slot = None;
        }
    }
}

/// Set the language for a parser. Returns 0 on success, -1 on error.
#[no_mangle]
pub extern "C" fn parser_set_language(parser_handle: i32, lang_id: i32) -> i32 {
    unsafe {
        let parser = match PARSERS.get_mut(parser_handle as usize) {
            Some(Some(p)) => p,
            _ => return -1,
        };
        let lang = match get_language(lang_id) {
            Some(l) => l,
            None => return -1,
        };
        match parser.set_language(&lang) {
            Ok(()) => 0,
            Err(_) => -1,
        }
    }
}

// --- Parse API ---

/// Parse a string. Returns tree handle, or -1 on error.
#[no_mangle]
pub extern "C" fn parser_parse_string(parser_handle: i32, source_ptr: i32, source_len: i32) -> i32 {
    unsafe {
        let parser = match PARSERS.get_mut(parser_handle as usize) {
            Some(Some(p)) => p,
            _ => return -1,
        };
        let source = slice::from_raw_parts(source_ptr as *const u8, source_len as usize);
        let source_str = match std::str::from_utf8(source) {
            Ok(s) => s,
            Err(_) => return -1,
        };
        match parser.parse(source_str, None) {
            Some(tree) => {
                let handle = TREES.len() as i32;
                TREES.push(Some(tree));
                handle
            }
            None => -1,
        }
    }
}

// --- Tree API ---

/// Get the root node of a tree. Returns node handle, or -1 on error.
#[no_mangle]
pub extern "C" fn tree_root_node(tree_handle: i32) -> i32 {
    unsafe {
        let tree = match TREES.get(tree_handle as usize) {
            Some(Some(t)) => t,
            _ => return -1,
        };
        let node = tree.root_node();
        store_node(node)
    }
}

#[no_mangle]
pub extern "C" fn tree_delete(tree_handle: i32) {
    unsafe {
        if let Some(slot) = TREES.get_mut(tree_handle as usize) {
            *slot = None;
        }
    }
}

// --- Node API ---

fn store_node<'a>(node: Node<'a>) -> i32 {
    let erased: Node<'static> = unsafe { mem::transmute(node) };
    unsafe {
        let handle = NODES.len() as i32;
        NODES.push(Some(erased));
        handle
    }
}

fn get_node(handle: i32) -> Option<&'static Node<'static>> {
    unsafe { NODES.get(handle as usize).and_then(|opt| opt.as_ref()) }
}

/// Get the type of a node. Result available via get_result_ptr/get_result_len.
#[no_mangle]
pub extern "C" fn node_type(node_handle: i32) -> i32 {
    match get_node(node_handle) {
        Some(node) => {
            set_result(node.kind().as_bytes());
            0
        }
        None => -1,
    }
}

#[no_mangle]
pub extern "C" fn node_child_count(node_handle: i32) -> i32 {
    match get_node(node_handle) {
        Some(node) => node.child_count() as i32,
        None => -1,
    }
}

#[no_mangle]
pub extern "C" fn node_named_child_count(node_handle: i32) -> i32 {
    match get_node(node_handle) {
        Some(node) => node.named_child_count() as i32,
        None => -1,
    }
}

/// Get a named child node by index. Returns node handle, or -1 if not found.
#[no_mangle]
pub extern "C" fn node_named_child(node_handle: i32, index: i32) -> i32 {
    match get_node(node_handle) {
        Some(node) => match node.named_child(index as usize) {
            Some(child) => store_node(child),
            None => -1,
        },
        None => -1,
    }
}

/// Get a child node by index. Returns node handle, or -1 if not found.
#[no_mangle]
pub extern "C" fn node_child(node_handle: i32, index: i32) -> i32 {
    match get_node(node_handle) {
        Some(node) => match node.child(index as usize) {
            Some(child) => store_node(child),
            None => -1,
        },
        None => -1,
    }
}

/// Get the s-expression of a node. Result available via get_result_ptr/get_result_len.
#[no_mangle]
pub extern "C" fn node_string(node_handle: i32) -> i32 {
    match get_node(node_handle) {
        Some(node) => {
            let sexp = node.to_sexp();
            set_result(sexp.as_bytes());
            0
        }
        None => -1,
    }
}

/// Get the start byte of a node.
#[no_mangle]
pub extern "C" fn node_start_byte(node_handle: i32) -> i32 {
    match get_node(node_handle) {
        Some(node) => node.start_byte() as i32,
        None => -1,
    }
}

/// Get the end byte of a node.
#[no_mangle]
pub extern "C" fn node_end_byte(node_handle: i32) -> i32 {
    match get_node(node_handle) {
        Some(node) => node.end_byte() as i32,
        None => -1,
    }
}

/// Check if a node is named.
#[no_mangle]
pub extern "C" fn node_is_named(node_handle: i32) -> i32 {
    match get_node(node_handle) {
        Some(node) => if node.is_named() { 1 } else { 0 },
        None => -1,
    }
}
