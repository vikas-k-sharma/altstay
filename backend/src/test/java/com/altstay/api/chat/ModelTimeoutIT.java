package com.altstay.api.chat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "altstay.concierge.model-connect-timeout=1s",
        "altstay.concierge.model-read-timeout=2s"
})
class ModelTimeoutIT {

    private static ServerSocket stallServerSocket;
    private static int stallPort;
    private static ExecutorService serverExecutor;
    private static final List<Socket> clientSockets = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean running = true;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startStallServer() throws IOException {
        stallServerSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        stallPort = stallServerSocket.getLocalPort();
        serverExecutor = Executors.newVirtualThreadPerTaskExecutor();

        serverExecutor.submit(() -> {
            while (running && !stallServerSocket.isClosed()) {
                try {
                    Socket socket = stallServerSocket.accept();
                    clientSockets.add(socket);
                    // Accept and never write bytes back, holding the socket open to simulate an upstream stall
                } catch (IOException e) {
                    // Socket closed on teardown
                }
            }
        });
    }

    @AfterAll
    static void stopStallServer() {
        running = false;
        try {
            if (stallServerSocket != null && !stallServerSocket.isClosed()) {
                stallServerSocket.close();
            }
            for (Socket s : clientSockets) {
                try {
                    s.close();
                } catch (IOException ignored) {
                }
            }
            if (serverExecutor != null) {
                serverExecutor.shutdownNow();
            }
        } catch (IOException ignored) {
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.google.genai.base-url", () -> "http://127.0.0.1:" + stallPort);
        registry.add("spring.ai.retry.max-attempts", () -> "0");
        registry.add("altstay.concierge.model-connect-timeout", () -> "1s");
        registry.add("altstay.concierge.model-read-timeout", () -> "2s");
    }

    @Test
    @DisplayName("Stalled upstream connection returns 502 Bad Gateway within readTimeout budget")
    void stalledUpstream_returns502WithinBudget() throws Exception {
        String jsonPayload = """
                {
                    "knowledgeBase": "Dorm beds are 650.",
                    "history": [],
                    "message": "check in time?"
                }
                """;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.body()).contains("Model Unavailable");

        // The bound is deliberately TIGHT around the configured 2s. A loose bound is what let the
        // original defect through: the SDK's RetryInterceptor (default attempts=5, jittered backoff)
        // was multiplying the read timeout to 13-21s, and an assertion of "< 20s" called that a pass.
        // If this starts failing high, the retry interceptor is back - check ChatClientConfig's
        // HttpOptions.retryOptions before relaxing the number.
        assertThat(elapsed)
                .as("502 must arrive at the CONFIGURED 2s read timeout, not a multiple of it")
                .isGreaterThanOrEqualTo(1500)
                .isLessThan(5000);
    }
}
