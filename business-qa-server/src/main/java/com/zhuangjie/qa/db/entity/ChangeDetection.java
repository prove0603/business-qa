package com.zhuangjie.qa.db.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 变更检测记录实体，对应 t_change_detection 表。
 *
 * 每次执行变更检测生成一条记录，记录 fromCommit → toCommit 之间的变更情况。
 * status: RUNNING → COMPLETED / FAILED
 */
@Data
@TableName("t_change_detection")
public class ChangeDetection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long moduleId;
    /** 上次同步的 commit hash（对比起点） */
    private String fromCommit;
    /** 本次检测的 commit hash（对比终点） */
    private String toCommit;
    /** 变更文件列表（JSON 数组） */
    private String changedFiles;
    private Integer changedFileCount;
    private Integer suggestionCount;
    /** RUNNING / COMPLETED / FAILED */
    private String status;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
