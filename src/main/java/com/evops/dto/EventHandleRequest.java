package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 异常事件处置：必须登记处置人并关联至少一张现场照片。
 */
@Data
public class EventHandleRequest {

    /** 处置人 */
    @NotBlank(message = "处置人不能为空")
    private String handler;

    private String handleNote;

    /** 现场照片，至少一张 */
    @NotEmpty(message = "异常处置必须关联现场照片")
    private List<PhotoItem> photos;

    @Data
    public static class PhotoItem {
        @NotBlank(message = "照片地址不能为空")
        private String photoUrl;
        private String photoName;
    }
}
