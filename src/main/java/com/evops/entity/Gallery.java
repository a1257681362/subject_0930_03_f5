package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 展厅：巡检计划按展厅生成任务。关闭只改状态，不删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_gallery")
public class Gallery extends BaseEntity {

    private String hallNo;

    private String name;

    private String location;

    /** ACTIVE / CLOSED */
    private String status;
}
