package com.example.demo.rateLimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.util.concurrent.TimeUnit;

import org.mockito.ArgumentMatchers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService(redisTemplate);
    }


    @Test
    void buildKey_combinesEndpointAndIp() {
        String key = service.buildKey("normal", "127.0.0.1");
        assertThat(key).isEqualTo("rl:normal:127.0.0.1");
    }

    @Test
    void buildKey_apiEndpoint() {
        String key = service.buildKey("api", "10.0.0.1");
        assertThat(key).isEqualTo("rl:api:10.0.0.1");
    }


    @Test
    void resolveLimit_api_returns10() {
        assertThat(service.resolveLimit("api")).isEqualTo(RateLimitService.CALL_LIMIT);
    }

    @Test
    void resolveLimit_ticketing_returns200() {
        assertThat(service.resolveLimit("ticketing")).isEqualTo(RateLimitService.TICKETING_LIMIT);
    }

    @Test
    void resolveLimit_normal_returns20() {
        assertThat(service.resolveLimit("normal")).isEqualTo(RateLimitService.NORMAL_LIMIT);
    }

    @Test
    void resolveLimit_unknown_returnsNormalLimit() {
        assertThat(service.resolveLimit("other")).isEqualTo(RateLimitService.NORMAL_LIMIT);
    }


    @Test
    void resolveWindow_api_returnsCallWindow() {
        assertThat(service.resolveWindow("api")).isEqualTo(RateLimitService.CALL_WINDOW);
    }

    @Test
    void resolveWindow_ticketing_returns1() {
        assertThat(service.resolveWindow("ticketing")).isEqualTo(RateLimitService.TICKETING_WINDOW);
    }

    @Test
    void resolveWindow_normal_returns1() {
        assertThat(service.resolveWindow("normal")).isEqualTo(RateLimitService.NORMAL_WINDOW);
    }


    @Test
    void checkAndIncrement_firstRequest_count1_remaining19() {
        given(redisTemplate.<Long>execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(), any())).willReturn(1L);
        given(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).willReturn(1L);

        RateLimitDto.CheckResult result = service.checkAndIncrement("normal", "127.0.0.1");

        assertThat(result.count()).isEqualTo(1L);
        assertThat(result.limit()).isEqualTo(RateLimitService.NORMAL_LIMIT);
        assertThat(result.remaining()).isEqualTo(RateLimitService.NORMAL_LIMIT - 1);
    }

    @Test
    void checkAndIncrement_countEqualsLimit_remaining0() {
        long count = RateLimitService.NORMAL_LIMIT;
        given(redisTemplate.<Long>execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(), any())).willReturn(count);
        given(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).willReturn(1L);

        RateLimitDto.CheckResult result = service.checkAndIncrement("normal", "127.0.0.1");

        assertThat(result.remaining()).isEqualTo(0L);
    }

    @Test
    void checkAndIncrement_countExceedsLimit_remainingStaysZero() {
        long count = RateLimitService.NORMAL_LIMIT + 5L;
        given(redisTemplate.<Long>execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(), any())).willReturn(count);
        given(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).willReturn(1L);

        RateLimitDto.CheckResult result = service.checkAndIncrement("normal", "127.0.0.1");

        assertThat(result.remaining()).isEqualTo(0L);
        assertThat(result.count()).isGreaterThan(result.limit());
    }

    @Test
    void checkAndIncrement_nullCountFromRedis_treatedAs1() {
        given(redisTemplate.<Long>execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(), any())).willReturn(null);
        given(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).willReturn(1L);

        RateLimitDto.CheckResult result = service.checkAndIncrement("normal", "127.0.0.1");

        assertThat(result.count()).isEqualTo(1L);
    }

    @Test
    void checkAndIncrement_negativeTtl_resetInFallsBackToWindow() {
        given(redisTemplate.<Long>execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(), any())).willReturn(1L);
        given(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).willReturn(-1L);

        RateLimitDto.CheckResult result = service.checkAndIncrement("normal", "127.0.0.1");

        assertThat(result.resetIn()).isEqualTo(RateLimitService.NORMAL_WINDOW);
    }


    @Test
    void getInfo_returnsNormalAndApiInfo() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(anyString())).willReturn("5");
        given(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).willReturn(30L);

        RateLimitDto.InfoResponse info = service.getInfo("127.0.0.1");

        assertThat(info.normal()).isNotNull();
        assertThat(info.api()).isNotNull();
        assertThat(info.normal().limit()).isEqualTo(RateLimitService.NORMAL_LIMIT);
        assertThat(info.api().limit()).isEqualTo(RateLimitService.CALL_LIMIT);
        assertThat(info.normal().remaining()).isEqualTo(RateLimitService.NORMAL_LIMIT - 5);
        assertThat(info.api().remaining()).isEqualTo(RateLimitService.CALL_LIMIT - 5);
    }

    @Test
    void getInfo_noRedisValue_remainingEqualsLimit() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(anyString())).willReturn(null);
        given(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).willReturn(0L);

        RateLimitDto.InfoResponse info = service.getInfo("127.0.0.1");

        assertThat(info.normal().remaining()).isEqualTo(RateLimitService.NORMAL_LIMIT);
        assertThat(info.api().remaining()).isEqualTo(RateLimitService.CALL_LIMIT);
    }


    @Test
    void reset_deletesBothNormalAndApiKeys() {
        service.reset("127.0.0.1");

        then(redisTemplate).should().delete("rl:normal:127.0.0.1");
        then(redisTemplate).should().delete("rl:api:127.0.0.1");
        then(redisTemplate).should(times(2)).delete(anyString());
    }
}
