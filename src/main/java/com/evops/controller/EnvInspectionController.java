package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.EnvPlanCreateRequest;
import com.evops.dto.EnvReadingRequest;
import com.evops.dto.EventHandleRequest;
import com.evops.dto.EventReviewRequest;
import com.evops.dto.TaskCompleteRequest;
import com.evops.entity.EnvEvent;
import com.evops.entity.EnvReading;
import com.evops.service.EnvEventService;
import com.evops.service.EnvInspectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 展厅环境巡检：计划按展厅生成任务 -> 登记读数（连续超限合并事件）
 * -> 任务完成只读；异常事件处置（现场照片 + 处置人）-> 保管员复核关闭。
 */
@RestController
public class EnvInspectionController {

    private final EnvInspectionService inspectionService;
    private final EnvEventService eventService;

    public EnvInspectionController(EnvInspectionService inspectionService, EnvEventService eventService) {
        this.inspectionService = inspectionService;
        this.eventService = eventService;
    }

    /** 生成巡检计划：为每个开放展厅生成一条任务（固化当前规则版本） */
    @PostMapping("/api/env-plans")
    public ApiResponse<Map<String, Object>> generatePlan(@Valid @RequestBody EnvPlanCreateRequest request) {
        return ApiResponse.ok(inspectionService.generatePlan(request));
    }

    /** 巡检计划列表 */
    @GetMapping("/api/env-plans")
    public ApiResponse<List<?>> listPlans() {
        return ApiResponse.ok(inspectionService.listPlans());
    }

    /** 计划详情：计划 + 各展厅任务 */
    @GetMapping("/api/env-plans/{id}")
    public ApiResponse<Map<String, Object>> getPlan(@PathVariable Long id) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("plan", inspectionService.requirePlan(id));
        detail.put("tasks", inspectionService.tasksOfPlan(id));
        return ApiResponse.ok(detail);
    }

    /** 任务详情：任务 + 采用的规则版本与阈值 + 读数明细 */
    @GetMapping("/api/env-tasks/{id}")
    public ApiResponse<Map<String, Object>> getTask(@PathVariable Long id) {
        return ApiResponse.ok(inspectionService.taskDetail(id));
    }

    /** 登记环境读数：超限自动开/并事件 */
    @PostMapping("/api/env-tasks/{id}/readings")
    public ApiResponse<EnvReading> recordReading(@PathVariable Long id,
                                                 @Valid @RequestBody EnvReadingRequest request) {
        return ApiResponse.ok(inspectionService.recordReading(id, request));
    }

    /** 完成巡检任务：完成后只读 */
    @PostMapping("/api/env-tasks/{id}/complete")
    public ApiResponse<?> completeTask(@PathVariable Long id,
                                       @Valid @RequestBody TaskCompleteRequest request) {
        return ApiResponse.ok(inspectionService.completeTask(id, request.getCompletedBy()));
    }

    /** 超限事件列表，可按展厅/状态过滤 */
    @GetMapping("/api/env-events")
    public ApiResponse<List<EnvEvent>> listEvents(@RequestParam(required = false) Long hallId,
                                                  @RequestParam(required = false) String status) {
        return ApiResponse.ok(eventService.listEvents(hallId, status));
    }

    /** 事件详情：事件 + 规则版本 + 现场照片 */
    @GetMapping("/api/env-events/{id}")
    public ApiResponse<Map<String, Object>> getEvent(@PathVariable Long id) {
        return ApiResponse.ok(eventService.eventDetail(id));
    }

    /** 事件处置：必须登记处置人并关联现场照片 */
    @PostMapping("/api/env-events/{id}/handle")
    public ApiResponse<EnvEvent> handle(@PathVariable Long id,
                                        @Valid @RequestBody EventHandleRequest request) {
        return ApiResponse.ok(eventService.handle(id, request));
    }

    /** 保管员复核：事件关闭前必须复核 */
    @PostMapping("/api/env-events/{id}/review")
    public ApiResponse<EnvEvent> review(@PathVariable Long id,
                                        @Valid @RequestBody EventReviewRequest request) {
        return ApiResponse.ok(eventService.review(id, request));
    }
}
