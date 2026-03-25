package com.zhuangjie.qa.db.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhuangjie.qa.db.entity.QaDocument;
import com.zhuangjie.qa.db.mapper.DocumentMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档数据库服务层，封装 MyBatis-Plus 的 CRUD 操作。
 * 继承 ServiceImpl 获得 save/updateById/getById/lambdaQuery 等能力。
 */
@Service
public class DocumentDbService extends ServiceImpl<DocumentMapper, QaDocument> {

    /** 按模块查询有效文档（status=1） */
    public List<QaDocument> listByModuleId(Long moduleId) {
        return lambdaQuery().eq(QaDocument::getModuleId, moduleId).eq(QaDocument::getStatus, 1).list();
    }

    /** 查询所有未向量化的有效文档（用于批量重新向量化） */
    public List<QaDocument> listUnvectorized() {
        return lambdaQuery().eq(QaDocument::getVectorized, false).eq(QaDocument::getStatus, 1).list();
    }

    public long countByModuleId(Long moduleId) {
        return lambdaQuery().eq(QaDocument::getModuleId, moduleId).eq(QaDocument::getStatus, 1).count();
    }
}
