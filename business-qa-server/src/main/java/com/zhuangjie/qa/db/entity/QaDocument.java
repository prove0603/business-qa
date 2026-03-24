package com.zhuangjie.qa.db.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_document")
public class QaDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long moduleId;
    private String title;
    private String content;
    private String fileType;
    private String contentHash;

    private String originalFilename;
    private String contentType;
    private String fileKey;
    private Long fileSize;

    private Integer chunkCount;
    private Boolean vectorized;
    private Integer version;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
