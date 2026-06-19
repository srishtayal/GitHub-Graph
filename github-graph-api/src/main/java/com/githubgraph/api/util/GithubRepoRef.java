package com.githubgraph.api.util;

public record GithubRepoRef(
        String normalizedUrl,
        String owner,
        String name
) {
}
