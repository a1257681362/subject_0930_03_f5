package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 展厅陈列：文物上架/撤展记录。撤展只置 REMOVED，记录保留可追溯。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_gallery_display")
public class GalleryDisplay extends BaseEntity {

    private Long hallId;

    private Long relicId;

    private LocalDateTime displayedAt;

    private LocalDateTime removedAt;

    /** ON_DISPLAY / REMOVED */
    private String status;
}
