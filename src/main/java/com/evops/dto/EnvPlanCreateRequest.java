package com.evops.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 生成巡检计划：按计划日期为每个开放展厅生成一条巡检任务。
 */
@Data
public class EnvPlanCreateRequest {

    @NotNull(message = "计划日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate planDate;

    @NotBlank(message = "创建人不能为空")
    private String createdBy;

    private String remark;
}
