package com.evops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 展厅温湿度与照度巡检（museum-5）验收：
 * 不同材质引用不同阈值与采样频率；计划按展厅生成任务；
 * 连续超限合并为同一事件；异常处置必须关联现场照片与处置人；
 * 事件关闭前必须由保管员复核；规则新版本不改写已完成巡检，报表标明规则版本；
 * 修复审批只完成一半时系统停留待处理。使用独立内存库，不调用起步接口。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:museum-env;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnvInspectionTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void env_inspection_rules_hold() throws Exception {
        // ---- 规则版本 V1：不同材质不同阈值与采样频率 ----
        long v1 = publishRule(ruleJson("馆领导",
                item("青铜", 16, 24, 40, 60, 300, 60) + ","
                        + item("纸质", 16, 22, 45, 60, 50, 30)));
        JsonNode v1Detail = dataOf(getJson("/api/env-rule-versions/" + v1));
        assertEquals(1, v1Detail.get("version").get("versionNo").asInt());
        assertEquals("ACTIVE", v1Detail.get("version").get("status").asText());
        assertEquals(2, v1Detail.get("items").size());

        // 阈值区间倒挂、同版本材质重复：拒绝发布
        expectFail(postJson("/api/env-rule-versions", ruleJson("馆领导",
                item("青铜", 25, 20, 40, 60, 300, 60))), "温度下限不能高于上限");
        expectFail(postJson("/api/env-rule-versions", ruleJson("馆领导",
                item("青铜", 16, 24, 40, 60, 300, 60) + ","
                        + item("青铜", 16, 24, 40, 60, 300, 60))), "材质不能重复");

        // ---- 展厅建档与陈列 ----
        long hall1 = dataOf(postJson("/api/halls",
                "{\"hallNo\":\"H-001\",\"name\":\"一号展厅\",\"location\":\"一层东\"}")).get("id").asLong();
        long hall2 = dataOf(postJson("/api/halls",
                "{\"hallNo\":\"H-002\",\"name\":\"二号展厅\",\"location\":\"一层西\"}")).get("id").asLong();
        expectFail(postJson("/api/halls",
                "{\"hallNo\":\"H-001\",\"name\":\"重复编号厅\"}"), "展厅编号已存在");

        long relicBronze = createRelic("E-001", "青铜鼎", "青铜", "ONE");
        long relicPaper = createRelic("E-002", "古画", "纸质", "TWO");
        long relicJade = createRelic("E-003", "玉佩", "玉", "THREE");
        long relicLent = createRelic("E-004", "外借铜镜", "青铜", "TWO");

        // 外借未还的文物不能上架
        expectOk(postJson("/api/loans", "{\"loanNo\":\"LO-E-001\",\"relicId\":" + relicLent
                + ",\"borrower\":\"兄弟博物馆\"}"));
        expectFail(postJson("/api/halls/" + hall1 + "/displays",
                "{\"relicId\":" + relicLent + "}"), "外借");

        // 正常上架；一件文物不能同时在两个展厅在展
        expectOk(postJson("/api/halls/" + hall1 + "/displays", "{\"relicId\":" + relicBronze + "}"));
        expectOk(postJson("/api/halls/" + hall1 + "/displays", "{\"relicId\":" + relicPaper + "}"));
        expectFail(postJson("/api/halls/" + hall2 + "/displays",
                "{\"relicId\":" + relicBronze + "}"), "已在展厅陈列");

        // 在展材质未被规则版本覆盖：不允许无依据生成巡检计划
        long jadeDisplay = dataOf(postJson("/api/halls/" + hall2 + "/displays",
                "{\"relicId\":" + relicJade + "}")).get("id").asLong();
        expectFail(postJson("/api/env-plans",
                "{\"planDate\":\"2026-10-04\",\"createdBy\":\"保管员丙\"}"), "未覆盖材质");
        expectOk(postEmpty("/api/displays/" + jadeDisplay + "/remove"));

        // ---- 计划按展厅生成任务：空展厅跳过，任务固化规则版本与最严采样频率 ----
        JsonNode plan1 = dataOf(postJson("/api/env-plans",
                "{\"planDate\":\"2026-10-04\",\"createdBy\":\"保管员丙\"}"));
        assertEquals(1, plan1.get("ruleVersionNo").asInt());
        assertEquals(1, plan1.get("tasks").size(), "仅一个有在展文物的展厅生成任务");
        assertEquals(1, plan1.get("skippedHalls").size(), "无在展文物的展厅跳过");
        JsonNode task1 = plan1.get("tasks").get(0);
        long task1Id = task1.get("id").asLong();
        assertEquals("PENDING", task1.get("status").asText());
        assertEquals(30, task1.get("sampleIntervalMinutes").asInt(),
                "采样频率取在展材质中的最严值（纸质30分钟）");

        JsonNode task1Detail = dataOf(getJson("/api/env-tasks/" + task1Id));
        assertEquals(1, task1Detail.get("ruleVersion").get("versionNo").asInt());
        assertEquals(2, task1Detail.get("ruleItems").size());

        // ---- 读数只追加：正常读数不产生事件 ----
        JsonNode r1 = dataOf(postJson("/api/env-tasks/" + task1Id + "/readings",
                readingJson(20, 50, 40, "2026-10-04T09:00:00", "巡检员甲")));
        assertEquals(0, r1.get("overLimit").asInt());
        assertEquals(0, dataOf(getJson("/api/env-events?hallId=" + hall1)).size());

        // ---- 温度超限：按两种材质的阈值评估，开出事件 ----
        JsonNode r2 = dataOf(postJson("/api/env-tasks/" + task1Id + "/readings",
                readingJson(26, 50, 40, "2026-10-04T09:30:00", "巡检员甲")));
        assertEquals(1, r2.get("overLimit").asInt());
        assertTrue(r2.get("overDetail").asText().contains("青铜"), "超限说明须标明材质");
        assertTrue(r2.get("overDetail").asText().contains("纸质"));
        JsonNode events1 = dataOf(getJson("/api/env-events?hallId=" + hall1));
        assertEquals(1, events1.size());
        JsonNode eventA = events1.get(0);
        long eventAId = eventA.get("id").asLong();
        assertEquals("TEMPERATURE", eventA.get("metric").asText());
        assertEquals("OPEN", eventA.get("status").asText());
        assertEquals(1, eventA.get("overCount").asInt());

        // ---- 连续超限合并为同一事件；照度超限另开一事件 ----
        expectOk(postJson("/api/env-tasks/" + task1Id + "/readings",
                readingJson(27, 50, 400, "2026-10-04T10:00:00", "巡检员甲")));
        JsonNode events2 = dataOf(getJson("/api/env-events?hallId=" + hall1));
        assertEquals(2, events2.size(), "温度连续超限合并、照度超限另开事件");
        JsonNode eventAAfter = findByMetric(events2, "TEMPERATURE");
        assertEquals(eventAId, eventAAfter.get("id").asLong(), "连续超限必须并入同一事件");
        assertEquals(2, eventAAfter.get("overCount").asInt());
        long eventBId = findByMetric(events2, "ILLUMINANCE").get("id").asLong();

        // ---- 异常处置：必须关联现场照片与处置人 ----
        expectFail(postJson("/api/env-events/" + eventAId + "/handle",
                "{\"handler\":\"处置员乙\",\"photos\":[]}"), "现场照片");
        expectFail(postJson("/api/env-events/" + eventAId + "/handle",
                "{\"photos\":[{\"photoUrl\":\"p/1.jpg\"}]}"), "handler");
        expectOk(postJson("/api/env-events/" + eventAId + "/handle",
                "{\"handler\":\"处置员乙\",\"handleNote\":\"已调低空调温度\","
                        + "\"photos\":[{\"photoUrl\":\"photos/e1.jpg\",\"photoName\":\"现场空调\"},"
                        + "{\"photoUrl\":\"photos/e2.jpg\"}]}"));
        JsonNode eventAHandled = dataOf(getJson("/api/env-events/" + eventAId));
        assertEquals("HANDLED", eventAHandled.get("event").get("status").asText());
        assertEquals("处置员乙", eventAHandled.get("event").get("handler").asText());
        assertEquals(2, eventAHandled.get("photos").size(), "现场照片须留痕");

        // ---- 事件关闭前必须由保管员复核：OPEN 不能复核，处置后复核才关闭 ----
        expectFail(postJson("/api/env-events/" + eventBId + "/review",
                "{\"reviewer\":\"保管员丙\"}"), "尚未处置");
        expectOk(postJson("/api/env-events/" + eventAId + "/review",
                "{\"reviewer\":\"保管员丙\",\"reviewNote\":\"现场复核属实\"}"));
        JsonNode eventAClosed = dataOf(getJson("/api/env-events/" + eventAId)).get("event");
        assertEquals("CLOSED", eventAClosed.get("status").asText());
        assertEquals("保管员丙", eventAClosed.get("reviewer").asText());
        assertFalse(eventAClosed.get("reviewedAt").isNull(), "复核时间必须留痕");

        // ---- 事件关闭后再超限：另开新事件 ----
        expectOk(postJson("/api/env-tasks/" + task1Id + "/readings",
                readingJson(28, 50, 40, "2026-10-04T10:30:00", "巡检员甲")));
        JsonNode events3 = dataOf(getJson("/api/env-events?hallId=" + hall1));
        assertEquals(3, events3.size(), "已关闭事件不再合并，重新超限开新事件");

        // ---- 处置后仍超限：处置无效，事件回到待处置 ----
        expectOk(postJson("/api/env-events/" + eventBId + "/handle",
                "{\"handler\":\"处置员乙\",\"photos\":[{\"photoUrl\":\"photos/b1.jpg\"}]}"));
        expectOk(postJson("/api/env-tasks/" + task1Id + "/readings",
                readingJson(20, 50, 500, "2026-10-04T11:00:00", "巡检员甲")));
        JsonNode eventBReopen = dataOf(getJson("/api/env-events/" + eventBId)).get("event");
        assertEquals("OPEN", eventBReopen.get("status").asText(), "处置后仍超限须回到待处置");
        assertEquals(2, eventBReopen.get("overCount").asInt());
        assertTrue(eventBReopen.get("handler").isNull(), "处置无效须重新处置");
        // 重新处置 + 保管员复核后关闭
        expectOk(postJson("/api/env-events/" + eventBId + "/handle",
                "{\"handler\":\"处置员乙\",\"photos\":[{\"photoUrl\":\"photos/b2.jpg\"}]}"));
        expectOk(postJson("/api/env-events/" + eventBId + "/review",
                "{\"reviewer\":\"保管员丙\"}"));
        assertEquals("CLOSED", dataOf(getJson("/api/env-events/" + eventBId))
                .get("event").get("status").asText());

        // ---- 完成任务：完成后只读，不接受新读数 ----
        expectOk(postJson("/api/env-tasks/" + task1Id + "/complete",
                "{\"completedBy\":\"巡检员甲\"}"));
        assertEquals("COMPLETED", dataOf(getJson("/api/env-tasks/" + task1Id))
                .get("task").get("status").asText());
        expectFail(postJson("/api/env-tasks/" + task1Id + "/readings",
                readingJson(20, 50, 40, "2026-10-04T11:30:00", "巡检员甲")), "只读");

        // ---- 规则新版本不改写已完成巡检 ----
        long v2 = publishRule(ruleJson("馆领导",
                item("青铜", 16, 26, 40, 60, 300, 45) + ","
                        + item("纸质", 16, 22, 45, 60, 50, 30) + ","
                        + item("玉", 18, 24, 45, 55, 200, 60)));
        JsonNode v1After = dataOf(getJson("/api/env-rule-versions/" + v1)).get("version");
        assertEquals("SUPERSEDED", v1After.get("status").asText(), "旧版本只读保留");
        assertEquals(2, dataOf(getJson("/api/env-rule-versions/current"))
                .get("versionNo").asInt());
        JsonNode task1AfterV2 = dataOf(getJson("/api/env-tasks/" + task1Id));
        assertEquals(1, task1AfterV2.get("ruleVersion").get("versionNo").asInt(),
                "已完成巡检仍引用原规则版本，不被新版本改写");
        assertEquals(24, task1AfterV2.get("ruleItems").get(0).get("tempMax").asInt(),
                "历史版本阈值明细不被新版本改写");

        // 新计划采用新版本
        JsonNode plan2 = dataOf(postJson("/api/env-plans",
                "{\"planDate\":\"2026-10-05\",\"createdBy\":\"保管员丙\"}"));
        assertEquals(2, plan2.get("ruleVersionNo").asInt());
        long task2Id = plan2.get("tasks").get(0).get("id").asLong();
        assertEquals(30, plan2.get("tasks").get(0).get("sampleIntervalMinutes").asInt());

        // ---- 报表标明采用的规则版本，环境指标与巡检计划可追溯 ----
        JsonNode report = dataOf(getJson("/api/halls/" + hall1 + "/env-report"));
        assertEquals("H-001", report.get("hall").get("hallNo").asText());
        assertEquals(2, report.get("tasks").size());
        assertEquals(1, report.get("tasks").get(0).get("ruleVersionNo").asInt());
        assertEquals(2, report.get("tasks").get(1).get("ruleVersionNo").asInt());
        assertTrue(report.get("tasks").get(0).get("readings").size() >= 5,
                "环境读数全程留痕可追溯");
        assertEquals("巡检员甲", report.get("tasks").get(0).get("readings").get(0)
                .get("sampler").asText(), "采样人留痕");
        assertEquals(3, report.get("events").size());
        assertEquals(1, report.get("events").get(0).get("ruleVersionNo").asInt(),
                "事件报表必须标明采用的规则版本");
        assertTrue(report.get("displays").size() >= 2, "陈列记录可追溯");

        // ---- 修复审批只完成一半：系统停留待处理，不能开工 ----
        long relicRepair = createRelic("E-005", "待修陶罐", "陶", "ONE");
        long project = dataOf(postJson("/api/repair-projects",
                "{\"projectNo\":\"RP-E-001\",\"relicId\":" + relicRepair
                        + ",\"client\":\"藏品部\",\"plan\":\"粘接补配\"}")).get("id").asLong();
        long version = dataOf(postJson("/api/repair-projects/" + project + "/plan-versions",
                "{\"planContent\":\"粘接补配\",\"internalExpert\":\"馆内专家甲\","
                        + "\"externalExpert\":\"外部专家乙\",\"custodian\":\"保管员丙\","
                        + "\"submittedBy\":\"项目负责人\"}")).get("version").get("id").asLong();
        // 一级文物顺序：外部专家 -> 馆内专家 -> 保管人员；只签第一步（审批一半）
        expectOk(postJson("/api/plan-versions/" + version + "/sign",
                "{\"signerRole\":\"EXTERNAL\",\"signerName\":\"外部专家乙\",\"decision\":\"APPROVE\"}"));
        JsonNode halfSigned = dataOf(getJson("/api/plan-versions/" + version)).get("version");
        assertEquals("SIGNING", halfSigned.get("status").asText(),
                "审批只完成一半时版本停留会签中（待处理）");
        expectFail(postEmpty("/api/repair-projects/" + project + "/start"), "会签未完成");
        assertEquals("PLANNED", dataOf(getJson("/api/relics/" + relicRepair))
                .get("repairProjects").get(0).get("status").asText(),
                "审批未完成，项目停留待处理");
    }

    // ------------------------------------------------------------------

    private JsonNode findByMetric(JsonNode events, String metric) {
        for (JsonNode e : events) {
            if (metric.equals(e.get("metric").asText())) {
                return e;
            }
        }
        throw new AssertionError("未找到指标事件: " + metric);
    }

    private String item(String material, int tMin, int tMax, int hMin, int hMax,
                        int luxMax, int interval) {
        return "{\"material\":\"" + material + "\",\"tempMin\":" + tMin + ",\"tempMax\":" + tMax
                + ",\"humidityMin\":" + hMin + ",\"humidityMax\":" + hMax
                + ",\"illuminanceMax\":" + luxMax + ",\"sampleIntervalMinutes\":" + interval + "}";
    }

    private String ruleJson(String publishedBy, String items) {
        return "{\"publishedBy\":\"" + publishedBy + "\",\"items\":[" + items + "]}";
    }

    private String readingJson(int temp, int humidity, int lux, String sampledAt, String sampler) {
        return "{\"temperature\":" + temp + ",\"humidity\":" + humidity
                + ",\"illuminance\":" + lux + ",\"sampledAt\":\"" + sampledAt
                + "\",\"sampler\":\"" + sampler + "\"}";
    }

    private long publishRule(String body) throws Exception {
        return dataOf(postJson("/api/env-rule-versions", body)).get("version").get("id").asLong();
    }

    private long createRelic(String relicNo, String name, String material, String grade) throws Exception {
        return dataOf(postJson("/api/relics", "{\"relicNo\":\"" + relicNo + "\",\"name\":\"" + name
                + "\",\"material\":\"" + material + "\",\"era\":\"清代\",\"source\":\"征集\","
                + "\"preservationGrade\":\"" + grade + "\"}")).get("id").asLong();
    }

    private MvcResult postJson(String uri, String body) throws Exception {
        return mockMvc.perform(post(uri)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
    }

    private MvcResult postEmpty(String uri) throws Exception {
        return mockMvc.perform(post(uri).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
    }

    private MvcResult getJson(String uri) throws Exception {
        return mockMvc.perform(get(uri)).andExpect(status().isOk()).andReturn();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .get("data");
    }

    private void expectOk(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(root.get("success").asBoolean(),
                "期望成功，实际: " + root.get("message").asText());
    }

    private void expectFail(MvcResult result, String messageContains) throws Exception {
        JsonNode root = objectMapper.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertFalse(root.get("success").asBoolean());
        assertTrue(root.get("message").asText().contains(messageContains),
                "错误信息应包含「" + messageContains + "」，实际: " + root.get("message").asText());
    }
}
