package com.zhuangjie.qa.db.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhuangjie.qa.db.entity.GuardrailRule;
import com.zhuangjie.qa.db.mapper.GuardrailRuleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GuardrailRuleDbService extends ServiceImpl<GuardrailRuleMapper, GuardrailRule> {

    public List<GuardrailRule> listActive() {
        return lambdaQuery()
                .eq(GuardrailRule::getIsActive, true)
                .orderByAsc(GuardrailRule::getSortOrder)
                .list();
    }

    public List<GuardrailRule> listActiveByType(String ruleType) {
        return lambdaQuery()
                .eq(GuardrailRule::getIsActive, true)
                .eq(GuardrailRule::getRuleType, ruleType)
                .orderByAsc(GuardrailRule::getSortOrder)
                .list();
    }

    public List<GuardrailRule> listAll() {
        return lambdaQuery().orderByAsc(GuardrailRule::getSortOrder).list();
    }
}
