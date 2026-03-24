package com.zhuangjie.qa.controller;

import com.zhuangjie.qa.common.Result;
import com.zhuangjie.qa.db.service.*;
import com.zhuangjie.qa.pojo.vo.DashboardVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ModuleDbService moduleDbService;
    private final DocumentDbService documentDbService;
    private final ChatSessionDbService chatSessionDbService;
    private final ChangeSuggestionDbService changeSuggestionDbService;

    @GetMapping("/overview")
    public Result<DashboardVo> overview() {
        return Result.ok(new DashboardVo(
                moduleDbService.listActive().size(),
                documentDbService.count(),
                chatSessionDbService.count(),
                changeSuggestionDbService.countPending()
        ));
    }
}
