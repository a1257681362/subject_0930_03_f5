package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 环境巡检规则版本：每次发布一个新版本，版本号全库递增。
 * 旧版本置 SUPERSEDED 只读不改写——已完成巡检引用的规则版本永久可查。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_env_rule_version")
public class EnvRuleVersion extends BaseEntity {

    /** 版本号，全库从 1 递增 */
    private Integer versionNo;

    /** ACTIVE / SUPERSEDED */
    private String status;

    private String publishedBy;

    private LocalDateTime publishedAt;

    private String remark;
}
