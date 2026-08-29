package com.altstay.api.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("Caller-supplied X-Correlation-Id is preserved in MDC and echoed on response header")
    void callerSuppliedCorrelationId_isHonouredAndEchoed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "custom-corr-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcValueInsideChain = new AtomicReference<>();
        MockFilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcValueInsideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        };

        filter.doFilter(request, response, filterChain);

        assertThat(mdcValueInsideChain.get()).isEqualTo("custom-corr-1234");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("custom-corr-1234");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Generated correlationId is created and echoed when header is absent")
    void absentCorrelationId_generatesNewAndEchoes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcValueInsideChain = new AtomicReference<>();
        MockFilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcValueInsideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        };

        filter.doFilter(request, response, filterChain);

        assertThat(mdcValueInsideChain.get()).isNotBlank();
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(mdcValueInsideChain.get());
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("MDC is cleared in finally block even when handler throws exception")
    void exceptionInChain_stillClearsMdc() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new RuntimeException("Simulated filter chain explosion");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated filter chain explosion");

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).as("MDC must be cleared after exception").isNull();
    }
}
