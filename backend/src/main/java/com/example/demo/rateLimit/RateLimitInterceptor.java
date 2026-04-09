package com.example.demo.rateLimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String uri = request.getRequestURI();
        String endpoint = extractEndpoint(uri);

        String ip = getClientIp(request);

        try {
            RateLimitDto.CheckResult result = rateLimitService.checkAndIncrement(endpoint, ip);

            response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
            response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetIn()));

            if (result.count() > result.limit()) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(result.resetIn()));
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"요청 한도를 초과했습니다.\",\"retryAfter\":" + result.resetIn() + "}"
                );
                return false;
            }
        } catch (Exception ignored) {
            // Redis 미연결 시 rate limit 없이 통과
        }

        return true;
    }

    private String extractEndpoint(String uri) {
        if (uri.endsWith("/rate-limit/call")) return "api";
        if (uri.contains("/ticketing/"))      return "ticketing";
        return "normal";
    }

    public static String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
