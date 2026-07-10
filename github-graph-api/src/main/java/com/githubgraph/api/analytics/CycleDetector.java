package com.githubgraph.api.analytics;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CycleDetector {

    /**
     * Detects cycles in the directed dependency graph and returns cycle paths. Complexity: O(V + E).
     */
    public CycleResult detectCycles(GraphView graph) {
        Map<String, VisitState> states = new HashMap<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        List<List<String>> cycles = new ArrayList<>();
        Set<String> seenCycles = new HashSet<>();

        for (String nodeId : graph.nodesById().keySet()) {
            if (states.getOrDefault(nodeId, VisitState.UNVISITED) == VisitState.UNVISITED) {
                dfs(graph, nodeId, states, stack, cycles, seenCycles);
            }
        }

        return new CycleResult(cycles);
    }

    private void dfs(
            GraphView graph,
            String currentNodeId,
            Map<String, VisitState> states,
            ArrayDeque<String> stack,
            List<List<String>> cycles,
            Set<String> seenCycles
    ) {
        states.put(currentNodeId, VisitState.VISITING);
        stack.addLast(currentNodeId);

        for (GraphEdgeView edge : graph.outgoingDependencyEdges(currentNodeId)) {
            String neighbor = edge.target();
            VisitState state = states.getOrDefault(neighbor, VisitState.UNVISITED);
            if (state == VisitState.UNVISITED) {
                dfs(graph, neighbor, states, stack, cycles, seenCycles);
            } else if (state == VisitState.VISITING) {
                List<String> cycle = extractCycle(stack, neighbor);
                String key = canonicalCycleKey(cycle);
                if (seenCycles.add(key)) {
                    cycles.add(cycle);
                }
            }
        }

        stack.removeLast();
        states.put(currentNodeId, VisitState.VISITED);
    }

    private List<String> extractCycle(ArrayDeque<String> stack, String cycleStartNodeId) {
        List<String> cycle = new ArrayList<>();
        boolean capture = false;
        for (String nodeId : stack) {
            if (nodeId.equals(cycleStartNodeId)) {
                capture = true;
            }
            if (capture) {
                cycle.add(nodeId);
            }
        }
        cycle.add(cycleStartNodeId);
        return cycle;
    }

    private String canonicalCycleKey(List<String> cycle) {
        List<String> normalized = new ArrayList<>(cycle.subList(0, cycle.size() - 1));
        if (normalized.isEmpty()) {
            return "";
        }
        int bestIndex = 0;
        for (int i = 1; i < normalized.size(); i++) {
            if (normalized.get(i).compareTo(normalized.get(bestIndex)) < 0) {
                bestIndex = i;
            }
        }
        List<String> rotated = new ArrayList<>();
        for (int i = 0; i < normalized.size(); i++) {
            rotated.add(normalized.get((bestIndex + i) % normalized.size()));
        }
        rotated.add(rotated.getFirst());
        return String.join("->", rotated);
    }

    private enum VisitState {
        UNVISITED,
        VISITING,
        VISITED
    }

    public record CycleResult(
            List<List<String>> cycles
    ) {
        public boolean hasCycles() {
            return !cycles.isEmpty();
        }
    }
}
