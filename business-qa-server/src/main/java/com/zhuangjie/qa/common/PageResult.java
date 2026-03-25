package com.zhuangjie.qa.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.util.List;

/**
 * 分页结果包装类，将 MyBatis-Plus 的 Page 对象转为前端友好的格式。
 * 包含 total（总记录数）、current（当前页码）、size（每页条数）、records（数据列表）。
 */
@Data
public class PageResult<T> {

    private long total;
    private long current;
    private long size;
    private List<T> records;

    /** 从 MyBatis-Plus 的 Page 对象转换 */
    public static <T> PageResult<T> of(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(page.getRecords());
        return result;
    }
}
