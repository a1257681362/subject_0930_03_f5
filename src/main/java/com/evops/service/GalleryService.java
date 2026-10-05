package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evops.dto.GalleryCreateRequest;
import com.evops.entity.Gallery;
import com.evops.entity.GalleryDisplay;
import com.evops.entity.Relic;
import com.evops.entity.RelicLoan;
import com.evops.mapper.GalleryDisplayMapper;
import com.evops.mapper.GalleryMapper;
import com.evops.mapper.RelicLoanMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.evops.constant.MuseumConstants.*;

/**
 * 展厅与陈列：展厅关闭只改状态；文物上架/撤展留痕不删除。
 * 在展文物决定展厅适用的材质阈值与采样频率。
 */
@Service
public class GalleryService {

    private final GalleryMapper galleryMapper;
    private final GalleryDisplayMapper displayMapper;
    private final RelicLoanMapper loanMapper;
    private final RelicService relicService;

    public GalleryService(GalleryMapper galleryMapper,
                          GalleryDisplayMapper displayMapper,
                          RelicLoanMapper loanMapper,
                          RelicService relicService) {
        this.galleryMapper = galleryMapper;
        this.displayMapper = displayMapper;
        this.loanMapper = loanMapper;
        this.relicService = relicService;
    }

    @Transactional
    public Gallery createHall(GalleryCreateRequest req) {
        if (galleryMapper.selectCount(new LambdaQueryWrapper<Gallery>()
                .eq(Gallery::getHallNo, req.getHallNo())) > 0) {
            throw new IllegalArgumentException("展厅编号已存在: " + req.getHallNo());
        }
        Gallery hall = new Gallery();
        hall.setHallNo(req.getHallNo());
        hall.setName(req.getName());
        hall.setLocation(req.getLocation());
        hall.setStatus(HALL_ACTIVE);
        galleryMapper.insert(hall);
        return hall;
    }

    public Gallery requireHall(Long hallId) {
        Gallery hall = galleryMapper.selectById(hallId);
        if (hall == null) {
            throw new IllegalArgumentException("展厅不存在: id=" + hallId);
        }
        return hall;
    }

    public List<Gallery> listHalls(String status) {
        LambdaQueryWrapper<Gallery> wrapper = new LambdaQueryWrapper<Gallery>()
                .orderByAsc(Gallery::getHallNo);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Gallery::getStatus, status);
        }
        return galleryMapper.selectList(wrapper);
    }

    /**
     * 文物上架：须在档、未外借、且未在其他展厅在展；一件文物同一时间只在一个展厅在展。
     */
    @Transactional
    public GalleryDisplay display(Long hallId, Long relicId) {
        Gallery hall = requireHall(hallId);
        if (!HALL_ACTIVE.equals(hall.getStatus())) {
            throw new IllegalArgumentException("展厅已关闭，不能上架: " + hall.getHallNo());
        }
        Relic relic = relicService.requireActiveRelic(relicId);
        Long lent = loanMapper.selectCount(new LambdaQueryWrapper<RelicLoan>()
                .eq(RelicLoan::getRelicId, relicId)
                .eq(RelicLoan::getStatus, LOAN_LENT));
        if (lent != null && lent > 0) {
            throw new IllegalArgumentException("文物尚在外借，不能上架陈列: " + relic.getRelicNo());
        }
        Long onDisplay = displayMapper.selectCount(new LambdaQueryWrapper<GalleryDisplay>()
                .eq(GalleryDisplay::getRelicId, relicId)
                .eq(GalleryDisplay::getStatus, DISPLAY_ON));
        if (onDisplay != null && onDisplay > 0) {
            throw new IllegalArgumentException("文物已在展厅陈列，不能重复上架: " + relic.getRelicNo());
        }
        GalleryDisplay display = new GalleryDisplay();
        display.setHallId(hallId);
        display.setRelicId(relicId);
        display.setDisplayedAt(LocalDateTime.now());
        display.setStatus(DISPLAY_ON);
        displayMapper.insert(display);
        return display;
    }

    /** 撤展：只置 REMOVED，陈列记录保留可追溯 */
    @Transactional
    public GalleryDisplay removeDisplay(Long displayId) {
        GalleryDisplay display = displayMapper.selectById(displayId);
        if (display == null) {
            throw new IllegalArgumentException("陈列记录不存在: id=" + displayId);
        }
        if (!DISPLAY_ON.equals(display.getStatus())) {
            throw new IllegalArgumentException("该陈列已撤展，不能重复操作");
        }
        display.setStatus(DISPLAY_REMOVED);
        display.setRemovedAt(LocalDateTime.now());
        displayMapper.updateById(display);
        return display;
    }

    public List<GalleryDisplay> listDisplays(Long hallId, boolean onlyOnDisplay) {
        LambdaQueryWrapper<GalleryDisplay> wrapper = new LambdaQueryWrapper<GalleryDisplay>()
                .eq(GalleryDisplay::getHallId, hallId)
                .orderByAsc(GalleryDisplay::getId);
        if (onlyOnDisplay) {
            wrapper.eq(GalleryDisplay::getStatus, DISPLAY_ON);
        }
        return displayMapper.selectList(wrapper);
    }

    /** 展厅当前在展的陈列记录 */
    public List<GalleryDisplay> onDisplay(Long hallId) {
        return listDisplays(hallId, true);
    }
}
