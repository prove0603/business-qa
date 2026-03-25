package com.zhuangjie.qa.db.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhuangjie.qa.db.entity.ChangeSuggestion;
import com.zhuangjie.qa.db.mapper.ChangeSuggestionMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** AI 变更建议数据库服务层 */
@Service
public class ChangeSuggestionDbService extends ServiceImpl<ChangeSuggestionMapper, ChangeSuggestion> {

    /** 查询某次检测产生的所有建议 */
    public List<ChangeSuggestion> listByDetectionId(Long detectionId) {
        return lambdaQuery().eq(ChangeSuggestion::getDetectionId, detectionId).list();
    }

    /** 统计待处理的建议数量（仪表盘使用） */
    public long countPending() {
        return lambdaQuery().eq(ChangeSuggestion::getStatus, "PENDING").count();
    }

    /** 获取所有待处理的建议，按时间倒序 */
    public List<ChangeSuggestion> listPending() {
        return lambdaQuery()
                .eq(ChangeSuggestion::getStatus, "PENDING")
                .orderByDesc(ChangeSuggestion::getCreateTime)
                .list();
    }
}
