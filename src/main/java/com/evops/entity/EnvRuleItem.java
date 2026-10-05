package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 规则阈值明细：一种材质一行——温度/湿度区间、照度上限与采样频率（分钟）。
 * 版本发布后不再修改，阈值调整只能发布新版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_env_rule_item")
public class EnvRuleItem extends BaseEntity {

    private Long versionId;

    /** 材质，与文物档案 material 对应 */
    private String material;

    private BigDecimal tempMin;

    private BigDecimal tempMax;

    private BigDecimal humidityMin;

    private BigDecimal humidityMax;

    /** 照度上限（lux） */
    private BigDecimal illuminanceMax;

    /** 采样频率（分钟/次） */
    private Integer sampleIntervalMinutes;
}
