# Demo Video Storyboard

This is a recording-ready 3 to 4 minute script for a placement portfolio.
Record it from the local Docker stack with screen capture. It intentionally
uses the real product rather than slides.

## Setup

```bash
cd infra
docker compose up --build
```

Open `http://localhost:3000`. Use a public Python repository such as
`https://github.com/pallets/itsdangerous`. If a completed snapshot already
exists, the demo can use the saved repository and focus on product behavior.

## Scene plan

| Time | Screen action | Narration |
| --- | --- | --- |
| 0:00-0:20 | Show the landing page and architecture diagram. | “GitHub Graph turns a public Python repository into a dependency graph, so developers can understand code flow and debug with evidence.” |
| 0:20-0:45 | Submit the repository URL or open its saved workspace. | “Spring Boot validates the repository and starts a bounded asynchronous ingestion job. Repeated scans reuse completed snapshots.” |
| 0:45-1:20 | Open dashboard and graph explorer; search for a function or file. | “The Python analysis service extracts files, symbols, calls, imports, inheritance, API routes, and modules. Neo4j stores their relationships.” |
| 1:20-1:50 | Select a node and show dependency trace and impact view. | “DFS explains upstream dependencies, while BFS shows what can be affected if this node fails. Centrality highlights critical functions.” |
| 1:50-2:20 | Open Similarity view and show a result. | “Similarity compares dependency and call features, helping find structurally related functions.” |
| 2:20-2:55 | Open Error Analysis, provide a stack trace, and show ranked candidates. | “Localization maps error evidence to the graph and returns ranked root-cause hypotheses with reasons. Confirmed findings are persisted for future investigations.” |
| 2:55-3:25 | Ask a grounded explanation question and open evidence. | “The AI layer receives bounded graph evidence and cites the nodes and edges it used, so it does not invent repository facts.” |
| 3:25-3:45 | Show history and JSON/PDF exports. | “The final product includes saved repositories, snapshot history, authentication-ready APIs, retries, and exportable reports.” |

## Recording checklist

- Keep the browser at 1440 px or wider and hide terminal windows before capture.
- Use a pre-ingested repository when time is limited; mention that ingestion is
  asynchronous rather than waiting through cloning on video.
- Do not expose `.env` files, tokens, Gemini keys, or local filesystem paths.
- Show evidence IDs or detail panels for at least one graph query and one AI
  response.
- End on the one-page write-up or the architecture diagram with the project
  repository URL visible.

## Suggested video title

`GitHub Graph: Visual Repository Intelligence with Static Analysis, Neo4j, and Grounded AI`
