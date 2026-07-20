"use client";

import { useDeferredValue, useState } from "react";
import { ChevronDown, Search, X } from "lucide-react";
import { nodeMeta, nodePath, nodeQualifiedName } from "@/lib/graph-utils";
import type { GraphNode } from "@/lib/types";

type NodePickerProps = {
  nodes: GraphNode[];
  selectedNode: GraphNode | null;
  onSelectNode: (node: GraphNode) => void;
  allowedTypes?: string[];
  label?: string;
  placeholder?: string;
};

export function NodePicker({
  nodes,
  selectedNode,
  onSelectNode,
  allowedTypes,
  label = "Target node",
  placeholder = "Search functions, files or classes…"
}: NodePickerProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const deferredQuery = useDeferredValue(query);
  const eligible = allowedTypes
    ? nodes.filter((node) => allowedTypes.includes(node.type))
    : nodes;
  const normalized = deferredQuery.trim().toLowerCase();
  const results = eligible
    .filter((node) =>
      !normalized
        ? true
        : [node.label, nodeQualifiedName(node), nodePath(node) ?? "", node.type]
            .join(" ")
            .toLowerCase()
            .includes(normalized)
    )
    .slice(0, 12);

  function choose(node: GraphNode) {
    onSelectNode(node);
    setQuery("");
    setOpen(false);
  }

  return (
    <div className="node-picker">
      <label>{label}</label>
      <button
        type="button"
        className="node-picker-trigger"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
      >
        {selectedNode ? (
          <>
            <span style={{ background: nodeMeta(selectedNode.type).color }}>
              {nodeMeta(selectedNode.type).short}
            </span>
            <span>
              <strong>{selectedNode.label}</strong>
              <small>{nodePath(selectedNode) ?? nodeMeta(selectedNode.type).label}</small>
            </span>
          </>
        ) : (
          <span className="node-picker-empty">Choose a graph node</span>
        )}
        <ChevronDown size={17} />
      </button>
      {open ? (
        <div className="node-picker-menu">
          <div>
            <Search size={16} />
            <input
              autoFocus
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={placeholder}
            />
            {query ? <button onClick={() => setQuery("")}><X size={14} /></button> : null}
          </div>
          <div className="node-picker-results">
            {results.map((node) => (
              <button key={node.id} onClick={() => choose(node)}>
                <span style={{ background: nodeMeta(node.type).color }}>
                  {nodeMeta(node.type).short}
                </span>
                <span>
                  <strong>{node.label}</strong>
                  <small>{nodePath(node) ?? nodeQualifiedName(node)}</small>
                </span>
              </button>
            ))}
            {results.length === 0 ? <p>No matching nodes.</p> : null}
          </div>
        </div>
      ) : null}
    </div>
  );
}
