package com.zhuangjie.qa.db.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档实体，对应 t_document 表。
 *
 * 核心字段说明：
 * - content: 文档的纯文本内容（Tika 解析后或在线编辑的文本），用于分块和向量化
 * - contentHash: 内容的 SHA-256 哈希，用于更新时快速判断内容是否变更
 * - fileKey: MinIO 中的存储路径（仅文件上传的文档有此字段）
 * - vectorized: 是否已向量化完成（false 表示待向量化或向量化失败）
 * - chunkCount: 分块后产生的 chunk 数（即向量库中的记录数）
 * - status: 1=有效, 0=逻辑删除
 */
@Data
@TableName("t_document")
public class QaDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属模块 ID */
    private Long moduleId;
    private String title;
    /** 文档纯文本内容（向量化的数据来源） */
    private String content;
    /** 文档类型标识：MARKDOWN / PDF / WORD / TEXT */
    private String fileType;
    /** 内容的 SHA-256 哈希，判断更新时内容是否真正变化 */
    private String contentHash;

    /** 上传文件的原始文件名 */
    private String originalFilename;
    /** Tika 检测到的 MIME 类型 */
    private String contentType;
    /** MinIO 存储路径（如 documents/2024/01/15/abc123.pdf） */
    private String fileKey;
    private Long fileSize;

    /** 向量化产生的 chunk 数量 */
    private Integer chunkCount;
    /** 是否已成功向量化 */
    private Boolean vectorized;
    /** 文档版本号，每次内容变更自增 */
    private Integer version;
    /** 状态：1=有效, 0=逻辑删除 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
