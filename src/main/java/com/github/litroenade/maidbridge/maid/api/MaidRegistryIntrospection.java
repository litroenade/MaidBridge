package com.github.litroenade.maidbridge.maid.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class MaidRegistryIntrospection {
    public static final String TOUHOU_LITTLE_MAID_MOD_ID = "touhou_little_maid";

    private static final String TOOL_REGISTER = "com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ToolRegister";
    private static final String OBJECT_PARAMETER = "com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter";
    private static final String GAME_CONTEXT_REGISTER = "com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister";
    private static final String SKILL_LOADER = "com.github.tartaricacid.touhoulittlemaid.ai.agent.skill.SkillLoader";
    private static final String TASK_MANAGER = "com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager";
    private static final String AVAILABLE_SITES = "com.github.tartaricacid.touhoulittlemaid.ai.manager.site.AvailableSites";

    private MaidRegistryIntrospection() {
    }

    public static Map<?, ?> toolMap() {
        return requireMap(MaidApiReflection.staticInvoke(TOOL_REGISTER, "getAllTools"), "ToolRegister.getAllTools");
    }

    public static Object toolById(String toolId) {
        return MaidApiReflection.staticInvoke(TOOL_REGISTER, "getTool", toolId);
    }

    public static Object objectParameterRoot() {
        return MaidApiReflection.staticInvoke(OBJECT_PARAMETER, "create");
    }

    public static Map<?, ?> skillMap() {
        return requireMap(MaidApiReflection.staticInvoke(SKILL_LOADER, "getAllSkills"), "SkillLoader.getAllSkills");
    }

    public static Object skillById(String skillId) {
        return MaidApiReflection.staticInvoke(SKILL_LOADER, "getSkill", skillId);
    }

    public static Collection<?> toolContextCategories() {
        return requireCollection(MaidApiReflection.staticInvoke(GAME_CONTEXT_REGISTER, "allToolCategories"), "GameContextRegister.allToolCategories");
    }

    public static Collection<?> promptContextCategories() {
        return requireCollection(MaidApiReflection.staticInvoke(GAME_CONTEXT_REGISTER, "allPromptCategories"), "GameContextRegister.allPromptCategories");
    }

    public static Object context(String categoryId, Object maid) {
        return MaidApiReflection.staticInvoke(GAME_CONTEXT_REGISTER, "getContext", categoryId, maid);
    }

    public static Object contextKeys(String categoryId) {
        return MaidApiReflection.staticInvoke(GAME_CONTEXT_REGISTER, "getContextKeys", categoryId);
    }

    public static boolean hasContextCategory(String categoryId) {
        return Boolean.TRUE.equals(MaidApiReflection.staticInvoke(GAME_CONTEXT_REGISTER, "hasCategory", categoryId));
    }

    public static Object contextsField() {
        return MaidApiReflection.staticField(GAME_CONTEXT_REGISTER, "CONTEXTS");
    }

    public static Map<?, ?> taskMap() {
        return requireMap(MaidApiReflection.staticInvoke(TASK_MANAGER, "getTaskMap"), "TaskManager.getTaskMap");
    }

    public static String taskManagerClassName() {
        return TASK_MANAGER;
    }

    public static Object sitesField(String fieldName) {
        return MaidApiReflection.staticField(AVAILABLE_SITES, fieldName);
    }

    public static Map<?, ?> requireMap(Object value, String source) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalStateException("读取注册表目录失败：" + source + "：" + clean(value));
    }

    public static Collection<?> requireCollection(Object value, String source) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        throw new IllegalStateException("读取注册表目录失败：" + source + "：" + clean(value));
    }

    public static List<String> collectionValue(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream().map(MaidRegistryIntrospection::clean).toList();
    }

    public static String firstNonBlank(Object... values) {
        for (Object value : values) {
            String text = clean(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    public static String clean(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.startsWith("<reflect-error:")) {
            return "";
        }
        return text;
    }
}
