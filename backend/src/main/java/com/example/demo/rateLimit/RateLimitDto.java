package com.example.demo.rateLimit;

public class RateLimitDto {
    public record EndpointInfo(int limit, int remaining, long resetIn) {}
    public record InfoResponse(EndpointInfo normal, EndpointInfo api, boolean redisAvailable) {}
    public record PingResponse(String message, long timestamp) {}
    public record CheckResult(long count, int limit, long remaining, long resetIn) {}
}
