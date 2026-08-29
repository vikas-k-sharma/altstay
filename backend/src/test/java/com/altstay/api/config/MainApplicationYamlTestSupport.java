package com.altstay.api.config;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code src/main/resources/application.yaml} - the production file - into an
 * {@link org.springframework.core.env.Environment} a test can assert against.
 *
 * <p>This exists because of the trap recorded in CLAUDE.md and in phase-4-foundations.md §3.7
 * finding 3: {@code src/test/resources/application.yaml} <strong>replaces</strong> the main file on
 * the test classpath rather than merging with it, so any setting that lives only in the main file is
 * covered by no test in this repo. {@code new ClassPathResource("application.yaml")} resolves to the
 * test file for the same reason, hence the filesystem path - Surefire runs with the module directory
 * as its working directory.
 *
 * <p>Use this for configuration that must hold <em>in production</em> and be asserted. Configuration
 * that must hold in both places and cannot be read from YAML belongs in a bean instead, the way
 * {@code CookieSameSiteSupplier} does.
 */
public final class MainApplicationYamlTestSupport {

    private MainApplicationYamlTestSupport() {
    }

    public static List<PropertySource<?>> load() throws IOException {
        FileSystemResource main = new FileSystemResource("src/main/resources/application.yaml");
        if (!main.exists()) {
            throw new IllegalStateException(
                    "main application.yaml not readable from the module directory: " + main.getPath());
        }
        return new YamlPropertySourceLoader().load("main-application-yaml", main);
    }

    /** The main file's properties, with {@code environmentVariables} available to placeholders. */
    public static StandardEnvironment environmentWith(Map<String, Object> environmentVariables) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources()
                .addFirst(new MapPropertySource("test-environment-variables", environmentVariables));
        for (PropertySource<?> source : load()) {
            environment.getPropertySources().addLast(source);
        }
        return environment;
    }
}
