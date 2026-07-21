package com.githubgraph.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AppPropertiesBindingTest {

    @Test
    void bindsCloneConfigurationToRenamedRecordComponent() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "github-graph.clone.root", "/workspace/repos",
                "github-graph.clone.timeout-seconds", "120",
                "github-graph.clone.max-size-bytes", "262144000",
                "github-graph.clone.poll-interval-millis", "250"
        ));

        AppProperties properties = new Binder(source)
                .bind("github-graph", Bindable.of(AppProperties.class))
                .orElseThrow(() -> new AssertionError("AppProperties did not bind"));

        assertNotNull(properties.cloneProperties());
        assertEquals("/workspace/repos", properties.cloneProperties().root());
        assertEquals(120, properties.cloneProperties().timeoutSeconds());
        assertEquals(262144000, properties.cloneProperties().maxSizeBytes());
        assertEquals(250, properties.cloneProperties().pollIntervalMillis());
    }
}
