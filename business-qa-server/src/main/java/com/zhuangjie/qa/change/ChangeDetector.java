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

/**
 * 代码变更检测器，编排 Git 同步 → 差异分析 → AI 建议生成 的完整流程。
 *
 * 触发方式：前端点击"检测变更"按钮 → ChangeController → detect()
 *
 * 整体流程：
 * 1. 拉取/克隆最新代码（GitSyncService.syncRepo）
 * 2. 对比上次同步的 commit 和最新 HEAD 的差异
 * 3. 如果有变更文件，获取 diff 内容
 * 4. 将 diff 发给 AI（SuggestionGenerator）分析是否需要更新文档
 *
 * 检测结果保存在 t_change_detection 表，AI 建议保存在 t_change_suggestion 表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeDetector {

    private final GitSyncService gitSyncService;
    private final ModuleDbService moduleDbService;
    private final ChangeDetectionDbService changeDetectionDbService;
    private final SuggestionGenerator suggestionGenerator;

    /**
     * 执行变更检测。
     *
     * 三种情况：
     * 1. 首次检测（lastSyncCommit 为 null）：只记录当前 HEAD，不做 diff
     * 2. 无变更（HEAD == lastSyncCommit）：直接返回空结果
     * 3. 有变更：计算 diff，异步触发 AI 分析
     */
    public ChangeDetection detect(Long moduleId) {
        QaModule module = moduleDbService.getById(moduleId);
        if (module == null || module.getGitRemoteUrl() == null) {
            throw new RuntimeException("Module not found or no Git URL configured");
        }

        // 先创建检测记录，状态 RUNNING
        ChangeDetection detection = new ChangeDetection();
        detection.setModuleId(moduleId);
        detection.setFromCommit(module.getLastSyncCommit());
        detection.setStatus("RUNNING");
        changeDetectionDbService.save(detection);

        try {
            // 第一步：同步 Git 仓库（不存在则克隆，已存在则 pull）
            Path repoPath = gitSyncService.syncRepo(module);
            String currentHead = gitSyncService.resolveHead(repoPath);

            // 情况 1：首次检测，没有上次的 commit 可以对比
            if (module.getLastSyncCommit() == null) {
                detection.setToCommit(currentHead);
                detection.setChangedFileCount(0);
                detection.setStatus("COMPLETED");
                detection.setChangedFiles("[]");
                changeDetectionDbService.updateById(detection);

                // 记录当前 HEAD 作为下次对比的基准
                moduleDbService.lambdaUpdate()
                        .eq(QaModule::getId, moduleId)
                        .set(QaModule::getLastSyncCommit, currentHead)
                        .update();
                return detection;
            }

            // 情况 2：代码没有变化
            if (currentHead.equals(module.getLastSyncCommit())) {
                detection.setToCommit(currentHead);
                detection.setChangedFileCount(0);
                detection.setStatus("COMPLETED");
                detection.setChangedFiles("[]");
                changeDetectionDbService.updateById(detection);
                return detection;
            }

            // 情况 3：有变更，计算差异
            GitSyncService.DeltaResult delta = gitSyncService.detectChanges(
                    repoPath, module.getLastSyncCommit(), currentHead);

            List<String> changedFiles = delta.allChangedFiles();
            detection.setToCommit(currentHead);
            detection.setChangedFiles(JSONUtil.toJsonStr(changedFiles));
            detection.setChangedFileCount(delta.totalCount());
            detection.setStatus("COMPLETED");
            changeDetectionDbService.updateById(detection);

            // 更新模块的最后同步 commit
            moduleDbService.lambdaUpdate()
                    .eq(QaModule::getId, moduleId)
                    .set(QaModule::getLastSyncCommit, currentHead)
                    .update();

            // 有变更文件时，获取 diff 并异步触发 AI 分析
            if (!changedFiles.isEmpty()) {
                String diffContent = gitSyncService.getFileDiff(
                        repoPath, module.getLastSyncCommit(), currentHead);
                // @Async 异步执行，不阻塞当前接口返回
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
