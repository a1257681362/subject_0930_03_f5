package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evops.dto.EnvPlanCreateRequest;
import com.evops.dto.EnvReadingRequest;
import com.evops.entity.EnvEvent;
import com.evops.entity.EnvInspectPlan;
import com.evops.entity.EnvInspectTask;
import com.evops.entity.EnvReading;
import com.evops.entity.EnvRuleItem;
import com.evops.entity.EnvRuleVersion;
import com.evops.entity.Gallery;
import com.evops.entity.GalleryDisplay;
import com.evops.entity.Relic;
import com.evops.mapper.EnvEventMapper;
import com.evops.mapper.EnvInspectPlanMapper;
import com.evops.mapper.EnvInspectTaskMapper;
import com.evops.mapper.EnvReadingMapper;
import com.evops.mapper.RelicMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.evops.constant.MuseumConstants.*;

/**
 * 展厅环境巡检：
 *   计划按展厅生成任务（任务固化规则版本与采样频率快照）
 *   -> 读数只追加、按任务固化的规则版本评估超限
 *   -> 同一展厅同一指标的连续超限合并为同一事件。
 * 规则发布新版本不改写已生成/已完成任务；已完成任务只读，不接受新读数。
 */
@Service
public class EnvInspectionService {

    private final EnvInspectPlanMapper planMapper;
    private final EnvInspectTaskMapper taskMapper;
    private final EnvReadingMapper readingMapper;
    private final EnvEventMapper eventMapper;
    private final RelicMapper relicMapper;
    private final GalleryService galleryService;
    private final EnvRuleService ruleService;

    public EnvInspectionService(EnvInspectPlanMapper planMapper,
                                EnvInspectTaskMapper taskMapper,
                                EnvReadingMapper readingMapper,
                                EnvEventMapper eventMapper,
                                RelicMapper relicMapper,
                                GalleryService galleryService,
                                EnvRuleService ruleService) {
        this.planMapper = planMapper;
        this.taskMapper = taskMapper;
        this.readingMapper = readingMapper;
        this.eventMapper = eventMapper;
        this.relicMapper = relicMapper;
        this.galleryService = galleryService;
        this.ruleService = ruleService;
    }

    /**
     * 生成巡检计划：为每个开放展厅生成一条任务。
     * 任务取当前生效规则版本快照；采样频率取在展文物各材质中的最严（最小间隔）；
     * 在展文物的材质未被规则版本覆盖时整单失败，不允许无依据巡检。
     */
    @Transactional
    public Map<String, Object> generatePlan(EnvPlanCreateRequest req) {
        EnvRuleVersion rule = ruleService.currentVersion();
        if (rule == null) {
            throw new IllegalArgumentException("尚未发布环境巡检规则版本，不能生成巡检计划");
        }
        LocalDateTime now = LocalDateTime.now();
        EnvInspectPlan plan = new EnvInspectPlan();
        plan.setPlanNo(nextNo("JH", planMapper));
        plan.setPlanDate(req.getPlanDate());
        plan.setStatus(PLAN_GENERATED);
        plan.setCreatedBy(req.getCreatedBy());
        plan.setRemark(req.getRemark());
        planMapper.insert(plan);

        List<EnvInspectTask> tasks = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Gallery hall : galleryService.listHalls(HALL_ACTIVE)) {
            List<Relic> relics = onDisplayRelics(hall.getId());
            if (relics.isEmpty()) {
                skipped.add(hall.getHallNo() + "（无在展文物）");
                continue;
            }
            Set<String> materials = relics.stream()
                    .map(Relic::getMaterial).collect(Collectors.toSet());
            Map<String, EnvRuleItem> thresholds = ruleService.thresholdsFor(rule.getId(), materials);
            int interval = thresholds.values().stream()
                    .mapToInt(EnvRuleItem::getSampleIntervalMinutes).min().getAsInt();

            EnvInspectTask task = new EnvInspectTask();
            task.setTaskNo(nextNo("XJ", taskMapper));
            task.setPlanId(plan.getId());
            task.setHallId(hall.getId());
            task.setRuleVersionId(rule.getId());
            task.setSampleIntervalMinutes(interval);
            task.setStatus(TASK_PENDING);
            task.setScheduledAt(req.getPlanDate().atStartOfDay());
            taskMapper.insert(task);
            tasks.add(task);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plan", plan);
        result.put("ruleVersionNo", rule.getVersionNo());
        result.put("tasks", tasks);
        result.put("skippedHalls", skipped);
        return result;
    }

    /**
     * 登记环境读数：只追加；按任务固化的规则版本逐材质评估，
     * 任一在展材质超限即标记；同一展厅同一指标的连续超限合并进同一未关闭事件。
     */
    @Transactional
    public EnvReading recordReading(Long taskId, EnvReadingRequest req) {
        EnvInspectTask task = requireTask(taskId);
        if (!TASK_PENDING.equals(task.getStatus())) {
            throw new IllegalArgumentException("巡检任务已完成，保持只读，不接受新读数: " + task.getTaskNo());
        }
        List<Relic> relics = onDisplayRelics(task.getHallId());
        Set<String> materials = relics.stream().map(Relic::getMaterial).collect(Collectors.toSet());
        Map<String, EnvRuleItem> thresholds = ruleService.thresholdsFor(task.getRuleVersionId(), materials);

        LocalDateTime now = LocalDateTime.now();
        EnvReading reading = new EnvReading();
        reading.setTaskId(taskId);
        reading.setHallId(task.getHallId());
        reading.setTemperature(req.getTemperature());
        reading.setHumidity(req.getHumidity());
        reading.setIlluminance(req.getIlluminance());
        reading.setSampledAt(req.getSampledAt());
        reading.setSampler(req.getSampler());

        Map<String, List<String>> overByMetric = evaluate(req, thresholds);
        reading.setOverLimit(overByMetric.isEmpty() ? 0 : 1);
        if (!overByMetric.isEmpty()) {
            List<String> details = new ArrayList<>();
            overByMetric.values().forEach(details::addAll);
            reading.setOverDetail(String.join("；", details));
        }
        readingMapper.insert(reading);

        for (Map.Entry<String, List<String>> entry : overByMetric.entrySet()) {
            mergeOrOpenEvent(task, entry.getKey(), reading, now);
        }
        return reading;
    }

    /** 完成任务：PENDING -> COMPLETED，完成后只读 */
    @Transactional
    public EnvInspectTask completeTask(Long taskId, String completedBy) {
        EnvInspectTask task = requireTask(taskId);
        if (!TASK_PENDING.equals(task.getStatus())) {
            throw new IllegalArgumentException("巡检任务已完成，不能重复完成: " + task.getTaskNo());
        }
        task.setStatus(TASK_COMPLETED);
        task.setCompletedBy(completedBy);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }

    public EnvInspectTask requireTask(Long taskId) {
        EnvInspectTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("巡检任务不存在: id=" + taskId);
        }
        return task;
    }

    public EnvInspectPlan requirePlan(Long planId) {
        EnvInspectPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("巡检计划不存在: id=" + planId);
        }
        return plan;
    }

    public List<EnvInspectPlan> listPlans() {
        return planMapper.selectList(new LambdaQueryWrapper<EnvInspectPlan>()
                .orderByDesc(EnvInspectPlan::getPlanDate).orderByDesc(EnvInspectPlan::getId));
    }

    public List<EnvInspectTask> tasksOfPlan(Long planId) {
        return taskMapper.selectList(new LambdaQueryWrapper<EnvInspectTask>()
                .eq(EnvInspectTask::getPlanId, planId)
                .orderByAsc(EnvInspectTask::getId));
    }

    public List<EnvReading> readingsOfTask(Long taskId) {
        return readingMapper.selectList(new LambdaQueryWrapper<EnvReading>()
                .eq(EnvReading::getTaskId, taskId)
                .orderByAsc(EnvReading::getSampledAt).orderByAsc(EnvReading::getId));
    }

    /** 任务详情：任务 + 采用的规则版本 + 读数明细（环境指标可追溯） */
    public Map<String, Object> taskDetail(Long taskId) {
        EnvInspectTask task = requireTask(taskId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("task", task);
        detail.put("ruleVersion", ruleService.requireVersion(task.getRuleVersionId()));
        detail.put("ruleItems", ruleService.itemsOf(task.getRuleVersionId()));
        detail.put("readings", readingsOfTask(taskId));
        return detail;
    }

    /**
     * 展厅环境报表：计划、任务、读数、超限事件全部标明采用的规则版本号，
     * 环境指标与巡检计划全程可追溯。
     */
    public Map<String, Object> hallReport(Long hallId) {
        Gallery hall = galleryService.requireHall(hallId);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("hall", hall);

        List<Map<String, Object>> displays = new ArrayList<>();
        for (GalleryDisplay display : galleryService.listDisplays(hallId, false)) {
            Relic relic = relicMapper.selectById(display.getRelicId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("display", display);
            row.put("relicNo", relic == null ? null : relic.getRelicNo());
            row.put("relicName", relic == null ? null : relic.getName());
            row.put("material", relic == null ? null : relic.getMaterial());
            displays.add(row);
        }
        report.put("displays", displays);

        Map<Long, Integer> versionNos = new HashMap<>();
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (EnvInspectTask task : taskMapper.selectList(new LambdaQueryWrapper<EnvInspectTask>()
                .eq(EnvInspectTask::getHallId, hallId).orderByAsc(EnvInspectTask::getId))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("task", task);
            row.put("ruleVersionNo", versionNoOf(task.getRuleVersionId(), versionNos));
            row.put("readings", readingsOfTask(task.getId()));
            tasks.add(row);
        }
        report.put("tasks", tasks);

        List<Map<String, Object>> events = new ArrayList<>();
        for (EnvEvent event : eventMapper.selectList(new LambdaQueryWrapper<EnvEvent>()
                .eq(EnvEvent::getHallId, hallId).orderByAsc(EnvEvent::getId))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("event", event);
            row.put("ruleVersionNo", versionNoOf(event.getRuleVersionId(), versionNos));
            events.add(row);
        }
        report.put("events", events);
        return report;
    }

    // --------------------------------------------------------------------

    /** 逐材质评估三项指标，返回 指标 -> 超限说明列表 */
    private Map<String, List<String>> evaluate(EnvReadingRequest req, Map<String, EnvRuleItem> thresholds) {
        Map<String, List<String>> over = new LinkedHashMap<>();
        for (EnvRuleItem item : thresholds.values()) {
            if (req.getTemperature().compareTo(item.getTempMin()) < 0
                    || req.getTemperature().compareTo(item.getTempMax()) > 0) {
                over.computeIfAbsent(METRIC_TEMPERATURE, k -> new ArrayList<>())
                        .add(item.getMaterial() + "：温度" + req.getTemperature()
                                + "℃超出[" + item.getTempMin() + "," + item.getTempMax() + "]");
            }
            if (req.getHumidity().compareTo(item.getHumidityMin()) < 0
                    || req.getHumidity().compareTo(item.getHumidityMax()) > 0) {
                over.computeIfAbsent(METRIC_HUMIDITY, k -> new ArrayList<>())
                        .add(item.getMaterial() + "：湿度" + req.getHumidity()
                                + "%超出[" + item.getHumidityMin() + "," + item.getHumidityMax() + "]");
            }
            if (req.getIlluminance().compareTo(item.getIlluminanceMax()) > 0) {
                over.computeIfAbsent(METRIC_ILLUMINANCE, k -> new ArrayList<>())
                        .add(item.getMaterial() + "：照度" + req.getIlluminance()
                                + "lx超上限" + item.getIlluminanceMax());
            }
        }
        return over;
    }

    /**
     * 连续超限合并：同一展厅同一指标存在未关闭事件（OPEN/HANDLED）时并入该事件，
     * 不另开新事件；若事件已处置（HANDLED）仍超限，视为处置无效，回到 OPEN 重新处置。
     */
    private void mergeOrOpenEvent(EnvInspectTask task, String metric, EnvReading reading, LocalDateTime now) {
        EnvEvent event = eventMapper.selectOne(new LambdaQueryWrapper<EnvEvent>()
                .eq(EnvEvent::getHallId, task.getHallId())
                .eq(EnvEvent::getMetric, metric)
                .in(EnvEvent::getStatus, Arrays.asList(EVENT_OPEN, EVENT_HANDLED))
                .orderByDesc(EnvEvent::getId)
                .last("LIMIT 1"));
        if (event != null) {
            if (EVENT_HANDLED.equals(event.getStatus())) {
                // 处置后再次超限：处置无效，回到待处置，需重新处置并重新复核
                // （updateById 会忽略 null 字段，此处用 UpdateWrapper 显式清空处置痕迹）
                eventMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update
                        .LambdaUpdateWrapper<EnvEvent>()
                        .eq(EnvEvent::getId, event.getId())
                        .set(EnvEvent::getOverCount, event.getOverCount() + 1)
                        .set(EnvEvent::getLastReadingId, reading.getId())
                        .set(EnvEvent::getStatus, EVENT_OPEN)
                        .set(EnvEvent::getHandler, null)
                        .set(EnvEvent::getHandledAt, null)
                        .set(EnvEvent::getHandleNote, null));
            } else {
                event.setOverCount(event.getOverCount() + 1);
                event.setLastReadingId(reading.getId());
                eventMapper.updateById(event);
            }
            return;
        }
        EnvEvent created = new EnvEvent();
        created.setEventNo(nextNo("YC", eventMapper));
        created.setHallId(task.getHallId());
        created.setMetric(metric);
        created.setRuleVersionId(task.getRuleVersionId());
        created.setFirstReadingId(reading.getId());
        created.setLastReadingId(reading.getId());
        created.setOverCount(1);
        created.setStatus(EVENT_OPEN);
        created.setOpenedAt(now);
        eventMapper.insert(created);
    }

    private List<Relic> onDisplayRelics(Long hallId) {
        List<Long> relicIds = galleryService.onDisplay(hallId).stream()
                .map(GalleryDisplay::getRelicId).collect(Collectors.toList());
        if (relicIds.isEmpty()) {
            return new ArrayList<>();
        }
        return relicMapper.selectBatchIds(relicIds);
    }

    private Integer versionNoOf(Long ruleVersionId, Map<Long, Integer> cache) {
        return cache.computeIfAbsent(ruleVersionId,
                id -> ruleService.requireVersion(id).getVersionNo());
    }

    /** 业务编号：前缀-年份-四位序号，数据库唯一约束兜底 */
    private <T> String nextNo(String prefix, com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper) {
        long count = mapper.selectCount(null);
        return String.format("%s-%d-%04d", prefix, LocalDate.now().getYear(), count + 1);
    }
}
