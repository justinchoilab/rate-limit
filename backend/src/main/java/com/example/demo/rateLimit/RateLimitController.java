package com.example.demo.rateLimit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rate-limit")
@RequiredArgsConstructor
public class RateLimitController {

    private final RateLimitService rateLimitService;

    @GetMapping("/call")
    public RateLimitDto.PingResponse call() {
        return new RateLimitDto.PingResponse("ok", System.currentTimeMillis());
    }

    @GetMapping("/info")
    public RateLimitDto.InfoResponse info(HttpServletRequest request) {
        return rateLimitService.getInfo(RateLimitInterceptor.getClientIp(request));
    }

    @PostMapping("/reset")
    public void reset(HttpServletRequest request) {
        rateLimitService.reset(RateLimitInterceptor.getClientIp(request));
    }
}
