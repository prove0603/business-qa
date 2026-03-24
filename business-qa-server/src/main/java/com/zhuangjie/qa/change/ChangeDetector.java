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
     * Triggers change detection for a module:
     * 1. Git pull to get latest code
     * 2. Detect diff from last sync commit
     * 3. AI-analyze affected documents and generate suggestions
     */
    public ChangeDetection detect(Long moduleId) {
        QaModule module = moduleDbService.getById(moduleId);
        if (module == null || module.getGitRemoteUrl() == null) {
            throw new RuntimeException("Module not found or no Git URL configured");
        }

        ChangeDetection detection = new ChangeDetection();
        detection.setModuleId(moduleId);
        detection.setFromCommit(module.getLastSyncCommit());
        detection.setStatus("RUNNING");
        changeDetectionDbService.save(detection);

        try {
            Path repoPath = gitSyncService.syncRepo(module);
            String currentHead = gitSyncService.resolveHead(repoPath);

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

            if (currentHead.equals(module.getLastSyncCommit())) {
                detection.setToCommit(currentHead);
                detection.setChangedFileCount(0);
                detection.setStatus("COMPLETED");
                detection.setChangedFiles("[]");
                changeDetectionDbService.updateById(detection);
                return detection;
            }

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
