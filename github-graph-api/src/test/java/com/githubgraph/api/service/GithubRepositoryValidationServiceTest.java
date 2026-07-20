package com.githubgraph.api.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.githubgraph.api.config.AppProperties;
import com.githubgraph.api.exception.ValidationException;
import com.githubgraph.api.util.GithubRepoRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GithubRepositoryValidationServiceTest {

    private GithubRepositoryValidationService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new GithubRepositoryValidationService(builder, properties());
    }

    @Test
    void acceptsValidPublicRepository() {
        server.expect(requestTo("https://api.github.test/repos/public/project"))
                .andRespond(withSuccess("{\"private\":false}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> service.verifyPublic(repository("public", "project")));
        server.verify();
    }

    @Test
    void rejectsPrivateRepository() {
        server.expect(requestTo("https://api.github.test/repos/private/project"))
                .andRespond(withSuccess("{\"private\":true}", MediaType.APPLICATION_JSON));

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.verifyPublic(repository("private", "project"))
        );

        assertEquals("PRIVATE_REPOSITORY", exception.getCode());
        server.verify();
    }

    @Test
    void rejectsMissingOrInaccessibleRepository() {
        server.expect(requestTo("https://api.github.test/repos/missing/project"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.verifyPublic(repository("missing", "project"))
        );

        assertEquals("REPOSITORY_NOT_FOUND", exception.getCode());
        server.verify();
    }

    private GithubRepoRef repository(String owner, String name) {
        return new GithubRepoRef("https://github.com/" + owner + "/" + name, owner, name);
    }

    private AppProperties properties() {
        return new AppProperties(
                new AppProperties.Analysis("http://analysis"),
                new AppProperties.CloneProperties("/tmp/repos", 120, 1024, 100),
                new AppProperties.Github("https://api.github.test", ""),
                new AppProperties.Neo4jInitialization(3, 1, 4)
        );
    }
}
