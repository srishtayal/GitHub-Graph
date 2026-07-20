package com.githubgraph.api.util;

import com.githubgraph.api.exception.ValidationException;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.stereotype.Component;

@Component
public class GithubUrlValidator {

    public GithubRepoRef validateAndNormalize(String githubUrl) {
        URI uri;
        try {
            uri = new URI(githubUrl.trim());
        } catch (URISyntaxException exception) {
            throw new ValidationException("INVALID_GITHUB_URL", "Invalid GitHub URL");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ValidationException("UNSUPPORTED_GITHUB_URL", "Only https GitHub URLs are supported");
        }

        if (!"github.com".equalsIgnoreCase(uri.getHost())) {
            throw new ValidationException("UNSUPPORTED_GITHUB_URL", "Only github.com repository URLs are supported");
        }

        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            throw new ValidationException("INVALID_GITHUB_URL", "Repository path is missing");
        }

        String sanitized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (sanitized.endsWith(".git")) {
            sanitized = sanitized.substring(0, sanitized.length() - 4);
        }

        String[] segments = sanitized.split("/");
        if (segments.length != 3 || segments[1].isBlank() || segments[2].isBlank()) {
            throw new ValidationException(
                    "INVALID_GITHUB_URL",
                    "GitHub URL must be in the form https://github.com/{owner}/{repo}"
            );
        }

        String normalizedUrl = "https://github.com/%s/%s".formatted(segments[1], segments[2]);
        return new GithubRepoRef(normalizedUrl, segments[1], segments[2]);
    }
}
