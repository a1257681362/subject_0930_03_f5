package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 事件现场照片：异常处置时必须关联至少一张；只追加不删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_env_event_photo")
public class EnvEventPhoto extends BaseEntity {

    private Long eventId;

    private String photoUrl;

    private String photoName;

    private Integer sortNo;
}
