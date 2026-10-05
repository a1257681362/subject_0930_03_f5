package com.evops.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 发布环境巡检规则新版本：版本号全库递增，旧版本置 SUPERSEDED 只读，
 * 已完成巡检引用的历史版本不改写。
 */
@Data
public class EnvRuleVersionCreateRequest {

    /** 按材质的阈值与采样频率，至少一行 */
    @NotEmpty(message = "规则阈值明细不能为空")
    @Valid
    private List<EnvRuleItemRequest> items;

    @NotBlank(message = "发布人不能为空")
    private String publishedBy;

    private String remark;
}
