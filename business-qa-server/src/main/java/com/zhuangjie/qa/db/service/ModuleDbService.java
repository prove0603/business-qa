package com.zhuangjie.qa.db.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhuangjie.qa.db.entity.QaModule;
import com.zhuangjie.qa.db.mapper.ModuleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 模块数据库服务层 */
@Service
public class ModuleDbService extends ServiceImpl<ModuleMapper, QaModule> {

    /** 按模块类型查询（COMMON / TASK），仅返回有效记录 */
    public List<QaModule> listByType(String moduleType) {
        return lambdaQuery().eq(QaModule::getModuleType, moduleType).eq(QaModule::getStatus, 1).list();
    }

    /** 查询所有有效模块 */
    public List<QaModule> listActive() {
        return lambdaQuery().eq(QaModule::getStatus, 1).list();
    }

    public QaModule getByCode(String moduleCode) {
        return lambdaQuery().eq(QaModule::getModuleCode, moduleCode).one();
    }
}
