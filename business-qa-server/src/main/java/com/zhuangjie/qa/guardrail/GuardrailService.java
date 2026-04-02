package com.zhuangjie.qa.guardrail;

import com.zhuangjie.qa.db.entity.GuardrailRule;
import com.zhuangjie.qa.db.service.GuardrailRuleDbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuardrailService {

    private final GuardrailRuleDbService guardrailRuleDbService;

    /**
     * Check user input against active guardrail rules.
     * Returns null if input passes; returns a block/warn message if intercepted.
     */
    public GuardrailCheckResult checkInput(String input) {
        List<GuardrailRule> inputRules = guardrailRuleDbService.listActive().stream()
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
     * Filter AI output through output guardrail rules (masking sensitive patterns).
     */
    public String filterOutput(String output) {
        List<GuardrailRule> outputRules = guardrailRuleDbService.listActive().stream()
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

    private String maskString(String original) {
        if (original.length() <= 4) return "****";
        return original.substring(0, 2) + "*".repeat(original.length() - 4) + original.substring(original.length() - 2);
    }

    public record GuardrailCheckResult(boolean blocked, String action, String message, String ruleName) {
        static final GuardrailCheckResult PASS = new GuardrailCheckResult(false, null, null, null);
    }
}
