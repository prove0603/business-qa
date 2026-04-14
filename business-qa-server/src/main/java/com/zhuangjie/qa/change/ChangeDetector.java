package com.zhuangjie.qa.change;

import cn.hutool.json.JSONUtil;
import com.zhuangjie.qa.db.entity.ChangeDetection;
import com.zhuangjie.qa.db.entity.QaModule;
import com.zhuangjie.qa.db.service.ChangeDetectionDbService;
import com.zhuangjie.qa.db.service.ModuleDbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeDetector {

    private final GitSyncService gitSyncService;
    private final ModuleDbService moduleDbService;
    private final ChangeDetectionDbService changeDetectionDbService;
    private final SuggestionGenerator suggestionGenerator;

    /**
     * 触发指定模块的变更检测流程：
     * 1. Git pull 拉取最新代码
     * 2. 对比上次同步 commit，检测差异文件
     * 3. 调用 AI 分析受影响的文档，生成更新建议
     */
    public ChangeDetection detect(Long moduleId) {
        QaModule module = moduleDbService.getById(moduleId);
        if (module == null || module.getGitRemoteUrl() == null) {
            throw new RuntimeException("Module not found or no Git URL configured");
        }

        // 创建检测记录并标记为进行中
        ChangeDetection detection = new ChangeDetection();
        detection.setModuleId(moduleId);
        detection.setFromCommit(module.getLastSyncCommit());
        detection.setStatus("RUNNING");
        changeDetectionDbService.save(detection);

        try {
            Path repoPath = gitSyncService.syncRepo(module);
            String currentHead = gitSyncService.resolveHead(repoPath);

            // 首次同步：无基准 commit，仅记录 HEAD 并更新模块，不产生 diff
            if (module.getLastSyncCommit() == null) {
                detection.setToCommit(currentHead);
                detection.setChangedFileCount(0);
                detection.setStatus("COMPLETED");
                detection.setChangedFiles("[]");
                changeDetectionDbService.updateById(detection);

                moduleDbService.lambdaUpdate()
                        .eq(QaModule::getId, moduleId)
                        .set(QaModule::getLastSyncCommit, currentHead)
                        .update();
                return detection;
            }

            // 与上次同步一致：无代码变更
            if (currentHead.equals(module.getLastSyncCommit())) {
                detection.setToCommit(currentHead);
                detection.setChangedFileCount(0);
                detection.setStatus("COMPLETED");
                detection.setChangedFiles("[]");
                changeDetectionDbService.updateById(detection);
                return detection;
            }

            // 计算两次 commit 间的变更文件列表并落库
            GitSyncService.DeltaResult delta = gitSyncService.detectChanges(
                    repoPath, module.getLastSyncCommit(), currentHead);

            List<String> changedFiles = delta.allChangedFiles();
            detection.setToCommit(currentHead);
            detection.setChangedFiles(JSONUtil.toJsonStr(changedFiles));
            detection.setChangedFileCount(delta.totalCount());
            detection.setStatus("COMPLETED");
            changeDetectionDbService.updateById(detection);

            moduleDbService.lambdaUpdate()
                    .eq(QaModule::getId, moduleId)
                    .set(QaModule::getLastSyncCommit, currentHead)
                    .update();

            // 有变更时拉取完整 diff，异步触发 AI 建议生成（使用更新前的 fromCommit 与当前 HEAD）
            if (!changedFiles.isEmpty()) {
                String diffContent = gitSyncService.getFileDiff(
                        repoPath, module.getLastSyncCommit(), currentHead);
                suggestionGenerator.generateSuggestions(detection, module, diffContent);
            }

            return detection;
        } catch (Exception e) {
            log.error("Change detection failed for module {}: {}", moduleId, e.getMessage(), e);
            detection.setStatus("FAILED");
            detection.setErrorMessage(e.getMessage());
            changeDetectionDbService.updateById(detection);
            return detection;
        }
    }
}
