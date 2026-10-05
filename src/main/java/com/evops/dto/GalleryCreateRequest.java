package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 展厅建档：关闭展厅只改状态，不删除。
 */
@Data
public class GalleryCreateRequest {

    @NotBlank(message = "展厅编号不能为空")
    private String hallNo;

    @NotBlank(message = "展厅名称不能为空")
    private String name;

    private String location;
}
