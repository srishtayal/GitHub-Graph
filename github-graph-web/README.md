# github-graph-web

Next.js frontend for GitHub Graph Phase 1.

## Responsibilities

- Accept a public GitHub repository URL
- Submit ingestion requests to the backend
- Display ingestion status
- Show extracted repository metadata

## Local development

```bash
npm install
npm run dev
```

Set `NEXT_PUBLIC_API_BASE_URL` to the backend URL.

## Production build

```bash
npm ci
npm run build
npm run start
```

The Docker image uses the same production build and starts Next.js with
`npm run start`.
