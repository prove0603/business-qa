package com.zhuangjie.qa.db.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhuangjie.qa.db.entity.QaDocument;
import com.zhuangjie.qa.db.mapper.DocumentMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentDbService extends ServiceImpl<DocumentMapper, QaDocument> {

    public List<QaDocument> listByModuleId(Long moduleId) {
        return lambdaQuery().eq(QaDocument::getModuleId, moduleId).eq(QaDocument::getStatus, 1).list();
    }

    public List<QaDocument> listUnvectorized() {
        return lambdaQuery().eq(QaDocument::getVectorized, false).eq(QaDocument::getStatus, 1).list();
    }

    public long countByModuleId(Long moduleId) {
        return lambdaQuery().eq(QaDocument::getModuleId, moduleId).eq(QaDocument::getStatus, 1).count();
    }
}
