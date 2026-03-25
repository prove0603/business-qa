package com.zhuangjie.qa.controller;

import com.zhuangjie.qa.change.ChangeDetector;
import com.zhuangjie.qa.common.Result;
import com.zhuangjie.qa.db.entity.ChangeDetection;
import com.zhuangjie.qa.db.entity.ChangeSuggestion;
import com.zhuangjie.qa.db.service.ChangeDetectionDbService;
import com.zhuangjie.qa.db.service.ChangeSuggestionDbService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Git 变更检测 REST 接口。
 *
 * 提供变更检测的触发和结果查询：
 * - 触发检测：拉取最新代码 → 分析 diff → AI 生成文档更新建议
 * - 查看检测记录和对应的 AI 建议
 * - 对建议执行"应用"或"忽略"操作
 */
@RestController
@RequestMapping("/api/change")
@RequiredArgsConstructor
public class ChangeController {

    private final ChangeDetector changeDetector;
    private final ChangeDetectionDbService changeDetectionDbService;
    private final ChangeSuggestionDbService changeSuggestionDbService;

    /** 触发指定模块的变更检测（同步返回检测结果，AI 建议异步生成） */
    @PostMapping("/detect/{moduleId}")
    public Result<ChangeDetection> detect(@PathVariable Long moduleId) {
        return Result.ok(changeDetector.detect(moduleId));
    }

    /** 查询变更检测记录（可按模块筛选） */
    @GetMapping("/detections")
    public Result<List<ChangeDetection>> listDetections(@RequestParam(required = false) Long moduleId) {
        if (moduleId != null) {
            return Result.ok(changeDetectionDbService.listByModuleId(moduleId));
        }
        return Result.ok(changeDetectionDbService.list());
    }

    @GetMapping("/detections/{id}")
    public Result<ChangeDetection> getDetection(@PathVariable Long id) {
        return Result.ok(changeDetectionDbService.getById(id));
    }

    /** 查询某次检测产生的所有 AI 建议 */
    @GetMapping("/detections/{id}/suggestions")
    public Result<List<ChangeSuggestion>> getSuggestions(@PathVariable Long id) {
        return Result.ok(changeSuggestionDbService.listByDetectionId(id));
    }

    /** 获取所有待处理的建议（跨检测记录） */
    @GetMapping("/suggestions/pending")
    public Result<List<ChangeSuggestion>> pendingSuggestions() {
        return Result.ok(changeSuggestionDbService.listPending());
    }

    /** 应用建议（标记为 APPLIED，当前仅改状态，未真正修改文档） */
    @PutMapping("/suggestions/{id}/apply")
    public Result<Void> applySuggestion(@PathVariable Long id) {
        changeSuggestionDbService.lambdaUpdate()
                .eq(ChangeSuggestion::getId, id)
                .set(ChangeSuggestion::getStatus, "APPLIED")
                .update();
        return Result.ok();
    }

    /** 忽略建议 */
    @PutMapping("/suggestions/{id}/ignore")
    public Result<Void> ignoreSuggestion(@PathVariable Long id) {
        changeSuggestionDbService.lambdaUpdate()
                .eq(ChangeSuggestion::getId, id)
                .set(ChangeSuggestion::getStatus, "IGNORED")
                .update();
        return Result.ok();
    }
}
