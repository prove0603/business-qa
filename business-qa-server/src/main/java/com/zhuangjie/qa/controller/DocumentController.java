package com.zhuangjie.qa.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhuangjie.qa.common.PageResult;
import com.zhuangjie.qa.common.Result;
import com.zhuangjie.qa.db.entity.QaDocument;
import com.zhuangjie.qa.doc.DocumentService;
import com.zhuangjie.qa.doc.VectorSyncService;
import com.zhuangjie.qa.pojo.req.DocumentCreateReq;
import com.zhuangjie.qa.pojo.req.DocumentUpdateReq;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final VectorSyncService vectorSyncService;

    @GetMapping("/page")
    public Result<PageResult<QaDocument>> page(
            @RequestParam(required = false) Long moduleId,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size) {
        Page<QaDocument> page = documentService.page(moduleId, current, size);
        return Result.ok(PageResult.of(page));
    }

    @GetMapping("/list")
    public Result<List<QaDocument>> listByModule(@RequestParam Long moduleId) {
        return Result.ok(documentService.listByModuleId(moduleId));
    }

    @GetMapping("/{id}")
    public Result<QaDocument> get(@PathVariable Long id) {
        return Result.ok(documentService.getById(id));
    }

    @PostMapping
    public Result<QaDocument> create(@Valid @RequestBody DocumentCreateReq req) {
        return Result.ok(documentService.create(req));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<QaDocument> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("moduleId") Long moduleId,
            @RequestParam(value = "title", required = false) String title) {
        return Result.ok(documentService.uploadFile(moduleId, title, file));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        QaDocument doc = documentService.getById(id);
        if (doc == null || doc.getFileKey() == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] content = documentService.downloadFile(id);
        String filename = doc.getOriginalFilename() != null ? doc.getOriginalFilename() : doc.getTitle();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CONTENT_TYPE,
                        doc.getContentType() != null ? doc.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .body(content);
    }

    @PutMapping("/{id}")
    public Result<QaDocument> update(@PathVariable Long id, @Valid @RequestBody DocumentUpdateReq req) {
        return Result.ok(documentService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/vectorize")
    public Result<Void> vectorize(@PathVariable Long id) {
        vectorSyncService.asyncVectorize(id);
        return Result.ok();
    }

    @PostMapping("/vectorize-all")
    public Result<Void> vectorizeAll() {
        vectorSyncService.reVectorizeAll();
        return Result.ok();
    }
}
