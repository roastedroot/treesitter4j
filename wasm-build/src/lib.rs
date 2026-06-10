use std::mem;
use std::slice;
use streaming_iterator::StreamingIterator;
use tree_sitter::{Language, Node, Parser, Tree, Query, QueryCursor};

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
const LANG_PROPERTIES: i32 = 2;
const LANG_HTML: i32 = 3;
const LANG_XML: i32 = 4;
const LANG_MARKDOWN: i32 = 5;
const LANG_YAML: i32 = 6;

fn get_language(lang_id: i32) -> Option<Language> {
    match lang_id {
        LANG_JSON => Some(tree_sitter_json::LANGUAGE.into()),
        LANG_JAVA => Some(tree_sitter_java::LANGUAGE.into()),
        LANG_PROPERTIES => Some(tree_sitter_properties::LANGUAGE.into()),
        LANG_HTML => Some(tree_sitter_html::LANGUAGE.into()),
        LANG_XML => Some(tree_sitter_xml::LANGUAGE_XML.into()),
        LANG_MARKDOWN => Some(tree_sitter_md::LANGUAGE.into()),
        LANG_YAML => Some(tree_sitter_yaml::LANGUAGE.into()),
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

/// Get the node's type. Result available via get_result_ptr/get_result_len.
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
        Some(node) => match node.named_child(index as u32) {
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
        Some(node) => match node.child(index as u32) {
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

// --- Query API ---

static mut QUERIES: Vec<Option<Query>> = Vec::new();

struct QueryCursorState {
    cursor: QueryCursor,
    captures: Vec<(i32, u32)>, // (node_handle, capture_name_index)
}

static mut QUERY_CURSORS: Vec<Option<QueryCursorState>> = Vec::new();

/// Create a new query for the given language. Returns query handle, or -1 on error.
#[no_mangle]
pub extern "C" fn query_new(lang_id: i32, source_ptr: i32, source_len: i32) -> i32 {
    let lang = match get_language(lang_id) {
        Some(l) => l,
        None => return -1,
    };
    let source = unsafe { slice::from_raw_parts(source_ptr as *const u8, source_len as usize) };
    let source_str = match std::str::from_utf8(source) {
        Ok(s) => s,
        Err(_) => return -1,
    };
    match Query::new(&lang, source_str) {
        Ok(query) => unsafe {
            let handle = QUERIES.len() as i32;
            QUERIES.push(Some(query));
            handle
        },
        Err(_) => -1,
    }
}

/// Delete a query.
#[no_mangle]
pub extern "C" fn query_delete(handle: i32) {
    unsafe {
        if let Some(slot) = QUERIES.get_mut(handle as usize) {
            *slot = None;
        }
    }
}

/// Get the number of patterns in a query.
#[no_mangle]
pub extern "C" fn query_pattern_count(handle: i32) -> i32 {
    unsafe {
        match QUERIES.get(handle as usize) {
            Some(Some(q)) => q.pattern_count() as i32,
            _ => -1,
        }
    }
}

/// Get the number of capture names in a query.
#[no_mangle]
pub extern "C" fn query_capture_count(handle: i32) -> i32 {
    unsafe {
        match QUERIES.get(handle as usize) {
            Some(Some(q)) => q.capture_names().len() as i32,
            _ => -1,
        }
    }
}

/// Get the name of a capture by index. Result available via get_result_ptr/get_result_len.
#[no_mangle]
pub extern "C" fn query_capture_name(query_handle: i32, name_index: i32) -> i32 {
    unsafe {
        match QUERIES.get(query_handle as usize) {
            Some(Some(q)) => {
                match q.capture_names().get(name_index as usize) {
                    Some(name) => {
                        set_result(name.as_bytes());
                        0
                    }
                    None => -1,
                }
            }
            _ => -1,
        }
    }
}

/// Create a new query cursor. Returns cursor handle.
#[no_mangle]
pub extern "C" fn query_cursor_new() -> i32 {
    unsafe {
        let handle = QUERY_CURSORS.len() as i32;
        QUERY_CURSORS.push(Some(QueryCursorState {
            cursor: QueryCursor::new(),
            captures: Vec::new(),
        }));
        handle
    }
}

/// Delete a query cursor.
#[no_mangle]
pub extern "C" fn query_cursor_delete(handle: i32) {
    unsafe {
        if let Some(slot) = QUERY_CURSORS.get_mut(handle as usize) {
            *slot = None;
        }
    }
}

/// Execute a query cursor against a node. Collects all captures.
/// Returns the number of captures, or -1 on error.
#[no_mangle]
pub extern "C" fn query_cursor_exec(
    cursor_handle: i32,
    query_handle: i32,
    node_handle: i32,
    source_ptr: i32,
    source_len: i32,
) -> i32 {
    unsafe {
        // Take cursor state out to avoid borrow conflicts
        let mut state = match QUERY_CURSORS.get_mut(cursor_handle as usize) {
            Some(slot) => match slot.take() {
                Some(s) => s,
                None => return -1,
            },
            None => return -1,
        };

        let query = match QUERIES.get(query_handle as usize) {
            Some(Some(q)) => q as *const Query,
            _ => {
                QUERY_CURSORS[cursor_handle as usize] = Some(state);
                return -1;
            }
        };

        let node = match get_node(node_handle) {
            Some(n) => *n,
            None => {
                QUERY_CURSORS[cursor_handle as usize] = Some(state);
                return -1;
            }
        };

        let source = slice::from_raw_parts(source_ptr as *const u8, source_len as usize);

        // Collect all captures from all matches
        let mut local_captures = Vec::new();
        let mut matches = state.cursor.matches(&*query, node, source);
        while let Some(m) = matches.next() {
            for capture in m.captures {
                let nh = store_node(capture.node);
                local_captures.push((nh, capture.index));
            }
        }
        drop(matches);
        state.captures = local_captures;

        let count = state.captures.len() as i32;

        // Put cursor state back
        QUERY_CURSORS[cursor_handle as usize] = Some(state);

        count
    }
}

/// Get the node handle for a capture at the given index.
#[no_mangle]
pub extern "C" fn query_cursor_capture_node(cursor_handle: i32, index: i32) -> i32 {
    unsafe {
        match QUERY_CURSORS.get(cursor_handle as usize) {
            Some(Some(state)) => {
                match state.captures.get(index as usize) {
                    Some((node_handle, _)) => *node_handle,
                    None => -1,
                }
            }
            _ => -1,
        }
    }
}

/// Get the capture name index for a capture at the given index.
#[no_mangle]
pub extern "C" fn query_cursor_capture_name_id(cursor_handle: i32, index: i32) -> i32 {
    unsafe {
        match QUERY_CURSORS.get(cursor_handle as usize) {
            Some(Some(state)) => {
                match state.captures.get(index as usize) {
                    Some((_, name_id)) => *name_id as i32,
                    None => -1,
                }
            }
            _ => -1,
        }
    }
}
