package com.altstay.api.chat;

import com.altstay.api.chat.dto.ChatRequest;
import com.altstay.api.chat.dto.ChatResponse;
import com.altstay.api.chat.dto.ChatTurn;
import com.altstay.api.chat.dto.Role;
import com.altstay.api.chat.dto.TokenUsage;
import com.altstay.api.common.GlobalExceptionHandler;
import com.altstay.api.common.ModelUnavailableException;
import com.altstay.api.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private com.altstay.api.ratelimit.RateLimiter rateLimiter;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(rateLimiter.tryConsume(any(), any())).thenReturn(com.altstay.api.ratelimit.ConsumptionResult.allow());
    }

    @Test
    @DisplayName("Happy path returns 200 OK with expected JSON response structure")
    void happyPathReturns200AndJsonShape() throws Exception {
        ChatResponse expectedResponse = new ChatResponse(
                "Check-in is from 2 PM.",
                false,
                "gemini-2.5-flash",
                new TokenUsage(150, 20, 170),
                350L
        );
        when(chatService.answer(any(ChatRequest.class))).thenReturn(expectedResponse);

        ChatRequest request = new ChatRequest(
                "Check-in is 2 PM.",
                List.of(new ChatTurn(Role.USER, "hello"), new ChatTurn(Role.ASSISTANT, "hi")),
                "what is check-in time?"
        );

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reply").value("Check-in is from 2 PM."))
                .andExpect(jsonPath("$.escalated").value(false))
                .andExpect(jsonPath("$.model").value("gemini-2.5-flash"))
                .andExpect(jsonPath("$.usage.promptTokens").value(150))
                .andExpect(jsonPath("$.usage.completionTokens").value(20))
                .andExpect(jsonPath("$.usage.totalTokens").value(170))
                .andExpect(jsonPath("$.latencyMs").value(350));
    }

    @Test
    @DisplayName("Blank message returns 400 Bad Request application/problem+json identifying the field")
    void blankMessageReturns400ProblemDetail() throws Exception {
        ChatRequest request = new ChatRequest(
                "Knowledge base content here.",
                List.of(),
                ""
        );

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://api.altstay.com/errors/validation-error"))
                .andExpect(jsonPath("$.title").value("Validation Failure"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.message").exists());
    }

    @Test
    @DisplayName("History exceeding 200 turns returns 400 Bad Request application/problem+json")
    void historyExceedingLimitReturns400ProblemDetail() throws Exception {
        List<ChatTurn> excessiveTurns = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            excessiveTurns.add(new ChatTurn(Role.USER, "turn " + i));
        }

        ChatRequest request = new ChatRequest(
                "AltStay Hostel",
                "Knowledge base content.",
                excessiveTurns,
                "valid message"
        );

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://api.altstay.com/errors/validation-error"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.history").exists());
    }

    @Test
    @DisplayName("ModelUnavailableException returns 502 Bad Gateway with no stack trace leakage")
    void modelUnavailableReturns502ProblemDetail() throws Exception {
        when(chatService.answer(any(ChatRequest.class)))
                .thenThrow(new ModelUnavailableException("Upstream model timeout"));

        ChatRequest request = new ChatRequest(
                "Knowledge base content.",
                List.of(),
                "what time is check-out?"
        );

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://api.altstay.com/errors/model-unavailable"))
                .andExpect(jsonPath("$.title").value("Model Unavailable"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("Standing guard (§0.1 constraint 1): POST /api/v1/chat succeeds with NO credentials or session")
    void anonymousPostChatSucceedsWithNoCredentials() throws Exception {
        when(rateLimiter.tryConsume(any(), any())).thenReturn(com.altstay.api.ratelimit.ConsumptionResult.allow());

        ChatResponse expectedResponse = new ChatResponse(
                "Check-in is from 2 PM.",
                false,
                "gemini-2.5-flash",
                new TokenUsage(150, 20, 170),
                350L
        );
        when(chatService.answer(any(ChatRequest.class))).thenReturn(expectedResponse);

        ChatRequest request = new ChatRequest(
                "Check-in is 2 PM.",
                List.of(),
                "what time is check-in?"
        );

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reply").value("Check-in is from 2 PM."))
                .andExpect(jsonPath("$.escalated").value(false));
    }

    @Test
    @DisplayName("Rate limit exceeded returns 429 Too Many Requests with Retry-After header and distinct copy")
    void rateLimitExceeded_returns429ProblemDetailWithRetryAfter() throws Exception {
        when(rateLimiter.tryConsume(any(), any()))
                .thenReturn(com.altstay.api.ratelimit.ConsumptionResult.reject(6));

        ChatRequest request = new ChatRequest(
                "Check-in is 2 PM.",
                List.of(),
                "what time is check-in?"
        );

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-altstay-session", "test-session-rate-limit")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "6"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://api.altstay.com/errors/rate-limited"))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("One moment — catching up."));
    }

    @Test
    @DisplayName("Upstream 429 (ModelRateLimitedException) returns 503 Service Unavailable ('The concierge is paused right now')")
    void upstreamRateLimit_returns503ProblemDetail() throws Exception {
        when(rateLimiter.tryConsume(any(), any())).thenReturn(com.altstay.api.ratelimit.ConsumptionResult.allow());
        when(chatService.answer(any(ChatRequest.class)))
                .thenThrow(new com.altstay.api.common.ModelRateLimitedException("Quota exhausted"));

        ChatRequest request = new ChatRequest(
                "Check-in is 2 PM.",
                List.of(),
                "what time is check-in?"
        );

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://api.altstay.com/errors/model-rate-limited"))
                .andExpect(jsonPath("$.title").value("Model Rate Limited"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("The upstream AI model is rate limited or quota exhausted. Please try again later."));
    }

    @Test
    @DisplayName("x-altstay-session header never reaches CurrentTenantHolder and cannot escalate tenant")
    void sessionHeader_neverReachesCurrentTenantHolder() throws Exception {
        when(rateLimiter.tryConsume(any(), any())).thenReturn(com.altstay.api.ratelimit.ConsumptionResult.allow());
        when(chatService.answer(any(ChatRequest.class))).thenAnswer(invocation -> {
            // Assert that inside the controller invocation, CurrentTenantHolder is empty
            org.assertj.core.api.Assertions.assertThat(com.altstay.api.tenancy.CurrentTenantHolder.get()).isEmpty();
            return new ChatResponse("Check-in is 2 PM.", false, "model", new TokenUsage(10, 10, 20), 100L);
        });

        ChatRequest request = new ChatRequest(
                "Check-in is 2 PM.",
                List.of(),
                "what time is check-in?"
        );

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-altstay-session", "session-attacker-controlled-tenant-attempt")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
