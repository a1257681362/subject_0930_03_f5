package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 完成巡检任务：完成后任务只读，不接受新读数。
 */
@Data
public class TaskCompleteRequest {

    @NotBlank(message = "完成人不能为空")
    private String completedBy;
}
