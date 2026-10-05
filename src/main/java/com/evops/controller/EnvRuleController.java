package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.EnvRuleVersionCreateRequest;
import com.evops.entity.EnvRuleVersion;
import com.evops.service.EnvRuleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 环境巡检规则版本：按材质配置阈值与采样频率；
 * 发布新版本不改写历史版本，已完成巡检仍按原版本追溯。
 */
@RestController
public class EnvRuleController {

    private final EnvRuleService ruleService;

    public EnvRuleController(EnvRuleService ruleService) {
        this.ruleService = ruleService;
    }

    /** 发布新规则版本：旧版本置 SUPERSEDED 只读 */
    @PostMapping("/api/env-rule-versions")
    public ApiResponse<Map<String, Object>> publish(@Valid @RequestBody EnvRuleVersionCreateRequest request) {
        return ApiResponse.ok(detail(ruleService.publish(request)));
    }

    /** 规则版本列表（含历史版本，均可查） */
    @GetMapping("/api/env-rule-versions")
    public ApiResponse<List<EnvRuleVersion>> listVersions() {
        return ApiResponse.ok(ruleService.listVersions());
    }

    /** 当前生效版本 */
    @GetMapping("/api/env-rule-versions/current")
    public ApiResponse<EnvRuleVersion> current() {
        return ApiResponse.ok(ruleService.currentVersion());
    }

    /** 版本详情：版本 + 按材质的阈值与采样频率 */
    @GetMapping("/api/env-rule-versions/{id}")
    public ApiResponse<Map<String, Object>> getVersion(@PathVariable Long id) {
        return ApiResponse.ok(detail(ruleService.requireVersion(id)));
    }

    private Map<String, Object> detail(EnvRuleVersion version) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("version", version);
        detail.put("items", ruleService.itemsOf(version.getId()));
        return detail;
    }
}
