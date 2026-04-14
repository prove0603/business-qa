package com.zhuangjie.qa.change;

import com.zhuangjie.qa.db.entity.QaModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitSyncService {

    @Value("${qa.git.clone-base-dir:${user.home}/.business-qa/repos}")
    private String cloneBaseDir;

    @Value("${qa.git.username:}")
    private String gitUsername;

    @Value("${qa.git.password:}")
    private String gitPassword;

    /**
     * 同步仓库：不存在则克隆，已存在则拉取。返回本地仓库路径。
     */
    public Path syncRepo(QaModule module) throws IOException, GitAPIException {
        Path cloneDir = getCloneDir(module);
        String branch = module.getGitBranch() != null ? module.getGitBranch() : "master";

        if (Files.exists(cloneDir.resolve(".git"))) {
            return pullRepo(cloneDir, branch, module);
        } else {
            return cloneRepo(module.getGitRemoteUrl(), cloneDir, branch, module);
        }
    }

    /**
     * 解析 HEAD 指向的 commit hash 值。
     */
    public String resolveHead(Path repoRoot) {
        try (Repository repo = openRepository(repoRoot)) {
            ObjectId head = repo.resolve("HEAD");
            return head != null ? head.getName() : null;
        } catch (IOException e) {
            log.warn("Failed to resolve HEAD: {}", repoRoot, e);
            return null;
        }
    }

    /**
     * 检测两个 commit 之间的变更文件（新增、修改、删除）。
     */
    public DeltaResult detectChanges(Path repoRoot, String fromCommit, String toCommit) {
        try (Repository repo = openRepository(repoRoot);
             RevWalk revWalk = new RevWalk(repo)) {

            ObjectId fromId = repo.resolve(fromCommit);
            ObjectId toId = toCommit != null ? repo.resolve(toCommit) : repo.resolve("HEAD");

            if (fromId == null || toId == null) {
                throw new IllegalArgumentException("Cannot resolve commits");
            }

            RevCommit from = revWalk.parseCommit(fromId);
            RevCommit to = revWalk.parseCommit(toId);

            try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                formatter.setRepository(repo);
                formatter.setDetectRenames(true);

                List<DiffEntry> diffs = formatter.scan(from.getTree(), to.getTree());
                List<String> added = new ArrayList<>();
                List<String> modified = new ArrayList<>();
                List<String> deleted = new ArrayList<>();

                for (DiffEntry diff : diffs) {
                    switch (diff.getChangeType()) {
                        case ADD -> added.add(diff.getNewPath());
                        case MODIFY -> modified.add(diff.getNewPath());
                        case DELETE -> deleted.add(diff.getOldPath());
                        case RENAME -> {
                            deleted.add(diff.getOldPath());
                            added.add(diff.getNewPath());
                        }
                        case COPY -> added.add(diff.getNewPath());
                    }
                }

                return new DeltaResult(fromCommit, toId.getName(), added, modified, deleted);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to detect changes", e);
        }
    }

    /**
     * 获取两个 commit 之间的完整 diff 内容。
     */
    public String getFileDiff(Path repoRoot, String fromCommit, String toCommit) {
        try (Repository repo = openRepository(repoRoot);
             RevWalk revWalk = new RevWalk(repo)) {

            ObjectId fromId = repo.resolve(fromCommit);
            ObjectId toId = repo.resolve(toCommit);

            RevCommit from = revWalk.parseCommit(fromId);
            RevCommit to = revWalk.parseCommit(toId);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter formatter = new DiffFormatter(out)) {
                formatter.setRepository(repo);
                formatter.setDetectRenames(true);
                formatter.format(from.getTree(), to.getTree());
                return out.toString();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to get diff", e);
        }
    }

    public Path getCloneDir(QaModule module) {
        String dirName = module.getModuleCode().replaceAll("[^a-zA-Z0-9_-]", "_");
        return Path.of(cloneBaseDir, dirName);
    }

    private Path cloneRepo(String remoteUrl, Path targetDir, String branch, QaModule module)
            throws IOException, GitAPIException {
        Files.createDirectories(targetDir.getParent());
        log.info("Cloning {} (branch: {}) into {}", remoteUrl, branch, targetDir);

        var cmd = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(targetDir.toFile())
                .setBranch(branch)
                .setCloneAllBranches(false);

        CredentialsProvider cred = getCredentials();
        if (cred != null) cmd.setCredentialsProvider(cred);

        cmd.call().close();
        log.info("Clone completed for module: {}", module.getModuleName());
        return targetDir;
    }

    private Path pullRepo(Path repoDir, String branch, QaModule module) throws IOException, GitAPIException {
        log.info("Pulling latest for module: {} (branch: {})", module.getModuleName(), branch);
        try (Git git = Git.open(repoDir.toFile())) {
            git.checkout().setName(branch).call();
            var pullCmd = git.pull().setRemoteBranchName(branch);
            CredentialsProvider cred = getCredentials();
            if (cred != null) pullCmd.setCredentialsProvider(cred);
            PullResult result = pullCmd.call();
            if (!result.isSuccessful()) {
                log.warn("Pull had issues for module: {}", module.getModuleName());
            }
        }
        return repoDir;
    }

    private CredentialsProvider getCredentials() {
        if (gitUsername != null && !gitUsername.isBlank() && gitPassword != null && !gitPassword.isBlank()) {
            return new UsernamePasswordCredentialsProvider(gitUsername, gitPassword);
        }
        return null;
    }

    private Repository openRepository(Path repoRoot) throws IOException {
        return new FileRepositoryBuilder()
                .setGitDir(repoRoot.resolve(".git").toFile())
                .readEnvironment()
                .build();
    }

    public record DeltaResult(
            String fromCommit, String toCommit,
            List<String> addedFiles, List<String> modifiedFiles, List<String> deletedFiles
    ) {
        public List<String> allChangedFiles() {
            List<String> all = new ArrayList<>();
            all.addAll(addedFiles);
            all.addAll(modifiedFiles);
            return all;
        }

        public int totalCount() {
            return addedFiles.size() + modifiedFiles.size() + deletedFiles.size();
        }
    }
}
