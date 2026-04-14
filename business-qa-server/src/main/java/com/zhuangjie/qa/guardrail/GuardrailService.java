package com.zhuangjie.qa.guardrail;

import com.zhuangjie.qa.db.entity.GuardrailRule;
import com.zhuangjie.qa.db.service.GuardrailRuleDbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 护栏服务：提供输入检查（拦截恶意/敏感输入）和输出脱敏（自动遮盖手机号、身份证等）。
 * 规则从数据库加载并本地缓存（30秒 TTL），规则变更后可手动刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardrailService {

    private final GuardrailRuleDbService guardrailRuleDbService;

    /** 规则本地缓存，避免每个 SSE chunk 都查一次数据库 */
    private final AtomicReference<List<GuardrailRule>> cachedRules = new AtomicReference<>();
    private final AtomicLong cacheTimestamp = new AtomicLong(0);
    private static final long CACHE_TTL_MS = 30_000;

    private List<GuardrailRule> getActiveRules() {
        long now = System.currentTimeMillis();
        List<GuardrailRule> cached = cachedRules.get();
        if (cached != null && now - cacheTimestamp.get() < CACHE_TTL_MS) {
            return cached;
        }
        List<GuardrailRule> fresh = guardrailRuleDbService.listActive();
        cachedRules.set(fresh);
        cacheTimestamp.set(now);
        return fresh;
    }

    /** 规则变更后可手动调用刷新缓存 */
    public void refreshCache() {
        cachedRules.set(null);
        cacheTimestamp.set(0);
    }

    /**
     * 输入护栏检查：遍历所有 INPUT_ 类型的活跃规则，匹配则返回拦截结果。
     * 支持关键词匹配（INPUT_KEYWORD）和正则匹配（INPUT_REGEX）两种规则类型。
     */
    public GuardrailCheckResult checkInput(String input) {
        List<GuardrailRule> inputRules = getActiveRules().stream()
                .filter(r -> r.getRuleType().startsWith("INPUT_"))
                .toList();

        for (GuardrailRule rule : inputRules) {
            boolean matched = switch (rule.getRuleType()) {
                case "INPUT_KEYWORD" -> containsKeyword(input, rule.getPattern());
                case "INPUT_REGEX" -> matchesRegex(input, rule.getPattern());
                default -> false;
            };

            if (matched) {
                log.warn("Guardrail triggered: rule='{}', type={}, action={}", rule.getRuleName(), rule.getRuleType(), rule.getAction());
                String message = rule.getReplyMessage() != null ? rule.getReplyMessage() : "您的输入触发了安全规则，请重新描述问题。";
                return new GuardrailCheckResult(true, rule.getAction(), message, rule.getRuleName());
            }
        }

        return GuardrailCheckResult.PASS;
    }

    /**
     * 输出护栏过滤：对 LLM 输出的每个 chunk 执行 OUTPUT_REGEX 规则匹配，
     * 将命中的敏感信息（如手机号、身份证）自动脱敏为部分遮盖格式。
     */
    public String filterOutput(String output) {
        List<GuardrailRule> outputRules = getActiveRules().stream()
                .filter(r -> "OUTPUT_REGEX".equals(r.getRuleType()) && "MASK".equals(r.getAction()))
                .toList();

        String result = output;
        for (GuardrailRule rule : outputRules) {
            try {
                Pattern pattern = Pattern.compile(rule.getPattern());
                Matcher matcher = pattern.matcher(result);
                if (matcher.find()) {
                    result = matcher.replaceAll(match -> maskString(match.group()));
                    log.debug("Output masked by rule '{}': pattern={}", rule.getRuleName(), rule.getPattern());
                }
            } catch (Exception e) {
                log.warn("Invalid output guardrail regex '{}': {}", rule.getPattern(), e.getMessage());
            }
        }
        return result;
    }

    private boolean containsKeyword(String input, String pattern) {
        String lower = input.toLowerCase();
        for (String keyword : pattern.split("[,，|]")) {
            if (lower.contains(keyword.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesRegex(String input, String pattern) {
        try {
            return Pattern.compile(pattern).matcher(input).find();
        } catch (Exception e) {
            log.warn("Invalid guardrail regex '{}': {}", pattern, e.getMessage());
            return false;
        }
    }

    /** 脱敏处理：保留前2位和后2位，中间用 * 替换 */
    private String maskString(String original) {
        if (original.length() <= 4) return "****";
        return original.substring(0, 2) + "*".repeat(original.length() - 4) + original.substring(original.length() - 2);
    }

    public record GuardrailCheckResult(boolean blocked, String action, String message, String ruleName) {
        static final GuardrailCheckResult PASS = new GuardrailCheckResult(false, null, null, null);
    }
}
