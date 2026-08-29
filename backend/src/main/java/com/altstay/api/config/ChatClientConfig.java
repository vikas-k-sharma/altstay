package com.altstay.api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryTemplate retryTemplate() {
        return new RetryTemplate(RetryPolicy.builder().maxRetries(0).build());
    }

    @Bean
    @ConditionalOnClass(Client.class)
    @ConditionalOnMissingBean
    public Client googleGenAiClient(
            GoogleGenAiConnectionProperties connectionProperties,
            ConciergeProperties conciergeProperties,
            @Value("${spring.ai.google.genai.base-url:}") String baseUrl) throws IOException {

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(conciergeProperties.modelConnectTimeout())
                .readTimeout(conciergeProperties.modelReadTimeout())
                .writeTimeout(conciergeProperties.modelReadTimeout())
                .callTimeout(conciergeProperties.modelReadTimeout())
                .retryOnConnectionFailure(false)
                .build();

        Client.Builder builder = Client.builder();

        if (connectionProperties.isVertexAi()) {
            if (StringUtils.hasText(connectionProperties.getProjectId())) {
                builder.project(connectionProperties.getProjectId());
            }
            if (StringUtils.hasText(connectionProperties.getLocation())) {
                builder.location(connectionProperties.getLocation());
            }
            builder.vertexAI(true);
            if (connectionProperties.getCredentialsUri() != null) {
                try (var is = connectionProperties.getCredentialsUri().getInputStream()) {
                    builder.credentials(GoogleCredentials.fromStream(is));
                }
            }
        } else if (StringUtils.hasText(connectionProperties.getApiKey())) {
            builder.apiKey(connectionProperties.getApiKey());
        } else {
            // No key configured. The context still has to load - both test suites boot the app without
            // GOOGLE_API_KEY - so we register a placeholder rather than failing the bean. Warn loudly:
            // silently accepting a missing credential is how Phase 1's hardcoded-key finding happened.
            log.warn("No Google GenAI API key configured. The context will start, but every model call "
                    + "will fail. Set GOOGLE_API_KEY before serving traffic.");
            builder.apiKey("placeholder-key-no-credential-configured");
        }

        ClientOptions clientOptions = ClientOptions.builder()
                .customHttpClient(okHttpClient)
                .build();
        builder.clientOptions(clientOptions);

        // The SDK ALWAYS installs a RetryInterceptor into the OkHttp chain (ApiClient#createHttpClient),
        // defaulting to attempts=5 with jittered exponential backoff. Left alone it multiplies the read
        // timeout by ~5x plus backoff, so a 2s read timeout took 13-21s to surface. Retry policy for this
        // service is "don't" - the BFF's budget is 25s and a guest is waiting. Note this is a different
        // layer from `spring.ai.retry.max-attempts`, which does not reach the SDK's interceptor at all.
        HttpOptions.Builder httpOptionsBuilder = HttpOptions.builder()
                .retryOptions(HttpRetryOptions.builder().attempts(1).build());

        // Deliberately NOT setting HttpOptions.timeout(): when a customHttpClient is supplied, the SDK
        // takes it via newBuilder() and never applies HttpOptions.timeout to it. The OkHttp timeouts
        // above are the ones that take effect. Setting it here would read as configuration that works.

        if (StringUtils.hasText(baseUrl)) {
            httpOptionsBuilder.baseUrl(baseUrl);
            httpOptionsBuilder.apiVersion("v1beta");
        }

        builder.httpOptions(httpOptionsBuilder.build());

        return builder.build();
    }
}
