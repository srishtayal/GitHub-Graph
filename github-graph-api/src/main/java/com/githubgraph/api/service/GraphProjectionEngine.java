package com.githubgraph.api.service;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import com.githubgraph.api.dto.graph.GraphProjectionResponse;
import com.githubgraph.api.dto.graph.GraphProjectionResponse.NodeCounts;
import com.githubgraph.api.dto.graph.GraphProjectionResponse.ProjectedEdge;
import com.githubgraph.api.dto.graph.GraphProjectionResponse.ProjectedNode;
import com.githubgraph.api.dto.graph.GraphProjectionResponse.ProjectionTotals;
import com.githubgraph.api.dto.graph.GraphProjectionResponse.RepresentativeReference;
import com.githubgraph.api.exception.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class GraphProjectionEngine {

    static final int OVERVIEW_MAX_NODES = 15;
    static final int COMPONENT_MAX_NODES = 40;
    static final int FILE_MAX_NODES = 80;
    static final int NEIGHBORHOOD_MAX_NODES = 200;
    private static final Set<String> PROTECTED_COMPONENTS = Set.of("testing", "documentation", "build-ci");
    private static final Set<String> BUILD_FILES = Set.of(
            "dockerfile", "makefile", "pyproject.toml", "setup.py", "setup.cfg", "tox.ini",
            "requirements.txt", "package.json", "package-lock.json", "pom.xml", "build.gradle",
            "gradle.properties", "docker-compose.yml", "docker-compose.yaml", ".gitignore",
            ".editorconfig", ".pre-commit-config.yaml", "mypy.ini", "pytest.ini"
    );
    private static final Map<String, String> DISPLAY_NAME_RULES = Map.of(
            "source:src/itsdangerous", "ItsDangerous Core",
            "source:src/fastapi", "FastAPI Core",
            "source:src/flask", "Flask Core"
    );
    private static final Map<String, String> MODULE_AREA_NAMES = Map.ofEntries(
            Map.entry("__init__", "Public API"),
            Map.entry("signer", "Signing and Verification"),
            Map.entry("timed", "Timed Signing"),
            Map.entry("serializer", "Serialization"),
            Map.entry("url_safe", "URL-Safe Serialization"),
            Map.entry("encoding-json", "Encoding and JSON"),
            Map.entry("exc", "Exception Model"),
            Map.entry("exceptions", "Exception Model"),
            Map.entry("errors", "Exception Model")
    );

    public GraphProjectionResponse overview(String repositoryId, String snapshotId, GraphView graph) {
        List<ComponentGroup> groups = detectComponents(repositoryId, graph);
        Map<String, String> rawToProjected = new HashMap<>();
        Map<String, Set<String>> members = new LinkedHashMap<>();
        Map<String, NodeDescriptor> descriptors = new LinkedHashMap<>();

        for (ComponentGroup group : groups) {
            members.put(group.id(), group.memberIds());
            group.memberIds().forEach(rawId -> rawToProjected.put(rawId, group.id()));
            descriptors.put(group.id(), new NodeDescriptor(
                    group.displayName(), "COMPONENT", group.category(), true
            ));
        }

        return project(
                repositoryId,
                snapshotId,
                "OVERVIEW",
                repositoryRootId(graph),
                OVERVIEW_MAX_NODES,
                false,
                graph,
                members,
                descriptors,
                rawToProjected,
                true
        );
    }

    public GraphProjectionResponse component(
            String repositoryId,
            String snapshotId,
            GraphView graph,
            String componentId
    ) {
        ComponentGroup group = detectComponents(repositoryId, graph).stream()
                .filter(candidate -> candidate.id().equals(componentId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Graph component not found: " + componentId));
        Map<String, String> fileIdByPath = fileIdsByPath(graph);
        Map<String, String> rawToCandidate = rawToFileOrModule(graph, fileIdByPath);
        Set<String> baseCandidates = new LinkedHashSet<>();

        for (String rawId : group.memberIds()) {
            GraphNodeView rawNode = graph.node(rawId);
            if (rawNode != null && (isType(rawNode, "file") || isType(rawNode, "module"))) {
                baseCandidates.add(rawId);
            }
        }

        Set<String> contextCandidates = connectedBoundaryCandidates(
                graph,
                group.memberIds(),
                rawToCandidate,
                group.key().equals("external-dependencies")
        );
        contextCandidates.removeAll(baseCandidates);
        List<String> orderedCandidates = new ArrayList<>(baseCandidates);
        orderedCandidates.sort(nodeIdPriority(graph));
        List<String> orderedContext = new ArrayList<>(contextCandidates);
        orderedContext.sort(nodeIdPriority(graph));
        orderedCandidates.addAll(orderedContext);

        boolean truncated = orderedCandidates.size() > COMPONENT_MAX_NODES;
        Set<String> selected = new LinkedHashSet<>(
                orderedCandidates.subList(0, Math.min(COMPONENT_MAX_NODES, orderedCandidates.size()))
        );
        Map<String, Set<String>> members = new LinkedHashMap<>();
        Map<String, NodeDescriptor> descriptors = new LinkedHashMap<>();

        for (String projectedId : selected) {
            GraphNodeView node = graph.node(projectedId);
            if (node == null) {
                continue;
            }
            Set<String> rawMembers = graph.nodes().stream()
                    .filter(raw -> projectedId.equals(rawToCandidate.get(raw.id())))
                    .map(GraphNodeView::id)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            members.put(projectedId, rawMembers);
            descriptors.put(projectedId, new NodeDescriptor(
                    node.label(), node.type().toUpperCase(Locale.ROOT), node.type(), isType(node, "file")
            ));
        }

        return project(
                repositoryId,
                snapshotId,
                "COMPONENT",
                componentId,
                COMPONENT_MAX_NODES,
                truncated,
                graph,
                members,
                descriptors,
                rawToCandidate,
                false
        );
    }

    public GraphProjectionResponse file(
            String repositoryId,
            String snapshotId,
            GraphView graph,
            String fileId
    ) {
        GraphNodeView file = graph.node(fileId);
        if (file == null || !isType(file, "file")) {
            throw new NotFoundException("Graph file not found: " + fileId);
        }
        String path = relativePath(file);
        List<GraphNodeView> children = graph.nodes().stream()
                .filter(node -> path.equals(relativePath(node)))
                .filter(node -> isType(node, "class") || isType(node, "function") || isType(node, "api"))
                .sorted(nodePriority(graph))
                .toList();
        boolean truncated = children.size() > FILE_MAX_NODES;
        List<GraphNodeView> selected = children.subList(0, Math.min(FILE_MAX_NODES, children.size()));
        Map<String, Set<String>> members = new LinkedHashMap<>();
        Map<String, NodeDescriptor> descriptors = new LinkedHashMap<>();
        Map<String, String> rawToProjected = new HashMap<>();

        for (GraphNodeView node : selected) {
            members.put(node.id(), Set.of(node.id()));
            rawToProjected.put(node.id(), node.id());
            descriptors.put(node.id(), new NodeDescriptor(
                    node.label(), node.type().toUpperCase(Locale.ROOT), node.type(), dependencyDegree(graph, node.id()) > 0
            ));
        }

        return project(
                repositoryId,
                snapshotId,
                "FILE",
                fileId,
                FILE_MAX_NODES,
                truncated,
                graph,
                members,
                descriptors,
                rawToProjected,
                false
        );
    }

    public GraphProjectionResponse neighborhood(
            String repositoryId,
            String snapshotId,
            GraphView graph,
            String nodeId,
            int depth
    ) {
        if (!graph.containsNode(nodeId)) {
            throw new NotFoundException("Graph node not found: " + nodeId);
        }
        if (depth < 0 || depth > 5) {
            throw new IllegalArgumentException("Neighborhood depth must be between 0 and 5");
        }

        NeighborhoodSelection selection = dependencyNeighborhood(graph, nodeId, depth, NEIGHBORHOOD_MAX_NODES);
        LinkedHashSet<String> selected = selection.nodeIds();
        Map<String, Set<String>> members = new LinkedHashMap<>();
        Map<String, NodeDescriptor> descriptors = new LinkedHashMap<>();
        Map<String, String> rawToProjected = new HashMap<>();
        for (String id : selected) {
            GraphNodeView node = graph.node(id);
            members.put(id, Set.of(id));
            rawToProjected.put(id, id);
            descriptors.put(id, new NodeDescriptor(node.label(), "NEIGHBORHOOD", node.type(), dependencyDegree(graph, id) > 0));
        }

        return project(
                repositoryId,
                snapshotId,
                "NEIGHBORHOOD",
                nodeId,
                NEIGHBORHOOD_MAX_NODES,
                selection.truncated(),
                graph,
                members,
                descriptors,
                rawToProjected,
                true
        );
    }

    private GraphProjectionResponse project(
            String repositoryId,
            String snapshotId,
            String level,
            String rootId,
            int suggestedMaximumNodes,
            boolean truncated,
            GraphView graph,
            Map<String, Set<String>> members,
            Map<String, NodeDescriptor> descriptors,
            Map<String, String> rawToProjected,
            boolean dependencyEdgesOnly
    ) {
        Set<String> projectedIds = members.keySet();
        List<ProjectedEdge> edges = aggregateEdges(graph, rawToProjected, projectedIds, level, dependencyEdgesOnly);
        DependencyCounts dependencyCounts = countBoundaryDependencies(graph, members);
        int dependencyEdgeCount = Math.max(graph.dependencyEdges().size(), 1);
        List<ProjectedNode> nodes = members.entrySet().stream()
                .map(entry -> {
                    String id = entry.getKey();
                    Set<String> rawMembers = entry.getValue();
                    NodeDescriptor descriptor = descriptors.get(id);
                    int in = dependencyCounts.incoming().getOrDefault(id, 0);
                    int out = dependencyCounts.outgoing().getOrDefault(id, 0);
                    return new ProjectedNode(
                            id,
                            descriptor.displayName(),
                            descriptor.level(),
                            descriptor.category(),
                            countTypes(graph, rawMembers),
                            in,
                            out,
                            rounded((in + out) / (double) dependencyEdgeCount),
                            directChildCount(graph, rawMembers, descriptor.level()),
                            representatives(graph, rawMembers),
                            rawMembers.stream().sorted().toList(),
                            descriptor.expandable()
                    );
                })
                .sorted(Comparator.comparing(ProjectedNode::id))
                .toList();

        return new GraphProjectionResponse(
                repositoryId,
                snapshotId,
                level,
                rootId,
                suggestedMaximumNodes,
                truncated,
                new ProjectionTotals(graph.nodes().size(), graph.edges().size(), nodes.size(), edges.size()),
                nodes,
                edges
        );
    }

    private List<ProjectedEdge> aggregateEdges(
            GraphView graph,
            Map<String, String> rawToProjected,
            Set<String> allowedProjectedIds,
            String level,
            boolean dependencyEdgesOnly
    ) {
        Map<EdgeKey, EdgeAccumulator> aggregated = new TreeMap<>();
        for (GraphEdgeView edge : graph.edges()) {
            if (dependencyEdgesOnly && !edge.isDependencyEdge()) {
                continue;
            }
            String source = rawToProjected.get(edge.source());
            String target = rawToProjected.get(edge.target());
            if (source == null || target == null || source.equals(target)
                    || !allowedProjectedIds.contains(source) || !allowedProjectedIds.contains(target)) {
                continue;
            }
            EdgeKey key = new EdgeKey(source, target);
            aggregated.computeIfAbsent(key, ignored -> new EdgeAccumulator()).add(edge);
        }

        return aggregated.entrySet().stream()
                .map(entry -> {
                    EdgeKey key = entry.getKey();
                    EdgeAccumulator value = entry.getValue();
                    return new ProjectedEdge(
                            "projected-edge:" + stableHash(level + "|" + key.source() + "|" + key.target()),
                            key.source(),
                            key.target(),
                            "AGGREGATED",
                            value.edgeIds.size(),
                            Collections.unmodifiableMap(new LinkedHashMap<>(value.countsByType)),
                            value.edgeIds.stream().sorted().toList()
                    );
                })
                .toList();
    }

    private List<ComponentGroup> detectComponents(String repositoryId, GraphView graph) {
        Map<String, ComponentDraft> drafts = new TreeMap<>();
        Set<String> packageRoots = pythonPackageRoots(graph);
        for (GraphNodeView node : graph.nodes()) {
            String key;
            if (isType(node, "module")) {
                key = "external-dependencies";
            } else {
                String path = relativePath(node);
                if (path == null || path.isBlank()) {
                    continue;
                }
                key = componentKey(path, packageRoots);
            }
            drafts.computeIfAbsent(key, ignored -> new ComponentDraft(key, componentDisplayName(key), componentCategory(key)))
                    .memberIds.add(node.id());
        }

        mergeSmallGroups(drafts, graph, "supporting-files", "Supporting Files");
        splitFlatPythonPackages(drafts, graph);
        if (drafts.size() > OVERVIEW_MAX_NODES) {
            mergeOverflowGroups(drafts, graph);
        }

        return drafts.values().stream()
                .filter(draft -> !draft.memberIds.isEmpty())
                .map(draft -> new ComponentGroup(
                        "component:" + stableHash(repositoryId + "|" + draft.key),
                        draft.key,
                        draft.displayName,
                        draft.category,
                        Set.copyOf(draft.memberIds)
                ))
                .sorted(Comparator.comparing(ComponentGroup::id))
                .toList();
    }

    private void splitFlatPythonPackages(Map<String, ComponentDraft> drafts, GraphView graph) {
        List<String> sourceKeys = drafts.keySet().stream()
                .filter(key -> key.startsWith("source:") && !key.equals("source:root"))
                .toList();
        for (String sourceKey : sourceKeys) {
            ComponentDraft source = drafts.get(sourceKey);
            if (source == null) {
                continue;
            }
            String packageRoot = sourceKey.substring("source:".length());
            long directPythonFiles = source.memberIds.stream()
                    .map(graph::node)
                    .filter(node -> node != null && isType(node, "file"))
                    .map(this::relativePath)
                    .filter(path -> directPythonModule(path, packageRoot) != null)
                    .count();
            if (directPythonFiles < 5) {
                continue;
            }

            Map<String, ComponentDraft> areas = new TreeMap<>();
            for (String memberId : source.memberIds) {
                GraphNodeView node = graph.node(memberId);
                String module = directPythonModule(relativePath(node), packageRoot);
                String area = module == null ? "package-support" : moduleAreaKey(module);
                String areaKey = "area:" + packageRoot + "/" + area;
                areas.computeIfAbsent(
                        areaKey,
                        ignored -> new ComponentDraft(areaKey, moduleAreaDisplayName(area), "source-area")
                ).memberIds.add(memberId);
            }

            if (areas.size() < 3) {
                continue;
            }
            drafts.remove(sourceKey);
            drafts.putAll(areas);
        }
    }

    private String directPythonModule(String rawPath, String packageRoot) {
        if (rawPath == null) {
            return null;
        }
        String prefix = packageRoot + "/";
        String path = normalizePath(rawPath);
        if (!path.startsWith(prefix)) {
            return null;
        }
        String remainder = path.substring(prefix.length());
        if (remainder.contains("/") || !remainder.toLowerCase(Locale.ROOT).endsWith(".py")) {
            return null;
        }
        return remainder.substring(0, remainder.length() - 3).toLowerCase(Locale.ROOT);
    }

    private String moduleAreaKey(String module) {
        return module.equals("encoding") || module.equals("_json") ? "encoding-json" : module;
    }

    private String moduleAreaDisplayName(String area) {
        if (area.equals("package-support")) {
            return "Package Support";
        }
        return MODULE_AREA_NAMES.getOrDefault(area, humanize(area));
    }

    private void mergeSmallGroups(
            Map<String, ComponentDraft> drafts,
            GraphView graph,
            String targetKey,
            String targetName
    ) {
        if (drafts.size() < 7) {
            return;
        }
        String largestSource = drafts.values().stream()
                .filter(draft -> draft.key.startsWith("source:") || draft.key.startsWith("directory:"))
                .max(Comparator.comparingInt(draft -> fileCount(graph, draft.memberIds)))
                .map(draft -> draft.key)
                .orElse("");
        List<String> mergeKeys = drafts.values().stream()
                .filter(draft -> !PROTECTED_COMPONENTS.contains(draft.key))
                .filter(draft -> !draft.key.equals("external-dependencies"))
                .filter(draft -> !draft.key.equals(largestSource))
                .filter(draft -> fileCount(graph, draft.memberIds) <= 1)
                .map(draft -> draft.key)
                .sorted()
                .toList();
        mergeDrafts(drafts, mergeKeys, targetKey, targetName, "supporting");
    }

    private void mergeOverflowGroups(Map<String, ComponentDraft> drafts, GraphView graph) {
        List<ComponentDraft> keepOrder = drafts.values().stream()
                .sorted(Comparator
                        .comparing((ComponentDraft draft) -> !PROTECTED_COMPONENTS.contains(draft.key))
                        .thenComparing((ComponentDraft draft) -> -fileCount(graph, draft.memberIds))
                        .thenComparing(draft -> draft.key))
                .toList();
        Set<String> keep = keepOrder.stream()
                .limit(OVERVIEW_MAX_NODES - 1L)
                .map(draft -> draft.key)
                .collect(java.util.stream.Collectors.toSet());
        List<String> mergeKeys = drafts.keySet().stream().filter(key -> !keep.contains(key)).sorted().toList();
        mergeDrafts(drafts, mergeKeys, "other", "Other", "other");
    }

    private void mergeDrafts(
            Map<String, ComponentDraft> drafts,
            List<String> mergeKeys,
            String targetKey,
            String targetName,
            String category
    ) {
        if (mergeKeys.isEmpty()) {
            return;
        }
        ComponentDraft target = drafts.computeIfAbsent(
                targetKey,
                ignored -> new ComponentDraft(targetKey, targetName, category)
        );
        for (String key : mergeKeys) {
            if (key.equals(targetKey)) {
                continue;
            }
            ComponentDraft removed = drafts.remove(key);
            if (removed != null) {
                target.memberIds.addAll(removed.memberIds);
            }
        }
    }

    private String componentKey(String rawPath, Set<String> packageRoots) {
        String path = normalizePath(rawPath);
        String[] parts = path.split("/");
        String first = parts[0].toLowerCase(Locale.ROOT);
        String fileName = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        if (first.equals("tests") || first.equals("test")) {
            return "testing";
        }
        if (first.equals("docs") || first.equals("documentation")) {
            return "documentation";
        }
        if (first.equals(".github") || first.equals("build") || first.equals("config")
                || first.equals("scripts") || (parts.length == 1 && BUILD_FILES.contains(fileName))) {
            return "build-ci";
        }
        if ((first.equals("src") || first.equals("lib")) && parts.length >= 3) {
            return "source:" + first + "/" + parts[1];
        }
        if ((first.equals("src") || first.equals("lib")) && parts.length >= 2) {
            return "source:" + first;
        }
        if (packageRoots.contains(parts[0])) {
            return "source:" + parts[0];
        }
        if (parts.length == 1) {
            return fileName.endsWith(".py") ? "source:root" : "supporting-files";
        }
        return "directory:" + parts[0];
    }

    private Set<String> pythonPackageRoots(GraphView graph) {
        Set<String> roots = new HashSet<>();
        graph.nodes().stream()
                .filter(node -> isType(node, "file"))
                .map(this::relativePath)
                .filter(path -> path != null && path.endsWith("/__init__.py"))
                .forEach(path -> {
                    String[] parts = path.split("/");
                    if (parts.length >= 3 && (parts[0].equals("src") || parts[0].equals("lib"))) {
                        roots.add(parts[0] + "/" + parts[1]);
                    } else if (parts.length >= 2) {
                        roots.add(parts[0]);
                    }
                });
        return roots;
    }

    private String componentDisplayName(String key) {
        if (DISPLAY_NAME_RULES.containsKey(key)) {
            return DISPLAY_NAME_RULES.get(key);
        }
        return switch (key) {
            case "testing" -> "Testing";
            case "documentation" -> "Documentation";
            case "build-ci" -> "Build and CI";
            case "external-dependencies" -> "External Dependencies";
            case "supporting-files" -> "Supporting Files";
            case "source:root" -> "Application Core";
            default -> {
                String name = key.substring(key.indexOf(':') + 1);
                name = name.substring(name.lastIndexOf('/') + 1);
                String humanized = humanize(name);
                yield key.startsWith("source:") ? humanized + " Core" : humanized;
            }
        };
    }

    private String componentCategory(String key) {
        if (key.equals("testing") || key.equals("documentation") || key.equals("build-ci")
                || key.equals("external-dependencies") || key.equals("supporting-files")) {
            return key;
        }
        return key.startsWith("source:") ? "source" : "directory";
    }

    private Map<String, String> fileIdsByPath(GraphView graph) {
        Map<String, String> result = new HashMap<>();
        graph.nodes().stream()
                .filter(node -> isType(node, "file"))
                .forEach(node -> result.put(relativePath(node), node.id()));
        return result;
    }

    private Map<String, String> rawToFileOrModule(GraphView graph, Map<String, String> fileIdByPath) {
        Map<String, String> result = new HashMap<>();
        for (GraphNodeView node : graph.nodes()) {
            if (isType(node, "module")) {
                result.put(node.id(), node.id());
            } else {
                String fileId = fileIdByPath.get(relativePath(node));
                if (fileId != null) {
                    result.put(node.id(), fileId);
                }
            }
        }
        return result;
    }

    private Set<String> connectedBoundaryCandidates(
            GraphView graph,
            Set<String> componentMembers,
            Map<String, String> rawToCandidate,
            boolean includeFiles
    ) {
        Set<String> result = new HashSet<>();
        for (GraphEdgeView edge : graph.dependencyEdges()) {
            boolean sourceInside = componentMembers.contains(edge.source());
            boolean targetInside = componentMembers.contains(edge.target());
            if (sourceInside == targetInside) {
                continue;
            }
            String boundaryRawId = sourceInside ? edge.target() : edge.source();
            String candidate = rawToCandidate.get(boundaryRawId);
            GraphNodeView candidateNode = graph.node(candidate);
            if (candidateNode != null && (isType(candidateNode, "module") || (includeFiles && isType(candidateNode, "file")))) {
                result.add(candidate);
            }
        }
        return result;
    }

    private DependencyCounts countBoundaryDependencies(GraphView graph, Map<String, Set<String>> members) {
        Map<String, String> ownerByRawId = new HashMap<>();
        members.forEach((projectedId, rawIds) -> rawIds.forEach(rawId -> ownerByRawId.put(rawId, projectedId)));
        Map<String, Integer> incoming = new HashMap<>();
        Map<String, Integer> outgoing = new HashMap<>();
        for (GraphEdgeView edge : graph.dependencyEdges()) {
            String sourceOwner = ownerByRawId.get(edge.source());
            String targetOwner = ownerByRawId.get(edge.target());
            if (sourceOwner != null && !sourceOwner.equals(targetOwner)) {
                outgoing.merge(sourceOwner, 1, Integer::sum);
            }
            if (targetOwner != null && !targetOwner.equals(sourceOwner)) {
                incoming.merge(targetOwner, 1, Integer::sum);
            }
        }
        return new DependencyCounts(incoming, outgoing);
    }

    private NeighborhoodSelection dependencyNeighborhood(GraphView graph, String nodeId, int depth, int limit) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        ArrayDeque<NodeDepth> queue = new ArrayDeque<>();
        queue.add(new NodeDepth(nodeId, 0));
        while (!queue.isEmpty() && selected.size() < limit) {
            NodeDepth current = queue.removeFirst();
            if (!selected.add(current.nodeId()) || current.depth() >= depth) {
                continue;
            }
            dependencyNeighbors(graph, current.nodeId()).stream()
                    .filter(neighbor -> !selected.contains(neighbor))
                    .forEach(neighbor -> queue.addLast(new NodeDepth(neighbor, current.depth() + 1)));
        }
        return new NeighborhoodSelection(selected, !queue.isEmpty());
    }

    private List<String> dependencyNeighbors(GraphView graph, String nodeId) {
        Set<String> neighbors = new HashSet<>();
        graph.outgoingDependencyEdges(nodeId).forEach(edge -> neighbors.add(edge.target()));
        graph.incomingDependencyEdges(nodeId).forEach(edge -> neighbors.add(edge.source()));
        return neighbors.stream().sorted().toList();
    }

    private Comparator<GraphNodeView> nodePriority(GraphView graph) {
        return Comparator
                .comparingInt((GraphNodeView node) -> -dependencyDegree(graph, node.id()))
                .thenComparing(GraphNodeView::id);
    }

    private Comparator<String> nodeIdPriority(GraphView graph) {
        return Comparator
                .comparingInt((String nodeId) -> -dependencyDegree(graph, nodeId))
                .thenComparing(nodeId -> nodeId);
    }

    private int dependencyDegree(GraphView graph, String nodeId) {
        return graph.incomingDependencyEdges(nodeId).size() + graph.outgoingDependencyEdges(nodeId).size();
    }

    private NodeCounts countTypes(GraphView graph, Collection<String> rawIds) {
        int files = 0;
        int classes = 0;
        int functions = 0;
        int routes = 0;
        for (String id : rawIds) {
            GraphNodeView node = graph.node(id);
            if (node == null) {
                continue;
            }
            switch (node.type().toLowerCase(Locale.ROOT)) {
                case "file" -> files++;
                case "class" -> classes++;
                case "function", "method" -> functions++;
                case "api", "route" -> routes++;
                default -> {
                }
            }
        }
        return new NodeCounts(files, classes, functions, routes);
    }

    private int directChildCount(GraphView graph, Set<String> rawMembers, String level) {
        if (level.equals("COMPONENT")) {
            return (int) rawMembers.stream()
                    .map(graph::node)
                    .filter(node -> node != null && (isType(node, "file") || isType(node, "module")))
                    .count();
        }
        if (level.equals("FILE")) {
            return Math.max(rawMembers.size() - 1, 0);
        }
        if (rawMembers.size() == 1) {
            String id = rawMembers.iterator().next();
            return (int) graph.incomingEdges(id).stream().filter(GraphEdgeView::isContainmentEdge).count();
        }
        return rawMembers.size();
    }

    private List<RepresentativeReference> representatives(GraphView graph, Set<String> rawMembers) {
        return rawMembers.stream()
                .map(graph::node)
                .filter(node -> node != null && !isType(node, "repo"))
                .sorted(nodePriority(graph))
                .limit(5)
                .map(node -> new RepresentativeReference(node.id(), node.label(), node.type()))
                .toList();
    }

    private int fileCount(GraphView graph, Set<String> rawIds) {
        return (int) rawIds.stream().map(graph::node).filter(node -> node != null && isType(node, "file")).count();
    }

    private String repositoryRootId(GraphView graph) {
        return graph.nodes().stream().filter(node -> isType(node, "repo")).map(GraphNodeView::id).findFirst().orElse(null);
    }

    private String relativePath(GraphNodeView node) {
        if (node == null) {
            return null;
        }
        Object path = node.properties().get("relativePath");
        return path == null ? (isType(node, "file") ? node.label() : null) : normalizePath(String.valueOf(path));
    }

    private String normalizePath(String path) {
        return path.replace('\\', '/').replaceAll("^\\./+", "");
    }

    private boolean isType(GraphNodeView node, String type) {
        return node != null && type.equalsIgnoreCase(node.type());
    }

    private String humanize(String value) {
        String[] words = value.replace('-', '_').split("_");
        List<String> result = new ArrayList<>();
        for (String word : words) {
            if (!word.isBlank()) {
                result.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return String.join(" ", result);
    }

    private double rounded(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    private String stableHash(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int index = 0; index < 10; index++) {
                hex.append(String.format("%02x", digest[index]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ComponentGroup(
            String id,
            String key,
            String displayName,
            String category,
            Set<String> memberIds
    ) {
    }

    private static final class ComponentDraft {
        private final String key;
        private final String displayName;
        private final String category;
        private final Set<String> memberIds = new LinkedHashSet<>();

        private ComponentDraft(String key, String displayName, String category) {
            this.key = key;
            this.displayName = displayName;
            this.category = category;
        }
    }

    private record NodeDescriptor(String displayName, String level, String category, boolean expandable) {
    }

    private record EdgeKey(String source, String target) implements Comparable<EdgeKey> {
        @Override
        public int compareTo(EdgeKey other) {
            int sourceComparison = source.compareTo(other.source);
            return sourceComparison != 0 ? sourceComparison : target.compareTo(other.target);
        }
    }

    private static final class EdgeAccumulator {
        private final Map<String, Integer> countsByType = new TreeMap<>();
        private final List<String> edgeIds = new ArrayList<>();

        private void add(GraphEdgeView edge) {
            countsByType.merge(edge.type().toUpperCase(Locale.ROOT), 1, Integer::sum);
            edgeIds.add(edge.id());
        }
    }

    private record NodeDepth(String nodeId, int depth) {
    }

    private record NeighborhoodSelection(LinkedHashSet<String> nodeIds, boolean truncated) {
    }

    private record DependencyCounts(Map<String, Integer> incoming, Map<String, Integer> outgoing) {
    }
}
