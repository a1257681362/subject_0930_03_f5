package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 文物上架陈列：在展文物决定展厅适用的材质阈值。
 */
@Data
public class DisplayRequest {

    @NotNull(message = "文物ID不能为空")
    private Long relicId;
}
