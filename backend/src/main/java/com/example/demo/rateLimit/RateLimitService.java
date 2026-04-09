package com.example.demo.rateLimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    static final int NORMAL_WINDOW    = 1;    // 1초
    static final int NORMAL_LIMIT     = 20;   // 초당 20회
    static final int TICKETING_WINDOW = 1;    // 1초
    static final int TICKETING_LIMIT  = 200;  // 초당 200회
    static final int CALL_WINDOW      = 10;   // 10초
    static final int CALL_LIMIT       = 10;   // 10초에 10회
    private static final String KEY_PREFIX = "rl:";

    private static final DefaultRedisScript<Long> INCR_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            elseif count > tonumber(ARGV[2]) then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            end
            return count
            """, Long.class);

    public RateLimitDto.CheckResult checkAndIncrement(String endpoint, String ip) {
        int limit  = resolveLimit(endpoint);
        int window = resolveWindow(endpoint);
        String key = buildKey(endpoint, ip);

        Long count = redisTemplate.execute(INCR_SCRIPT, List.of(key), String.valueOf(window), String.valueOf(limit));
        if (count == null) count = 1L;

        long remaining = Math.max(0, limit - count);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        long resetIn = (ttl != null && ttl > 0) ? ttl : window;

        return new RateLimitDto.CheckResult(count, limit, remaining, resetIn);
    }

    public RateLimitDto.InfoResponse getInfo(String ip) {
        try {
            return new RateLimitDto.InfoResponse(
                    buildInfo("normal", ip, NORMAL_LIMIT),
                    buildInfo("api", ip, CALL_LIMIT),
                    true
            );
        } catch (Exception e) {
            return new RateLimitDto.InfoResponse(null, null, false);
        }
    }

    public void reset(String ip) {
        redisTemplate.delete(buildKey("normal", ip));
        redisTemplate.delete(buildKey("api", ip));
    }

    private RateLimitDto.EndpointInfo buildInfo(String endpoint, String ip, int limit) {
        String key = buildKey(endpoint, ip);
        String val = redisTemplate.opsForValue().get(key);
        int count = val != null ? Integer.parseInt(val) : 0;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        long resetIn = (ttl != null && ttl > 0) ? ttl : 0;
        return new RateLimitDto.EndpointInfo(limit, Math.max(0, limit - count), resetIn);
    }

    String buildKey(String endpoint, String ip) {
        return KEY_PREFIX + endpoint + ":" + ip;
    }

    int resolveLimit(String endpoint) {
        if ("api".equals(endpoint))       return CALL_LIMIT;
        if ("ticketing".equals(endpoint)) return TICKETING_LIMIT;
        return NORMAL_LIMIT;
    }

    int resolveWindow(String endpoint) {
        if ("api".equals(endpoint))       return CALL_WINDOW;
        if ("ticketing".equals(endpoint)) return TICKETING_WINDOW;
        return NORMAL_WINDOW;
    }
}
