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

@RestController
@RequestMapping("/api/change")
@RequiredArgsConstructor
public class ChangeController {

    private final ChangeDetector changeDetector;
    private final ChangeDetectionDbService changeDetectionDbService;
    private final ChangeSuggestionDbService changeSuggestionDbService;

    @PostMapping("/detect/{moduleId}")
    public Result<ChangeDetection> detect(@PathVariable Long moduleId) {
        return Result.ok(changeDetector.detect(moduleId));
    }

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

    @GetMapping("/detections/{id}/suggestions")
    public Result<List<ChangeSuggestion>> getSuggestions(@PathVariable Long id) {
        return Result.ok(changeSuggestionDbService.listByDetectionId(id));
    }

    @GetMapping("/suggestions/pending")
    public Result<List<ChangeSuggestion>> pendingSuggestions() {
        return Result.ok(changeSuggestionDbService.listPending());
    }

    @PutMapping("/suggestions/{id}/apply")
    public Result<Void> applySuggestion(@PathVariable Long id) {
        changeSuggestionDbService.lambdaUpdate()
                .eq(ChangeSuggestion::getId, id)
                .set(ChangeSuggestion::getStatus, "APPLIED")
                .update();
        return Result.ok();
    }

    @PutMapping("/suggestions/{id}/ignore")
    public Result<Void> ignoreSuggestion(@PathVariable Long id) {
        changeSuggestionDbService.lambdaUpdate()
                .eq(ChangeSuggestion::getId, id)
                .set(ChangeSuggestion::getStatus, "IGNORED")
                .update();
        return Result.ok();
    }
}
