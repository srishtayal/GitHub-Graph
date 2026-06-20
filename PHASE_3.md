# GitHub Graph - Phase 3

Phase 3 performs Python static code extraction and returns structured JSON that can be used to build repository and call graphs.

## Extracted data

- files and directories
- classes and base classes
- functions, methods, parameters, and async status
- imports and resolved local modules
- function and method calls with their containing caller
- class inheritance relationships
- FastAPI, Flask, Django, and imperative FastAPI routes
- internal and external module dependencies

Python's standard `ast` parser is used. Invalid Python files are skipped without failing the repository analysis job. Java, JavaScript, and TypeScript files remain in file metadata but are not statically parsed in this phase.

## JSON response

The analysis response includes both per-file structured data in `codeFiles` and flattened arrays for graph/query consumers:

```json
{
  "codeFiles": [
    {
      "relativePath": "auth.py",
      "language": "Python",
      "classes": [],
      "functions": [
        {
          "name": "login",
          "qualifiedName": "login",
          "functionType": "FUNCTION",
          "parameters": ["username", "password"],
          "isAsync": false
        }
      ],
      "imports": [
        {
          "importValue": "jwt",
          "importType": "MODULE",
          "resolvedPath": null
        }
      ],
      "methodCalls": [],
      "inheritance": [],
      "apiRoutes": [],
      "moduleDependencies": [
        {
          "sourcePath": "auth.py",
          "targetModule": "jwt",
          "resolvedPath": null,
          "dependencyType": "EXTERNAL"
        }
      ]
    }
  ],
  "classes": [],
  "functions": [],
  "imports": [],
  "methodCalls": [],
  "inheritance": [],
  "apiRoutes": [],
  "moduleDependencies": []
}
```

Line numbers, paths, and the remaining metadata fields are included in actual responses.

## Endpoints

- `POST /internal/v1/analysis-jobs` runs extraction inside the analysis service.
- `GET /api/v1/repositories/{repositoryId}/analysis` returns the latest stored Phase 3 JSON through the backend.

Run the stack from `infra` with `docker compose up --build`, submit a repository, wait for the job to reach `COMPLETED`, then call the analysis endpoint with its `repositoryId`.
