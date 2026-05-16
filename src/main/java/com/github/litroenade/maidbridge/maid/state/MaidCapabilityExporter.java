package com.github.litroenade.maidbridge.maid.state;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.maid.api.MaidApiReflection;
import com.github.litroenade.maidbridge.maid.api.MaidRegistryIntrospection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.litroenade.maidbridge.maid.api.MaidRegistryIntrospection.clean;
import static com.github.litroenade.maidbridge.maid.api.MaidRegistryIntrospection.firstNonBlank;

/**
 * 导出外部 agent 本轮可见的女仆能力。
 * <p>这里只暴露本轮回写会用到的动作 schema，以及只读的 TLM 工具摘要。</p>
 */
public final class MaidCapabilityExporter {
    private MaidCapabilityExporter() {
    }

    public static Map<String, Object> compactTurnAffordances(Object maid) {
        var capabilities = new LinkedHashMap<String, Object>();
        capabilities.put("tools", tools(maid));
        capabilities.put("action_schema", actionSchema());
        return capabilities;
    }

    private static Map<String, Object> tools(Object maid) {
        Map<?, ?> tools;
        try {
            tools = MaidRegistryIntrospection.toolMap();
        } catch (RuntimeException exception) {
            return Map.of("count", 0, "items", List.of());
        }
        var items = new ArrayList<Map<String, Object>>();
        tools.forEach((key, tool) -> items.add(toolSchema(clean(firstNonBlank(
                MaidApiReflection.invoke(tool, "id"), key)), tool, maid)));
        return Map.of("count", items.size(), "items", items);
    }

    private static Map<String, Object> toolSchema(String fallbackId, Object tool, Object maid) {
        String id = clean(firstNonBlank(MaidApiReflection.invoke(tool, "id"), fallbackId));
        Object root = MaidRegistryIntrospection.objectParameterRoot();
        Object parameter = MaidApiReflection.invoke(tool, "parameters", root, maid);
        var item = new LinkedHashMap<String, Object>();
        item.put("id", id);
        item.put("summary", clean(MaidApiReflection.invoke(tool, "summary", maid)));
        item.put("parameters", MaidApiReflection.jsonCompatible(parameter));
        return item;
    }

    private static Map<String, Object> actionSchema() {
        var actions = new ArrayList<Map<String, Object>>();
        actions.add(action("switch_sit", "切换女仆坐下或站起。", Map.of(
                "type", "object",
                "required", List.of("sit"),
                "properties", Map.of("sit", Map.of("type", "boolean"))
        )));
        actions.add(action("switch_follow_state", "切换女仆跟随或原地活动。", Map.of(
                "type", "object",
                "required", List.of("follow"),
                "properties", Map.of("follow", Map.of("type", "boolean"))
        )));
        actions.add(action("switch_schedule", "设置女仆日程模式。", Map.of(
                "type", "object",
                "required", List.of("schedule"),
                "properties", Map.of("schedule", Map.of("type", "string", "enum", List.of("DAY", "NIGHT", "ALL")))
        )));
        var taskChoices = taskChoices();
        actions.add(action("switch_work_task", "设置女仆工作任务。", Map.of(
                "type", "object",
                "required", List.of("task_id"),
                "properties", Map.of(
                        "task_id", Map.of("type", "string", "enum", taskIds(taskChoices)),
                        "entity_id", Map.of("type", "integer", "description", "攻击任务可选的目标实体 ID。")
                )
        ), taskChoices));
        if (Config.enableExternalAgentEmoji) {
            actions.add(action("show_emoji_bubble", "附加 TLM 本地随机图片或颜文字气泡；不是 MaiBot 外部表情包发送。", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "kind", Map.of("type", "string", "enum", List.of("image", "kaomoji"), "description", "image 为 TLM 本地随机图片，kaomoji 为按女仆当前状态选择颜文字。")
                    )
            )));
        }
        return Map.of("count", actions.size(), "items", actions);
    }

    private static Map<String, Object> action(String id, String summary, Map<String, Object> parameters) {
        return action(id, summary, parameters, List.of());
    }

    private static Map<String, Object> action(String id, String summary, Map<String, Object> parameters, List<Map<String, Object>> choices) {
        var action = new LinkedHashMap<String, Object>();
        action.put("id", id);
        action.put("summary", summary);
        action.put("parameters", parameters);
        if (!choices.isEmpty()) {
            action.put("choices", choices);
        }
        return action;
    }

    private static List<Map<String, Object>> taskChoices() {
        Map<?, ?> tasks;
        try {
            tasks = MaidRegistryIntrospection.taskMap();
        } catch (RuntimeException exception) {
            return List.of();
        }
        var choices = new ArrayList<Map<String, Object>>();
        tasks.forEach((key, task) -> {
            String id = clean(firstNonBlank(MaidApiReflection.invoke(task, "getUid"), key));
            if (!id.isBlank()) {
                var choice = new LinkedHashMap<String, Object>();
                choice.put("id", id);
                choice.put("summary", clean(MaidApiReflection.invoke(task, "getMaidActionSummary")));
                choices.add(choice);
            }
        });
        return choices;
    }

    private static List<String> taskIds(List<Map<String, Object>> choices) {
        var ids = new ArrayList<String>();
        for (Map<String, Object> choice : choices) {
            String id = clean(choice.get("id"));
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        return ids;
    }

}
