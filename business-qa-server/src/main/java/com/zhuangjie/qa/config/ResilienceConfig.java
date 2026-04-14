package com.zhuangjie.qa.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j 弹性保护配置：为 AI 服务调用提供限流和熔断保护。
 * <ul>
 *   <li>RateLimiter — 限制 API 调用频率，控制 Token 消耗成本</li>
 *   <li>CircuitBreaker — AI 服务不可用时快速失败，避免级联故障</li>
 * </ul>
 */
@Slf4j
@Configuration
public class ResilienceConfig {

    /**
     * 限流器：控制单位时间内的最大请求数。
     * <p>
     * limitForPeriod=10, limitRefreshPeriod=60s 表示"每60秒最多允许10个请求"。
     * timeoutDuration=5s 表示"如果当前周期的配额用完了，最多等5秒看下一个周期能不能拿到配额"。
     * 等不到就抛 RequestNotPermitted。
     */
    @Bean
    public RateLimiter aiRateLimiter(
            @Value("${qa.resilience.rate-limiter.limit-for-period:10}") int limitForPeriod,
            @Value("${qa.resilience.rate-limiter.limit-refresh-period:60s}") Duration limitRefreshPeriod,
            @Value("${qa.resilience.rate-limiter.timeout-duration:5s}") Duration timeoutDuration) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(limitForPeriod)
                .limitRefreshPeriod(limitRefreshPeriod)
                .timeoutDuration(timeoutDuration)
                .build();
        RateLimiter rateLimiter = RateLimiter.of("ai-chat", config);
        log.info("AI RateLimiter created: {} requests per {}", limitForPeriod, limitRefreshPeriod);
        return rateLimiter;
    }

    /**
     * 熔断器：当 AI 服务连续失败时，自动"断开电路"，后续请求直接快速失败。
     * <p>
     * 状态流转：CLOSED(正常) → OPEN(熔断) → HALF_OPEN(试探) → CLOSED(恢复)
     * <p>
     * 例：slidingWindowSize=10, failureRateThreshold=50%
     * → 最近10次调用中如果有5次以上失败，就触发熔断。
     * 熔断期间(30s)所有请求直接拒绝，30s后进入 HALF_OPEN，放行3个请求试探：
     * → 试探成功率达标 → 回到 CLOSED
     * → 试探仍然失败 → 回到 OPEN，再等30s
     */
    @Bean
    public CircuitBreaker aiCircuitBreaker(
            @Value("${qa.resilience.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${qa.resilience.circuit-breaker.wait-duration-in-open-state:30s}") Duration waitDurationInOpenState,
            @Value("${qa.resilience.circuit-breaker.sliding-window-size:10}") int slidingWindowSize,
            @Value("${qa.resilience.circuit-breaker.minimum-number-of-calls:5}") int minimumNumberOfCalls,
            @Value("${qa.resilience.circuit-breaker.permitted-in-half-open:3}") int permittedInHalfOpen) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(waitDurationInOpenState)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .permittedNumberOfCallsInHalfOpenState(permittedInHalfOpen)
                .build();
        CircuitBreaker cb = CircuitBreaker.of("ai-chat", config);
        cb.getEventPublisher().onStateTransition(event ->
                log.warn("AI CircuitBreaker state transition: {}", event.getStateTransition()));
        log.info("AI CircuitBreaker created: failureRate={}%, window={}, waitOpen={}",
                failureRateThreshold, slidingWindowSize, waitDurationInOpenState);
        return cb;
    }

    @Bean
    public LlmRetryAdvisor llmRetryAdvisor(
            @Value("${qa.resilience.retry.max-retries:3}") int maxRetries,
            @Value("${qa.resilience.retry.initial-backoff:2s}") Duration initialBackoff) {
        log.info("LlmRetryAdvisor created: maxRetries={}, initialBackoff={}", maxRetries, initialBackoff);
        return new LlmRetryAdvisor(maxRetries, initialBackoff);
    }
}
