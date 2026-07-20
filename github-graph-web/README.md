# github-graph-web

Next.js frontend for the GitHub Graph repository-intelligence platform.

## Implemented experiences

- public repository submission and ingestion progress;
- repository overview and analytics dashboard;
- interactive React Flow code graph and node details;
- dependency and impact analysis;
- function similarity and clustering;
- stack-trace localization and root-cause confirmation;
- grounded AI explanations with citations;
- responsive mobile workspace and accessible graph fallback.

See [`../PHASE_8.md`](../PHASE_8.md) for the complete implementation and
verification record.

## Local development

```bash
npm install
npm run dev
```

Set `NEXT_PUBLIC_API_BASE_URL` to the Spring Boot backend URL. It defaults to
`http://localhost:8080`.

## Frontend-only contract environment

When the backend is not running:

```bash
npm run e2e:mock-api
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8180 npm run dev -- --port 4173
```

Open `http://127.0.0.1:4173/repositories/phase8-demo`.

## Production quality gate

```bash
npm ci
npm run build
npm audit
npm run start
```

The Docker image uses the same production build and starts Next.js with
`npm run start`.
