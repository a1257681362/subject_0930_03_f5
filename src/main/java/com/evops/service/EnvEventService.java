package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evops.dto.EventHandleRequest;
import com.evops.dto.EventReviewRequest;
import com.evops.entity.EnvEvent;
import com.evops.entity.EnvEventPhoto;
import com.evops.mapper.EnvEventMapper;
import com.evops.mapper.EnvEventPhotoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.evops.constant.MuseumConstants.*;

/**
 * 超限事件闭环：OPEN -> HANDLED（处置人 + 现场照片）-> CLOSED（保管员复核）。
 * 事件关闭前必须由保管员复核；处置与复核人均留痕，照片只追加不删除。
 */
@Service
public class EnvEventService {

    private final EnvEventMapper eventMapper;
    private final EnvEventPhotoMapper photoMapper;
    private final EnvRuleService ruleService;

    public EnvEventService(EnvEventMapper eventMapper,
                           EnvEventPhotoMapper photoMapper,
                           EnvRuleService ruleService) {
        this.eventMapper = eventMapper;
        this.photoMapper = photoMapper;
        this.ruleService = ruleService;
    }

    public EnvEvent requireEvent(Long eventId) {
        EnvEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("超限事件不存在: id=" + eventId);
        }
        return event;
    }

    public List<EnvEvent> listEvents(Long hallId, String status) {
        LambdaQueryWrapper<EnvEvent> wrapper = new LambdaQueryWrapper<EnvEvent>()
                .orderByDesc(EnvEvent::getId);
        if (hallId != null) {
            wrapper.eq(EnvEvent::getHallId, hallId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(EnvEvent::getStatus, status);
        }
        return eventMapper.selectList(wrapper);
    }

    /** 事件详情：事件 + 采用的规则版本 + 现场照片 */
    public Map<String, Object> eventDetail(Long eventId) {
        EnvEvent event = requireEvent(eventId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("event", event);
        detail.put("ruleVersion", ruleService.requireVersion(event.getRuleVersionId()));
        detail.put("photos", photosOf(eventId));
        return detail;
    }

    /**
     * 处置：必须登记处置人并关联至少一张现场照片；OPEN -> HANDLED。
     * 照片只追加，处置留痕（处置人、时间、说明）。
     */
    @Transactional
    public EnvEvent handle(Long eventId, EventHandleRequest req) {
        EnvEvent event = requireEvent(eventId);
        if (!EVENT_OPEN.equals(event.getStatus())) {
            throw new IllegalArgumentException("事件当前不在待处置状态，不能重复处置: " + event.getEventNo());
        }
        LocalDateTime now = LocalDateTime.now();
        int sort = 0;
        for (EventHandleRequest.PhotoItem item : req.getPhotos()) {
            EnvEventPhoto photo = new EnvEventPhoto();
            photo.setEventId(eventId);
            photo.setPhotoUrl(item.getPhotoUrl());
            photo.setPhotoName(item.getPhotoName());
            photo.setSortNo(sort++);
            photoMapper.insert(photo);
        }
        event.setStatus(EVENT_HANDLED);
        event.setHandler(req.getHandler());
        event.setHandledAt(now);
        event.setHandleNote(req.getHandleNote());
        eventMapper.updateById(event);
        return event;
    }

    /**
     * 保管员复核：事件关闭前必须复核；HANDLED -> CLOSED。
     * 未处置的事件不能复核关闭。
     */
    @Transactional
    public EnvEvent review(Long eventId, EventReviewRequest req) {
        EnvEvent event = requireEvent(eventId);
        if (EVENT_CLOSED.equals(event.getStatus())) {
            throw new IllegalArgumentException("事件已复核关闭，不能重复复核: " + event.getEventNo());
        }
        if (!EVENT_HANDLED.equals(event.getStatus())) {
            throw new IllegalArgumentException("事件尚未处置，关闭前必须先登记处置人与现场照片: "
                    + event.getEventNo());
        }
        event.setStatus(EVENT_CLOSED);
        event.setReviewer(req.getReviewer());
        event.setReviewedAt(LocalDateTime.now());
        event.setReviewNote(req.getReviewNote());
        eventMapper.updateById(event);
        return event;
    }

    public List<EnvEventPhoto> photosOf(Long eventId) {
        return photoMapper.selectList(new LambdaQueryWrapper<EnvEventPhoto>()
                .eq(EnvEventPhoto::getEventId, eventId)
                .orderByAsc(EnvEventPhoto::getSortNo).orderByAsc(EnvEventPhoto::getId));
    }
}
