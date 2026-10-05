package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 巡检计划：按计划日期为每个开放展厅生成一条巡检任务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_env_inspect_plan")
public class EnvInspectPlan extends BaseEntity {

    private String planNo;

    private LocalDate planDate;

    /** GENERATED */
    private String status;

    private String createdBy;

    private String remark;
}
