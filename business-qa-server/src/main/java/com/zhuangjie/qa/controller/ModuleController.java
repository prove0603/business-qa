package com.zhuangjie.qa.controller;

import com.zhuangjie.qa.common.Result;
import com.zhuangjie.qa.db.entity.QaModule;
import com.zhuangjie.qa.db.service.ModuleDbService;
import com.zhuangjie.qa.pojo.req.ModuleReq;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/module")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleDbService moduleDbService;

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

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        moduleDbService.lambdaUpdate().eq(QaModule::getId, id).set(QaModule::getStatus, 0).update();
        return Result.ok();
    }
}
