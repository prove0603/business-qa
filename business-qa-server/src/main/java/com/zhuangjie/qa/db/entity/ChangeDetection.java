package com.zhuangjie.qa.db.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_change_detection")
public class ChangeDetection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long moduleId;
    private String fromCommit;
    private String toCommit;
    private String changedFiles;
    private Integer changedFileCount;
    private Integer suggestionCount;
    private String status;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
