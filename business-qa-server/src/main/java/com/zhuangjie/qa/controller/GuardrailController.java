package com.zhuangjie.qa.controller;

import com.zhuangjie.qa.common.Result;
import com.zhuangjie.qa.db.entity.GuardrailRule;
import com.zhuangjie.qa.db.service.GuardrailRuleDbService;
import com.zhuangjie.qa.guardrail.GuardrailService;
import com.zhuangjie.qa.guardrail.GuardrailService.GuardrailCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/guardrail")
@RequiredArgsConstructor
public class GuardrailController {

    private final GuardrailRuleDbService guardrailRuleDbService;
    private final GuardrailService guardrailService;

    @GetMapping("/list")
    public Result<List<GuardrailRule>> list() {
        return Result.ok(guardrailRuleDbService.listAll());
    }

    @GetMapping("/{id}")
    public Result<GuardrailRule> get(@PathVariable Long id) {
        return Result.ok(guardrailRuleDbService.getById(id));
    }

    @PostMapping
    public Result<GuardrailRule> create(@RequestBody GuardrailRule rule) {
        guardrailRuleDbService.save(rule);
        guardrailService.refreshCache();
        return Result.ok(rule);
    }

    @PutMapping("/{id}")
    public Result<GuardrailRule> update(@PathVariable Long id, @RequestBody GuardrailRule rule) {
        rule.setId(id);
        guardrailRuleDbService.updateById(rule);
        guardrailService.refreshCache();
        return Result.ok(guardrailRuleDbService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        guardrailRuleDbService.removeById(id);
        guardrailService.refreshCache();
        return Result.ok();
    }

    @PutMapping("/{id}/toggle")
    public Result<GuardrailRule> toggleActive(@PathVariable Long id) {
        GuardrailRule rule = guardrailRuleDbService.getById(id);
        if (rule != null) {
            rule.setIsActive(!rule.getIsActive());
            guardrailRuleDbService.updateById(rule);
            guardrailService.refreshCache();
        }
        return Result.ok(guardrailRuleDbService.getById(id));
    }

    @PostMapping("/test")
    public Result<GuardrailCheckResult> testInput(@RequestBody String input) {
        return Result.ok(guardrailService.checkInput(input));
    }
}
