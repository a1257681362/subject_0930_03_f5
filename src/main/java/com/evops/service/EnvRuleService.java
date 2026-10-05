package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evops.dto.EnvRuleItemRequest;
import com.evops.dto.EnvRuleVersionCreateRequest;
import com.evops.entity.EnvRuleItem;
import com.evops.entity.EnvRuleVersion;
import com.evops.mapper.EnvRuleItemMapper;
import com.evops.mapper.EnvRuleVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.evops.constant.MuseumConstants.*;

/**
 * 环境巡检规则版本：每次发布一个新版本（版本号全库递增），
 * 旧版本置 SUPERSEDED 只读不改写——已完成巡检引用的历史版本永久可查，
 * 阈值或采样频率调整只能通过发布新版本生效。
 */
@Service
public class EnvRuleService {

    private final EnvRuleVersionMapper versionMapper;
    private final EnvRuleItemMapper itemMapper;

    public EnvRuleService(EnvRuleVersionMapper versionMapper, EnvRuleItemMapper itemMapper) {
        this.versionMapper = versionMapper;
        this.itemMapper = itemMapper;
    }

    /**
     * 发布新规则版本：校验阈值区间合法、材质不重复；
     * 新版本 ACTIVE，原现行版本置 SUPERSEDED（数据保留，不改写）。
     */
    @Transactional
    public EnvRuleVersion publish(EnvRuleVersionCreateRequest req) {
        Set<String> materials = new HashSet<>();
        for (EnvRuleItemRequest item : req.getItems()) {
            if (!materials.add(item.getMaterial())) {
                throw new IllegalArgumentException("同一版本内材质不能重复: " + item.getMaterial());
            }
            if (item.getTempMin().compareTo(item.getTempMax()) > 0) {
                throw new IllegalArgumentException("温度下限不能高于上限: " + item.getMaterial());
            }
            if (item.getHumidityMin().compareTo(item.getHumidityMax()) > 0) {
                throw new IllegalArgumentException("湿度下限不能高于上限: " + item.getMaterial());
            }
        }

        Integer maxNo = versionMapper.selectList(new LambdaQueryWrapper<EnvRuleVersion>()
                        .orderByDesc(EnvRuleVersion::getVersionNo)
                        .last("LIMIT 1"))
                .stream().findFirst().map(EnvRuleVersion::getVersionNo).orElse(0);

        LocalDateTime now = LocalDateTime.now();
        EnvRuleVersion version = new EnvRuleVersion();
        version.setVersionNo(maxNo + 1);
        version.setStatus(RULE_ACTIVE);
        version.setPublishedBy(req.getPublishedBy());
        version.setPublishedAt(now);
        version.setRemark(req.getRemark());
        versionMapper.insert(version);

        for (EnvRuleItemRequest itemReq : req.getItems()) {
            EnvRuleItem item = new EnvRuleItem();
            item.setVersionId(version.getId());
            item.setMaterial(itemReq.getMaterial());
            item.setTempMin(itemReq.getTempMin());
            item.setTempMax(itemReq.getTempMax());
            item.setHumidityMin(itemReq.getHumidityMin());
            item.setHumidityMax(itemReq.getHumidityMax());
            item.setIlluminanceMax(itemReq.getIlluminanceMax());
            item.setSampleIntervalMinutes(itemReq.getSampleIntervalMinutes());
            itemMapper.insert(item);
        }

        // 旧版本只改状态标记，阈值明细一行不改：历史巡检仍按原版本追溯
        List<EnvRuleVersion> old = versionMapper.selectList(new LambdaQueryWrapper<EnvRuleVersion>()
                .eq(EnvRuleVersion::getStatus, RULE_ACTIVE)
                .ne(EnvRuleVersion::getId, version.getId()));
        for (EnvRuleVersion v : old) {
            v.setStatus(RULE_SUPERSEDED);
            versionMapper.updateById(v);
        }
        return version;
    }

    /** 当前生效版本；尚未发布时返回 null */
    public EnvRuleVersion currentVersion() {
        return versionMapper.selectOne(new LambdaQueryWrapper<EnvRuleVersion>()
                .eq(EnvRuleVersion::getStatus, RULE_ACTIVE)
                .last("LIMIT 1"));
    }

    public EnvRuleVersion requireVersion(Long versionId) {
        EnvRuleVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new IllegalArgumentException("规则版本不存在: id=" + versionId);
        }
        return version;
    }

    public List<EnvRuleVersion> listVersions() {
        return versionMapper.selectList(new LambdaQueryWrapper<EnvRuleVersion>()
                .orderByAsc(EnvRuleVersion::getVersionNo));
    }

    public List<EnvRuleItem> itemsOf(Long versionId) {
        return itemMapper.selectList(new LambdaQueryWrapper<EnvRuleItem>()
                .eq(EnvRuleItem::getVersionId, versionId)
                .orderByAsc(EnvRuleItem::getId));
    }

    /**
     * 取指定规则版本下若干材质的阈值；任一材质未被版本覆盖即报错，
     * 不允许无阈值依据地生成巡检任务。
     */
    public Map<String, EnvRuleItem> thresholdsFor(Long versionId, Set<String> materials) {
        Map<String, EnvRuleItem> result = new HashMap<>();
        for (EnvRuleItem item : itemsOf(versionId)) {
            result.put(item.getMaterial(), item);
        }
        Set<String> missing = new HashSet<>(materials);
        missing.removeAll(result.keySet());
        if (!missing.isEmpty()) {
            EnvRuleVersion version = requireVersion(versionId);
            throw new IllegalArgumentException("规则版本V" + version.getVersionNo()
                    + "未覆盖材质阈值: " + String.join("、", missing));
        }
        return result;
    }
}
