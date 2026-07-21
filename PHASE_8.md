# GitHub Graph - Phase 8

Phase 8 turns the Phase 2-7 backend capabilities into a responsive repository
intelligence workspace. The implementation is in `github-graph-web`.

## Product surfaces

### Landing and ingestion

- Editorial repository-intelligence landing page.
- Public GitHub repository submission with validation and loading feedback.
- Sample Python repositories.
- Local recent-workspace history.
- Full ingestion pipeline state with retry and categorized failure handling.

### Repository dashboard

- Repository and snapshot context.
- Function, class, relationship, component, and cycle metrics.
- Language distribution.
- Critical-node ranking with centrality scores.
- Node-type distribution.
- Direct navigation to graph, similarity, failure, and explanation workflows.

### Interactive graph explorer

- Repository overview is the default graph level, bounded to 15 major components.
- Deterministic flat-package grouping exposes cohesive Python capability areas.
- Directed layered layout communicates dependency flow rather than node-type lanes.
- Component cards show purpose, file count, symbol count, and dependency emphasis.
- Aggregated edge width and labels communicate relationship volume and type.
- Hover and selection expose edge breakdowns backed by raw graph edge IDs.
- Click-to-inspect and double-click/Open component drill into files and modules.
- Repository/component breadcrumbs and an explanatory architecture legend.
- Explicit Detailed graph action preserves the full symbol-level explorer.
- React Flow canvas with pan, zoom, fit, minimap, and draggable nodes.
- Stable node-type colors and edge-type colors.
- Search by label, qualified name, relative path, route, and module.
- Node-type and edge-type filters.
- Click-to-open node detail panel.
- Incoming and outgoing relationship lists.
- File path, source lines, metadata, stable ID, and centrality rank.
- Live Phase 5 dependency and impact highlighting.
- One- and two-hop neighborhood focus.
- Compact radial layout for small graphs.
- Bounded lane layout for large graphs with a 180-node rendering limit.
- Accessible list alternative for keyboard and assistive-technology users.

### Dependency and analytics view

- Node selection shared with the graph explorer.
- DFS dependency trace with depth, predecessor, and edge evidence.
- BFS impact map with affected-node count and depth.
- Component, cycle, topological-order, and criticality summaries on the
  dashboard.

### Similarity view

- Function selection and configurable cluster threshold.
- Public Phase 6 similarity ranking.
- Score bars and feature-level Jaccard evidence.
- Function clusters and cluster membership navigation.

### Error analysis view

- Optional failing-node selection.
- Error-log and stack-trace input.
- Resolved and unresolved frame counts.
- Impacted-node summary.
- Ranked root-cause candidates with confidence and score.
- Evidence-contribution breakdown for every candidate.
- Failure-history persistence.
- Confirmed-root-cause workflow that feeds future localization.

### Grounded explanation view

- Free-form questions and three guided repository questions.
- Optional target node and error context.
- Intent, confidence, answer, limitations, and follow-up suggestions.
- Supporting evidence citations.
- Referenced graph-node navigation.
- Snapshot, model, prompt, and orchestration metadata.
- Controlled provider error states.

## Visual and accessibility system

- Locally bundled Manrope and JetBrains Mono variable fonts.
- Warm paper, ink, coral, teal, blue, and amber semantic palette.
- Responsive desktop, tablet, and mobile layouts.
- Mobile slide-out workspace navigation.
- Visible focus treatment and semantic controls.
- Reduced-motion support.
- Loading, empty, error, and disabled states.
- No runtime font or image dependency.

## API integration

The frontend uses the existing Spring Boot APIs for:

- repository summary, files, symbols, extraction, and graph;
- bounded repository overview and component graph projections;
- all six Phase 5 analytics;
- Phase 6 similarity, clusters, localization, failures, and confirmation;
- Phase 7 grounded explanation orchestration.

The TypeScript contracts in `src/lib/types.ts` mirror those backend responses.

## Verification

Run the production quality gate:

```bash
cd github-graph-web
npm ci
npm run build
npm audit
```

The deterministic browser contract API is available for frontend-only
verification when the real backend is not running:

```bash
npm run e2e:mock-api
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8180 npm run dev -- --port 4173
```

Then open:

`http://127.0.0.1:4173/repositories/phase8-demo`

Browser verification covered:

- desktop and 390 px mobile landing pages;
- mobile workspace navigation with no horizontal overflow;
- dashboard rendering;
- graph search, selection, detail, dependency highlighting, filters, and
  accessible fallback;
- repository overview layout, aggregate relationship labels, component details,
  and component drill-down;
- DFS and BFS results;
- function similarity and clustering;
- stack localization, persistence, and root-cause confirmation;
- grounded explanation citations and graph references.

## Scope boundary

Phase 8 is complete for the current one-public-repository-at-a-time product
scope. A server-backed repository catalog, authentication, saved user
workspaces, PDF/JSON reports, and report exports belong to Phase 9.
