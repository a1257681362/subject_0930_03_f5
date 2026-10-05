package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 巡检任务：一个计划下一个展厅一条。
 * 固化规则版本与采样频率快照——规则发新版不改写已生成任务；
 * 完成后只读，不接受新读数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_env_inspect_task")
public class EnvInspectTask extends BaseEntity {

    private String taskNo;

    private Long planId;

    private Long hallId;

    /** 生成任务时引用的规则版本（快照，永不改写） */
    private Long ruleVersionId;

    /** 采样频率快照：展厅在展文物各材质采样频率的最小值（分钟） */
    private Integer sampleIntervalMinutes;

    /** PENDING / COMPLETED */
    private String status;

    private LocalDateTime scheduledAt;

    private String completedBy;

    private LocalDateTime completedAt;
}
