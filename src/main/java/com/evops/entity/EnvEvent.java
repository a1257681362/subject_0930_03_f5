package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 超限事件：同一展厅同一指标的连续超限合并为同一事件（over_count 累加）。
 * 闭环：OPEN -> HANDLED（处置人 + 现场照片）-> CLOSED（保管员复核）；
 * 处置后再次超限视为处置无效，事件回到 OPEN 重新处置。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_env_event")
public class EnvEvent extends BaseEntity {

    private String eventNo;

    private Long hallId;

    /** TEMPERATURE / HUMIDITY / ILLUMINANCE */
    private String metric;

    /** 首次超限时任务引用的规则版本（报表口径） */
    private Long ruleVersionId;

    private Long firstReadingId;

    private Long lastReadingId;

    /** 合并进本事件的连续超限读数条数 */
    private Integer overCount;

    /** OPEN / HANDLED / CLOSED */
    private String status;

    private LocalDateTime openedAt;

    /** 处置人 */
    private String handler;

    private LocalDateTime handledAt;

    private String handleNote;

    /** 保管员复核人 */
    private String reviewer;

    private LocalDateTime reviewedAt;

    private String reviewNote;
}
