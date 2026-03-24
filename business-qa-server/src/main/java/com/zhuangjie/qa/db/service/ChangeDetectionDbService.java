package com.zhuangjie.qa.db.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhuangjie.qa.db.entity.ChangeDetection;
import com.zhuangjie.qa.db.mapper.ChangeDetectionMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChangeDetectionDbService extends ServiceImpl<ChangeDetectionMapper, ChangeDetection> {

    public List<ChangeDetection> listByModuleId(Long moduleId) {
        return lambdaQuery()
                .eq(ChangeDetection::getModuleId, moduleId)
                .orderByDesc(ChangeDetection::getCreateTime)
                .list();
    }

    public ChangeDetection getLatestByModuleId(Long moduleId) {
        return lambdaQuery()
                .eq(ChangeDetection::getModuleId, moduleId)
                .orderByDesc(ChangeDetection::getCreateTime)
                .last("LIMIT 1")
                .one();
    }
}
