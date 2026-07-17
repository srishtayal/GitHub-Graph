from app.schemas.responses import GraphEdge, GraphNode, GraphPayload


def similarity_graph() -> GraphPayload:
    return GraphPayload(
        nodes=[
            _node("repo:sample", "repo", "sample"),
            _node("file:auth-a", "file", "auth_a.py"),
            _node("file:auth-b", "file", "auth_b.py"),
            _node("file:data", "file", "data.py"),
            _node("function:login-a", "function", "login_a"),
            _node("function:login-b", "function", "login_b"),
            _node("function:validate", "function", "validate"),
            _node("function:save", "function", "save"),
            _node("module:jwt", "module", "jwt"),
            _node("module:database", "module", "database"),
        ],
        edges=[
            _edge("edge:01", "file:auth-a", "repo:sample", "BELONGS_TO"),
            _edge("edge:02", "file:auth-b", "repo:sample", "BELONGS_TO"),
            _edge("edge:03", "file:data", "repo:sample", "BELONGS_TO"),
            _edge("edge:04", "function:login-a", "file:auth-a", "BELONGS_TO"),
            _edge("edge:05", "function:login-b", "file:auth-b", "BELONGS_TO"),
            _edge("edge:06", "function:validate", "file:auth-a", "BELONGS_TO"),
            _edge("edge:07", "function:save", "file:data", "BELONGS_TO"),
            _edge("edge:08", "file:auth-a", "module:jwt", "IMPORTS"),
            _edge("edge:09", "file:auth-b", "module:jwt", "IMPORTS"),
            _edge("edge:10", "file:data", "module:database", "IMPORTS"),
            _edge("edge:11", "function:login-a", "function:validate", "CALLS"),
            _edge("edge:12", "function:login-b", "function:validate", "CALLS"),
            _edge("edge:13", "function:login-a", "module:jwt", "CALLS"),
            _edge("edge:14", "function:login-b", "module:jwt", "CALLS"),
            _edge("edge:15", "function:save", "module:database", "CALLS"),
        ],
    )


def _node(node_id: str, node_type: str, label: str) -> GraphNode:
    return GraphNode(id=node_id, type=node_type, label=label)


def _edge(edge_id: str, source: str, target: str, edge_type: str) -> GraphEdge:
    return GraphEdge(id=edge_id, source=source, target=target, type=edge_type)
