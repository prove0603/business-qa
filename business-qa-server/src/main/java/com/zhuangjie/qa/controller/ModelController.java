package com.zhuangjie.qa.controller;

import com.zhuangjie.qa.common.Result;
import com.zhuangjie.qa.config.ModelConfigService;
import com.zhuangjie.qa.config.ModelConfigService.TestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
public class ModelController {

    private final ModelConfigService modelConfigService;

    /**
     * 获取当前模型配置信息
     */
    @GetMapping("/config")
    public Result<Map<String, String>> getConfig() {
        return Result.ok(Map.of(
                "activeChatModel", modelConfigService.getActiveChatModel(),
                "defaultChatModel", modelConfigService.getDefaultChatModel(),
                "embeddingModel", modelConfigService.getEmbeddingModel()
        ));
    }

    /**
     * 测试模型连通性
     */
    @PostMapping("/test")
    public Result<TestResult> testModel(@RequestParam String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return Result.fail("模型名称不能为空");
        }
        TestResult result = modelConfigService.testModel(modelName.trim());
        return Result.ok(result);
    }

    /**
     * 切换当前 chat model
     */
    @PostMapping("/switch")
    public Result<String> switchModel(@RequestParam String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return Result.fail("模型名称不能为空");
        }
        modelConfigService.switchChatModel(modelName.trim());
        return Result.ok(modelConfigService.getActiveChatModel());
    }

    /**
     * 恢复为默认 chat model
     */
    @PostMapping("/reset")
    public Result<String> resetModel() {
        modelConfigService.switchChatModel(null);
        return Result.ok(modelConfigService.getActiveChatModel());
    }
}
