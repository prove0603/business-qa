package com.zhuangjie.qa.controller;

import com.zhuangjie.qa.common.Result;
import com.zhuangjie.qa.db.entity.PromptTemplate;
import com.zhuangjie.qa.db.service.PromptTemplateDbService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prompt")
@RequiredArgsConstructor
public class PromptController {

    private final PromptTemplateDbService promptTemplateDbService;

    @GetMapping("/list")
    public Result<List<PromptTemplate>> list() {
        return Result.ok(promptTemplateDbService.listAll());
    }

    @GetMapping("/{id}")
    public Result<PromptTemplate> get(@PathVariable Long id) {
        return Result.ok(promptTemplateDbService.getById(id));
    }

    @GetMapping("/key/{key}")
    public Result<PromptTemplate> getByKey(@PathVariable String key) {
        return Result.ok(promptTemplateDbService.getByKey(key));
    }

    @PostMapping
    public Result<PromptTemplate> create(@RequestBody PromptTemplate template) {
        promptTemplateDbService.save(template);
        return Result.ok(template);
    }

    @PutMapping("/{id}")
    public Result<PromptTemplate> update(@PathVariable Long id, @RequestBody PromptTemplate template) {
        template.setId(id);
        promptTemplateDbService.updateById(template);
        return Result.ok(promptTemplateDbService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        promptTemplateDbService.removeById(id);
        return Result.ok();
    }

    @PutMapping("/{id}/toggle")
    public Result<PromptTemplate> toggleActive(@PathVariable Long id) {
        PromptTemplate template = promptTemplateDbService.getById(id);
        if (template != null) {
            template.setIsActive(!template.getIsActive());
            promptTemplateDbService.updateById(template);
        }
        return Result.ok(promptTemplateDbService.getById(id));
    }
}
