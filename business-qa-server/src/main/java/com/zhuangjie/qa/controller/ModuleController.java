package com.zhuangjie.qa.controller;

import com.zhuangjie.qa.common.Result;
import com.zhuangjie.qa.db.entity.QaModule;
import com.zhuangjie.qa.db.service.ModuleDbService;
import com.zhuangjie.qa.pojo.req.ModuleReq;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模块管理 REST 接口。
 *
 * 模块是文档和问答的组织单元，分为两种类型：
 * - COMMON: 全局模块，其文档在所有问答中都会被检索（如公司规范、通用FAQ）
 * - TASK: 业务模块，需要用户手动选择才纳入检索范围
 *
 * 每个模块可关联 Git 仓库，用于代码变更检测功能。
 */
@RestController
@RequestMapping("/api/module")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleDbService moduleDbService;

    /** 获取所有有效模块（status=1） */
    @GetMapping("/list")
    public Result<List<QaModule>> list() {
        return Result.ok(moduleDbService.listActive());
    }

    @GetMapping("/{id}")
    public Result<QaModule> get(@PathVariable Long id) {
        return Result.ok(moduleDbService.getById(id));
    }

    @PostMapping
    public Result<QaModule> create(@RequestBody ModuleReq req) {
        QaModule module = new QaModule();
        module.setModuleName(req.moduleName());
        module.setModuleCode(req.moduleCode());
        module.setGitRemoteUrl(req.gitRemoteUrl());
        module.setGitBranch(req.gitBranch() != null ? req.gitBranch() : "master");
        module.setModuleType(req.moduleType());
        module.setDescription(req.description());
        module.setStatus(1);
        moduleDbService.save(module);
        return Result.ok(module);
    }

    @PutMapping("/{id}")
    public Result<QaModule> update(@PathVariable Long id, @RequestBody ModuleReq req) {
        QaModule module = moduleDbService.getById(id);
        if (module == null) return Result.fail("Module not found");

        module.setModuleName(req.moduleName());
        module.setModuleCode(req.moduleCode());
        module.setGitRemoteUrl(req.gitRemoteUrl());
        module.setGitBranch(req.gitBranch());
        module.setModuleType(req.moduleType());
        module.setDescription(req.description());
        moduleDbService.updateById(module);
        return Result.ok(module);
    }

    /** 逻辑删除（status 置 0） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        moduleDbService.lambdaUpdate().eq(QaModule::getId, id).set(QaModule::getStatus, 0).update();
        return Result.ok();
    }
}
