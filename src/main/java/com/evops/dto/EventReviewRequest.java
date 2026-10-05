package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 异常事件复核：事件关闭前必须由保管员复核。
 */
@Data
public class EventReviewRequest {

    /** 复核人（保管员） */
    @NotBlank(message = "复核保管员不能为空")
    private String reviewer;

    private String reviewNote;
}
