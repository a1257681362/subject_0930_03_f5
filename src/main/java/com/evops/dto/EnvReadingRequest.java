package com.evops.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 登记环境读数：只追加，按任务固化的规则版本评估超限。
 */
@Data
public class EnvReadingRequest {

    @NotNull(message = "温度不能为空")
    private BigDecimal temperature;

    @NotNull(message = "湿度不能为空")
    private BigDecimal humidity;

    @NotNull(message = "照度不能为空")
    private BigDecimal illuminance;

    @NotNull(message = "采样时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime sampledAt;

    @NotBlank(message = "采样人不能为空")
    private String sampler;
}
