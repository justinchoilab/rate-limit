package com.example.demo.rateLimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock RateLimitService rateLimitService;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(rateLimitService);
    }


    @Test
    void preHandle_optionsRequest_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/rate-limit/call");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        then(rateLimitService).shouldHaveNoInteractions();
    }


    @Test
    void preHandle_withinLimit_setsHeadersAndReturnsTrue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/normal");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RateLimitDto.CheckResult result = new RateLimitDto.CheckResult(5L, 20, 15L, 1L);
        given(rateLimitService.checkAndIncrement(anyString(), anyString())).willReturn(result);

        boolean pass = interceptor.preHandle(request, response, new Object());

        assertThat(pass).isTrue();
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("20");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("15");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("1");
    }


    @Test
    void preHandle_exceedsLimit_returns429AndFalse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/normal");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // count(21) > limit(20)
        RateLimitDto.CheckResult result = new RateLimitDto.CheckResult(21L, 20, 0L, 1L);
        given(rateLimitService.checkAndIncrement(anyString(), anyString())).willReturn(result);

        boolean pass = interceptor.preHandle(request, response, new Object());

        assertThat(pass).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getContentType()).contains("application/json");
    }

    @Test
    void preHandle_exceedsLimit_responseBodyContainsErrorJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/normal");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RateLimitDto.CheckResult result = new RateLimitDto.CheckResult(25L, 20, 0L, 5L);
        given(rateLimitService.checkAndIncrement(anyString(), anyString())).willReturn(result);

        interceptor.preHandle(request, response, new Object());

        String body = response.getContentAsString();
        assertThat(body).contains("TOO_MANY_REQUESTS");
        assertThat(body).contains("retryAfter");
    }


    @Test
    void getClientIp_noForwardedHeader_returnsRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        String ip = RateLimitInterceptor.getClientIp(request);

        assertThat(ip).isEqualTo("192.168.1.100");
    }

    @Test
    void getClientIp_withXForwardedFor_returnsFirstIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.1, 172.16.0.1, 192.168.1.1");

        String ip = RateLimitInterceptor.getClientIp(request);

        assertThat(ip).isEqualTo("10.0.0.1");
    }

    @Test
    void getClientIp_singleIpInForwardedFor_returnsThatIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.5");

        String ip = RateLimitInterceptor.getClientIp(request);

        assertThat(ip).isEqualTo("203.0.113.5");
    }

    @Test
    void getClientIp_blankForwardedFor_returnsRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "   ");
        request.setRemoteAddr("10.10.10.10");

        String ip = RateLimitInterceptor.getClientIp(request);

        assertThat(ip).isEqualTo("10.10.10.10");
    }


    @Test
    void extractEndpoint_rateLimitCallUri_returnsApi() throws Exception {
        String endpoint = invokeExtractEndpoint("/api/rate-limit/call");
        assertThat(endpoint).isEqualTo("api");
    }

    @Test
    void extractEndpoint_ticketingUri_returnsTicketing() throws Exception {
        String endpoint = invokeExtractEndpoint("/api/ticketing/reserve");
        assertThat(endpoint).isEqualTo("ticketing");
    }

    @Test
    void extractEndpoint_normalUri_returnsNormal() throws Exception {
        String endpoint = invokeExtractEndpoint("/api/posts");
        assertThat(endpoint).isEqualTo("normal");
    }

    @Test
    void extractEndpoint_rootUri_returnsNormal() throws Exception {
        String endpoint = invokeExtractEndpoint("/");
        assertThat(endpoint).isEqualTo("normal");
    }

    private String invokeExtractEndpoint(String uri) throws Exception {
        Method m = RateLimitInterceptor.class.getDeclaredMethod("extractEndpoint", String.class);
        m.setAccessible(true);
        return (String) m.invoke(interceptor, uri);
    }
}
