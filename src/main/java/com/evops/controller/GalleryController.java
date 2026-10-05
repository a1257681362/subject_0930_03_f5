package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.DisplayRequest;
import com.evops.dto.GalleryCreateRequest;
import com.evops.entity.Gallery;
import com.evops.entity.GalleryDisplay;
import com.evops.service.EnvInspectionService;
import com.evops.service.GalleryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 展厅与陈列：展厅建档、文物上架/撤展、展厅环境报表。
 */
@RestController
public class GalleryController {

    private final GalleryService galleryService;
    private final EnvInspectionService inspectionService;

    public GalleryController(GalleryService galleryService, EnvInspectionService inspectionService) {
        this.galleryService = galleryService;
        this.inspectionService = inspectionService;
    }

    /** 展厅建档（编号唯一） */
    @PostMapping("/api/halls")
    public ApiResponse<Gallery> createHall(@Valid @RequestBody GalleryCreateRequest request) {
        return ApiResponse.ok(galleryService.createHall(request));
    }

    /** 展厅列表，可按状态过滤 */
    @GetMapping("/api/halls")
    public ApiResponse<List<Gallery>> listHalls(@RequestParam(required = false) String status) {
        return ApiResponse.ok(galleryService.listHalls(status));
    }

    /** 文物上架陈列 */
    @PostMapping("/api/halls/{id}/displays")
    public ApiResponse<GalleryDisplay> display(@PathVariable Long id,
                                               @Valid @RequestBody DisplayRequest request) {
        return ApiResponse.ok(galleryService.display(id, request.getRelicId()));
    }

    /** 展厅陈列记录（默认只看在展） */
    @GetMapping("/api/halls/{id}/displays")
    public ApiResponse<List<GalleryDisplay>> listDisplays(@PathVariable Long id,
                                                          @RequestParam(defaultValue = "true") boolean onDisplay) {
        return ApiResponse.ok(galleryService.listDisplays(id, onDisplay));
    }

    /** 撤展：只改状态，记录保留 */
    @PostMapping("/api/displays/{displayId}/remove")
    public ApiResponse<GalleryDisplay> removeDisplay(@PathVariable Long displayId) {
        return ApiResponse.ok(galleryService.removeDisplay(displayId));
    }

    /** 展厅环境报表：计划/任务/读数/事件均标明采用的规则版本 */
    @GetMapping("/api/halls/{id}/env-report")
    public ApiResponse<Map<String, Object>> hallReport(@PathVariable Long id) {
        return ApiResponse.ok(inspectionService.hallReport(id));
    }
}
