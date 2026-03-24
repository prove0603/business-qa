package com.zhuangjie.qa.db.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhuangjie.qa.db.entity.ChangeSuggestion;
import com.zhuangjie.qa.db.mapper.ChangeSuggestionMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChangeSuggestionDbService extends ServiceImpl<ChangeSuggestionMapper, ChangeSuggestion> {

    public List<ChangeSuggestion> listByDetectionId(Long detectionId) {
        return lambdaQuery().eq(ChangeSuggestion::getDetectionId, detectionId).list();
    }

    public long countPending() {
        return lambdaQuery().eq(ChangeSuggestion::getStatus, "PENDING").count();
    }

    public List<ChangeSuggestion> listPending() {
        return lambdaQuery()
                .eq(ChangeSuggestion::getStatus, "PENDING")
                .orderByDesc(ChangeSuggestion::getCreateTime)
                .list();
    }
}
