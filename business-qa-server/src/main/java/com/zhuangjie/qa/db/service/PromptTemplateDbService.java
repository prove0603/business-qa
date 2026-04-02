package com.zhuangjie.qa.db.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhuangjie.qa.db.entity.PromptTemplate;
import com.zhuangjie.qa.db.mapper.PromptTemplateMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptTemplateDbService extends ServiceImpl<PromptTemplateMapper, PromptTemplate> {

    public PromptTemplate getByKey(String templateKey) {
        return lambdaQuery()
                .eq(PromptTemplate::getTemplateKey, templateKey)
                .eq(PromptTemplate::getIsActive, true)
                .one();
    }

    public List<PromptTemplate> listAll() {
        return lambdaQuery().orderByAsc(PromptTemplate::getTemplateType, PromptTemplate::getTemplateKey).list();
    }
}
