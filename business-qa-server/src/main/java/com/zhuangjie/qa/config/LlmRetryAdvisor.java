package com.zhuangjie.qa.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Spring AI Advisor that transparently retries failed LLM calls with exponential backoff.
 * <p>
 * Retries on: 429 (rate limit), 5xx (server error), timeout.
 * Applies to both sync ({@link CallAdvisor}) and streaming ({@link StreamAdvisor}) paths.
 */
@Slf4j
public class LlmRetryAdvisor implements CallAdvisor, StreamAdvisor {

    private final int maxRetries;
    private final Duration initialBackoff;

    public LlmRetryAdvisor(int maxRetries, Duration initialBackoff) {
        this.maxRetries = maxRetries;
        this.initialBackoff = initialBackoff;
    }

    /**
     * 同步调用的重试逻辑。
     * <p>
     * 这是 Spring AI Advisor 链的一环：当 ChatClient.call() 执行时，
     * 请求会沿着 Advisor 链传递：LlmRetryAdvisor → RAG Advisor → Memory Advisor → 实际 LLM 调用。
     * chain.nextCall(request) 就是"把请求交给链中的下一个 Advisor 处理"。
     * <p>
     * 重试策略：指数退避，即每次等待时间翻倍（2s → 4s → 8s）。
     * 只有特定异常（429/5xx/timeout）才会重试，其他异常直接抛出。
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Exception lastException = null;
        // attempt=0 是首次尝试，attempt=1~maxRetries 是重试
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                // 调用 Advisor 链的下一环（最终会到达 LLM API 调用）
                // 成功则直接返回，不进入 catch
                return chain.nextCall(request);
            } catch (Exception e) {
                lastException = e;
                // 两种情况直接放弃：已达最大重试次数 或 异常类型不适合重试
                if (attempt == maxRetries || !isRetryable(e)) {
                    throw e;
                }
                // 指数退避：initialBackoff × 2^attempt（位运算 1L << attempt 等价于 2^attempt）
                // attempt=0 → ×1(2s), attempt=1 → ×2(4s), attempt=2 → ×4(8s)
                long backoffMs = initialBackoff.toMillis() * (1L << attempt);
                log.warn("LLM call failed (attempt {}/{}), retrying in {}ms: {}",
                        attempt + 1, maxRetries, backoffMs, e.getMessage());
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }
        // 理论上不会执行到这里（for 循环内的 throw 会先触发），防御性兜底
        throw new RuntimeException("Retries exhausted", lastException);
    }

    /**
     * 流式调用的重试逻辑（ChatClient.stream() 时走这里）。
     * <p>
     * 与同步版本逻辑相同，但用 Reactor 的 retryWhen 实现（非阻塞）。
     * Flux.defer() 确保每次重试都重新订阅，即重新发起 LLM 请求。
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return Flux.defer(() -> chain.nextStream(request))
                .retryWhen(Retry.backoff(maxRetries, initialBackoff)
                        .filter(this::isRetryable)   // 只对可重试异常生效
                        .doBeforeRetry(signal ->
                                log.warn("LLM stream failed (attempt {}/{}), retrying: {}",
                                        signal.totalRetries() + 1, maxRetries,
                                        signal.failure().getMessage()))
                );
    }

    /**
     * 判断异常是否值得重试。通过异常消息中的关键词匹配：
     * - 429 / rate limit：API 调用频率超限，等一下再试通常能成功
     * - 5xx：服务端临时故障
     * - timeout：网络超时
     * - connection reset/refused：连接层故障
     */
    private boolean isRetryable(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("429") || lower.contains("rate limit")
                || lower.contains("too many requests")
                || lower.contains("timeout") || lower.contains("timed out")
                || lower.contains("500") || lower.contains("502")
                || lower.contains("503") || lower.contains("504")
                || lower.contains("connection reset")
                || lower.contains("connection refused");
    }

    @Override
    public String getName() {
        return "LlmRetryAdvisor";
    }

    /**
     * Advisor 执行顺序：数值越小越先执行。
     * HIGHEST_PRECEDENCE + 100 确保 RetryAdvisor 在链的最外层，
     * 这样它能"包住"后续所有 Advisor（RAG、Memory 等）的执行——
     * 任何一环失败都会被这里捕获并重试。
     */
    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE + 100;
    }
}
