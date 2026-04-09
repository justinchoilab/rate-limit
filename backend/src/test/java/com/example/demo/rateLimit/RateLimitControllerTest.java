package com.example.demo.rateLimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RateLimitController.class)
class RateLimitControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean RateLimitService rateLimitService;
    @MockBean RateLimitInterceptor rateLimitInterceptor;


    @Test
    void call_returns200WithPingResponse() throws Exception {
        given(rateLimitInterceptor.preHandle(any(), any(), any())).willReturn(true);

        mockMvc.perform(get("/api/rate-limit/call"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.timestamp").isNumber());
    }


    @Test
    void info_returns200WithInfoResponse() throws Exception {
        RateLimitDto.InfoResponse infoResponse = new RateLimitDto.InfoResponse(
                new RateLimitDto.EndpointInfo(20, 15, 1L),
                new RateLimitDto.EndpointInfo(10, 8, 30L),
                true
        );
        given(rateLimitService.getInfo(anyString())).willReturn(infoResponse);

        mockMvc.perform(get("/api/rate-limit/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.normal.limit").value(20))
                .andExpect(jsonPath("$.normal.remaining").value(15))
                .andExpect(jsonPath("$.api.limit").value(10))
                .andExpect(jsonPath("$.api.remaining").value(8));
    }


    @Test
    void reset_returns200() throws Exception {
        willDoNothing().given(rateLimitService).reset(anyString());

        mockMvc.perform(post("/api/rate-limit/reset"))
                .andExpect(status().isOk());
    }
}
