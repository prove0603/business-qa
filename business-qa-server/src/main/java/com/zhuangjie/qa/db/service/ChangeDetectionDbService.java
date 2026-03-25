package com.zhuangjie.qa.db.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhuangjie.qa.db.entity.ChangeDetection;
import com.zhuangjie.qa.db.mapper.ChangeDetectionMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 变更检测记录数据库服务层 */
@Service
public class ChangeDetectionDbService extends ServiceImpl<ChangeDetectionMapper, ChangeDetection> {

    /** 按模块查询检测记录，按时间倒序 */
    public List<ChangeDetection> listByModuleId(Long moduleId) {
        return lambdaQuery()
                .eq(ChangeDetection::getModuleId, moduleId)
                .orderByDesc(ChangeDetection::getCreateTime)
                .list();
    }

    /** 获取某模块最近一次检测记录 */
    public ChangeDetection getLatestByModuleId(Long moduleId) {
        return lambdaQuery()
                .eq(ChangeDetection::getModuleId, moduleId)
                .orderByDesc(ChangeDetection::getCreateTime)
                .last("LIMIT 1")
                .one();
    }
}
