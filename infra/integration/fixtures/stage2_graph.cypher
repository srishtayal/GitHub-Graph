CREATE
  (repoA:CodeGraphNode {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '22222222-2222-2222-2222-222222222222',
    id: 'repo:fixture',
    type: 'repo',
    label: 'stage2-integration-fixture'
  }),
  (fileA:CodeGraphNode {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '22222222-2222-2222-2222-222222222222',
    id: 'file:service',
    type: 'file',
    label: 'service.py',
    relativePath: 'app/service.py'
  }),
  (entryA:CodeGraphNode {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '22222222-2222-2222-2222-222222222222',
    id: 'function:entry',
    type: 'function',
    label: 'entry',
    relativePath: 'app/service.py',
    name: 'entry',
    qualifiedName: 'service.entry',
    startLine: 1,
    endLine: 5
  }),
  (rootA:CodeGraphNode {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '22222222-2222-2222-2222-222222222222',
    id: 'function:root',
    type: 'function',
    label: 'root',
    relativePath: 'app/service.py',
    name: 'root',
    qualifiedName: 'service.root',
    startLine: 8,
    endLine: 12
  }),
  (repoA)-[:CONTAINS {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '22222222-2222-2222-2222-222222222222',
    edgeId: 'edge:a:repo-file'
  }]->(fileA),
  (fileA)-[:CONTAINS {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '22222222-2222-2222-2222-222222222222',
    edgeId: 'edge:a:file-entry'
  }]->(entryA),
  (fileA)-[:CONTAINS {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '22222222-2222-2222-2222-222222222222',
    edgeId: 'edge:a:file-root'
  }]->(rootA),
  (entryA)-[:CALLS {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '22222222-2222-2222-2222-222222222222',
    edgeId: 'edge:a:entry-root'
  }]->(rootA);

CREATE
  (repoB:CodeGraphNode {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '33333333-3333-3333-3333-333333333333',
    id: 'repo:fixture',
    type: 'repo',
    label: 'stage2-integration-fixture'
  }),
  (fileB:CodeGraphNode {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '33333333-3333-3333-3333-333333333333',
    id: 'file:service',
    type: 'file',
    label: 'service.py',
    relativePath: 'app/service.py'
  }),
  (entryB:CodeGraphNode {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '33333333-3333-3333-3333-333333333333',
    id: 'function:entry',
    type: 'function',
    label: 'entry',
    relativePath: 'app/service.py',
    name: 'entry',
    qualifiedName: 'service.entry',
    startLine: 1,
    endLine: 5
  }),
  (rootB:CodeGraphNode {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '33333333-3333-3333-3333-333333333333',
    id: 'function:root',
    type: 'function',
    label: 'root',
    relativePath: 'app/service.py',
    name: 'root',
    qualifiedName: 'service.root',
    startLine: 8,
    endLine: 12
  }),
  (repoB)-[:CONTAINS {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '33333333-3333-3333-3333-333333333333',
    edgeId: 'edge:b:repo-file'
  }]->(fileB),
  (fileB)-[:CONTAINS {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '33333333-3333-3333-3333-333333333333',
    edgeId: 'edge:b:file-entry'
  }]->(entryB),
  (fileB)-[:CONTAINS {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '33333333-3333-3333-3333-333333333333',
    edgeId: 'edge:b:file-root'
  }]->(rootB),
  (entryB)-[:CALLS {
    repositoryId: '11111111-1111-1111-1111-111111111111',
    snapshotId: '33333333-3333-3333-3333-333333333333',
    edgeId: 'edge:b:entry-root'
  }]->(rootB);
