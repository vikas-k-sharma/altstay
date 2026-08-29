package com.altstay.api.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.altstay.api.config.MainApplicationYamlTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Track E's first definition-of-done item: <em>"Logs are JSON when {@code ALTSTAY_LOG_FORMAT=ecs}
 * and human-readable otherwise, and every line of a request carries the same correlationId."</em>
 *
 * <p>The previous version of this class asserted that {@code MDC.put} followed by {@code MDC.get}
 * returned what was put - SLF4J's behaviour, not this application's - and referenced neither
 * {@code ALTSTAY_LOG_FORMAT} nor any log output. It proved nothing and the box above it was ticked
 * anyway. These two tests exercise the two halves that actually matter: the encoder really emits
 * JSON carrying the MDC correlation id, and the main {@code application.yaml} really maps the
 * environment variable onto Boot's structured-logging property.
 *
 * <p>Reading the MAIN {@code application.yaml} explicitly is deliberate. The test classpath's
 * {@code application.yaml} <em>replaces</em> it rather than merging, so a property that lives only
 * in the main file is otherwise invisible to every test in this repo.
 */
class StructuredLoggingTest {

    private static final String LOG_FORMAT_PROPERTY = "logging.structured.format.console";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("With the ecs format, a log line is emitted as JSON carrying the MDC correlationId")
    void ecsFormat_emitsJsonCarryingCorrelationId() throws IOException {
        LoggerContext loggerContext = new LoggerContext();
        loggerContext.putObject(Environment.class.getName(), new StandardEnvironment());

        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(loggerContext);
        encoder.setFormat("ecs");
        encoder.start();

        ch.qos.logback.classic.Logger logger = loggerContext.getLogger("com.altstay.api.chat.ChatService");
        LoggingEvent event = new LoggingEvent(
                ch.qos.logback.classic.Logger.FQCN, logger, Level.INFO, "Chat call completed", null, null);
        event.setMDCPropertyMap(Map.of(CorrelationIdFilter.MDC_KEY, "corr-test-1234"));

        String encoded = new String(encoder.encode(event), StandardCharsets.UTF_8);

        JsonNode json = objectMapper.readTree(encoded);
        assertThat(json.isObject())
                .as("ecs format must produce a JSON object, got: %s", encoded)
                .isTrue();
        assertThat(json.path("message").asText()).isEqualTo("Chat call completed");
        assertThat(encoded)
                .as("the MDC correlationId must survive into the structured line")
                .contains("corr-test-1234");

        encoder.stop();
    }

    @Test
    @DisplayName("Main application.yaml maps ALTSTAY_LOG_FORMAT onto logging.structured.format.console")
    void mainApplicationYaml_mapsLogFormatEnvironmentVariable() throws IOException {
        assertThat(resolveLogFormatWith(Map.of("ALTSTAY_LOG_FORMAT", "ecs")))
                .as("ALTSTAY_LOG_FORMAT=ecs must select structured JSON output")
                .isEqualTo("ecs");

        assertThat(resolveLogFormatWith(Map.of()))
                .as("with the variable unset, the format must be empty so local logs stay human-readable")
                .isEmpty();
    }

    private String resolveLogFormatWith(Map<String, Object> environmentVariables) throws IOException {
        String resolved = MainApplicationYamlTestSupport.environmentWith(environmentVariables)
                .getProperty(LOG_FORMAT_PROPERTY);
        return resolved == null ? "" : resolved;
    }
}
