package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 环境读数：只追加。按任务固化的规则版本逐条评估超限，
 * 采样人、采样时间留痕，环境指标全程可追溯。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_env_reading")
public class EnvReading extends BaseEntity {

    private Long taskId;

    private Long hallId;

    private BigDecimal temperature;

    private BigDecimal humidity;

    private BigDecimal illuminance;

    private LocalDateTime sampledAt;

    private String sampler;

    /** 0 正常 / 1 超限 */
    private Integer overLimit;

    /** 超限说明（哪些材质、哪个指标、超多少） */
    private String overDetail;
}
