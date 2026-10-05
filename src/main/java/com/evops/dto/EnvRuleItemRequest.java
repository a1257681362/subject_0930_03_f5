package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 规则阈值明细：一种材质一行——温度/湿度区间、照度上限、采样频率（分钟）。
 */
@Data
public class EnvRuleItemRequest {

    @NotBlank(message = "材质不能为空")
    private String material;

    @NotNull(message = "温度下限不能为空")
    private BigDecimal tempMin;

    @NotNull(message = "温度上限不能为空")
    private BigDecimal tempMax;

    @NotNull(message = "湿度下限不能为空")
    private BigDecimal humidityMin;

    @NotNull(message = "湿度上限不能为空")
    private BigDecimal humidityMax;

    @NotNull(message = "照度上限不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "照度上限必须大于0")
    private BigDecimal illuminanceMax;

    @NotNull(message = "采样频率不能为空")
    @Min(value = 1, message = "采样频率至少1分钟")
    private Integer sampleIntervalMinutes;
}
