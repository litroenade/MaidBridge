package com.github.litroenade.maidbridge.maid.api;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.trace.ReflectiveAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MaidActionExecutor {
    private static final String ENTITY_MAID = "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid";
    private static final String I_MAID_TASK = "com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask";
    private static final String I_ATTACK_TASK = "com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask";
    private static final String I_CHAT_BUBBLE_DATA = "com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData";
    private static final String CHAT_BUBBLE_MANAGER = "com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleManager";
    private static final String EMOJI_CHAT_BUBBLE_DATA = "com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.EmojiChatBubbleData";
    private static final String KAOMOJI_DATA = "com.github.tartaricacid.touhoulittlemaid.datapack.KaomojiData";
    private static final String MAID_SCHEDULE = "com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule";
    private static final String MAID_CONFIG = "com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig";

    private MaidActionExecutor() {
    }

    public static List<Map<String, Object>> applyAll(Entity maid, List<Object> actions) {
        validateAll(actions);
        var results = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < actions.size(); index++) {
            results.add(apply(maid, actionMap(actions.get(index)), index));
        }
        return List.copyOf(results);
    }

    public static Map<String, Object> apply(Entity maid, Map<String, Object> action) {
        validate(action);
        return apply(maid, action, 0);
    }

    public static void validateAll(List<Object> actions) {
        for (Object action : actions) {
            validate(actionMap(action));
        }
    }

    public static void validate(Map<String, Object> action) {
        var type = canonicalType(action);
        switch (type) {
            case "switch_sit" -> requireBoolean(action, "sit", "value", "enabled");
            case "switch_follow_state" -> requireBoolean(action, "follow", "value", "enabled");
            case "switch_schedule" -> requireString(action, "schedule", "value");
            case "switch_work_task" -> {
                findTask(requireString(action, "task_id", "id", "value"));
                optionalInt(action, "entity_id", "target_entity_id");
            }
            case "show_emoji_bubble" -> {
                ensureExternalEmojiEnabled();
                externalEmojiKind(action);
            }
            default -> throw new IllegalArgumentException("不支持的女仆动作类型：" + type);
        }
    }

    private static Map<String, Object> apply(Entity maid, Map<String, Object> action, int index) {
        var type = canonicalType(action);
        var result = new LinkedHashMap<String, Object>();
        result.put("index", index);
        result.put("type", type);
        switch (type) {
            case "switch_sit" -> result.putAll(applySit(maid, requireBoolean(action, "sit", "value", "enabled")));
            case "switch_follow_state" -> result.putAll(applyFollow(maid, requireBoolean(action, "follow", "value", "enabled")));
            case "switch_schedule" -> result.putAll(applySchedule(maid, requireString(action, "schedule", "value")));
            case "switch_work_task" -> result.putAll(applyTask(maid, action));
            case "show_emoji_bubble" -> result.putAll(applyEmojiBubble(maid, action));
            default -> throw new IllegalArgumentException("不支持的女仆动作类型：" + type);
        }
        return result;
    }

    private static Map<String, Object> applySit(Entity maid, boolean sit) {
        var wasSitting = invokeBoolean(maid, "isMaidInSittingPose");
        if (wasSitting != sit) {
            invoke(maid, "setInSittingPose", new Class<?>[]{boolean.class}, sit);
        }
        return Map.of(
                "requested_sit", sit,
                "previous_sit", wasSitting,
                "changed", wasSitting != sit
        );
    }

    private static Map<String, Object> applyFollow(Entity maid, boolean follow) {
        var wasHomeMode = invokeBoolean(maid, "isHomeModeEnable");
        if (follow) {
            if (wasHomeMode) {
                invoke(maid, "restrictTo", new Class<?>[]{BlockPos.class, int.class}, BlockPos.ZERO, nonHomeRange());
                invoke(maid, "setHomeModeEnable", new Class<?>[]{boolean.class}, false);
            }
        } else if (!wasHomeMode) {
            Object schedulePos = invoke(maid, "getSchedulePos", new Class<?>[]{});
            Class<?> maidClass = classForName(ENTITY_MAID);
            Method setHome = method(schedulePos.getClass(), "setHomeModeEnable", maidClass, BlockPos.class);
            try {
                setHome.invoke(schedulePos, maid, maid.blockPosition());
            } catch (IllegalAccessException exception) {
                throw new IllegalArgumentException("设置女仆 home mode 位置失败", exception);
            } catch (InvocationTargetException exception) {
                throw rethrowInvocation("SchedulePos.setHomeModeEnable", exception);
            }
            invoke(maid, "setHomeModeEnable", new Class<?>[]{boolean.class}, true);
        }
        return Map.of(
                "requested_follow", follow,
                "previous_home_mode", wasHomeMode,
                "changed", follow == wasHomeMode
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> applySchedule(Entity maid, String schedule) {
        Class<?> scheduleType = classForName(MAID_SCHEDULE);
        Object target = Enum.valueOf((Class<Enum>) scheduleType.asSubclass(Enum.class), schedule.trim().toUpperCase(Locale.ROOT));
        Object current = invoke(maid, "getSchedule", new Class<?>[]{});
        if (current != target) {
            invoke(maid, "setSchedule", new Class<?>[]{scheduleType}, target);
        }
        return Map.of(
                "requested_schedule", String.valueOf(target),
                "previous_schedule", String.valueOf(current),
                "changed", current != target
        );
    }

    private static Map<String, Object> applyTask(Entity maid, Map<String, Object> action) {
        String taskId = requireString(action, "task_id", "id", "value");
        Object task = findTask(taskId);
        Object currentTask = ReflectiveAccess.invoke(maid, "getTask");
        var sameTask = task == currentTask;
        if (!sameTask) {
            invokeSetTask(maid, task);
        }
        Object switchResult = invokeTaskSwitch(task, maid);
        var result = new LinkedHashMap<String, Object>();
        result.put("task_id", taskId);
        result.put("same_task", sameTask);
        result.put("switch_result", String.valueOf(switchResult));
        result.putAll(applyAttackTargetIfRequested(maid, task, optionalInt(action, "entity_id", "target_entity_id")));
        return result;
    }

    @SuppressWarnings("resource")
    private static Map<String, Object> applyAttackTargetIfRequested(Entity maid, Object task, int entityId) {
        if (!classForName(I_ATTACK_TASK).isInstance(task) || entityId < 0) {
            return Map.of();
        }
        // 只按实体 ID 查询攻击目标；世界对象由 Minecraft 生命周期持有，不能主动关闭。
        Entity targetEntity = maid.level().getEntity(entityId);
        if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
            return Map.of(
                    "target_entity_id", entityId,
                    "target_result", "target_not_found"
            );
        }
        Object previousTarget = invoke(maid, "getLastHurtByMob", new Class<?>[]{});
        invoke(maid, "setLastHurtByMob", new Class<?>[]{LivingEntity.class}, target);
        if (!canAttack(task, maid, target)) {
            invoke(maid, "setLastHurtByMob", new Class<?>[]{LivingEntity.class}, previousTarget);
            return Map.of(
                    "target_entity_id", entityId,
                    "target_name", target.getName().getString(),
                    "target_result", "target_not_allowed"
            );
        }
        if (!(maid instanceof Mob mob)) {
            throw new IllegalArgumentException("女仆实体不支持 Brain 记忆");
        }
        mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        return Map.of(
                "target_entity_id", entityId,
                "target_name", target.getName().getString(),
                "target_result", "target_success"
        );
    }

    private static Map<String, Object> applyEmojiBubble(Entity maid, Map<String, Object> action) {
        ensureExternalEmojiEnabled();
        String kind = externalEmojiKind(action);
        Object bubbleManager = invoke(maid, "getChatBubbleManager", new Class<?>[]{});
        int previousBubbles = chatBubbleCount(bubbleManager);
        var result = new LinkedHashMap<String, Object>();
        result.put("kind", kind);
        result.put("previous_bubbles", previousBubbles);
        if ("kaomoji".equals(kind)) {
            applyRoutineKaomoji(maid, bubbleManager);
        } else {
            long bubbleId = addRandomImageEmoji(bubbleManager);
            result.put("bubble_id", bubbleId);
        }
        result.put("current_bubbles", chatBubbleCount(bubbleManager));
        return result;
    }

    private static long addRandomImageEmoji(Object bubbleManager) {
        try {
            Object bubble = method(classForName(EMOJI_CHAT_BUBBLE_DATA), "create").invoke(null);
            Object key = invoke(bubbleManager, "addChatBubble", new Class<?>[]{classForName(I_CHAT_BUBBLE_DATA)}, bubble);
            if (key instanceof Number number) {
                return number.longValue();
            }
            throw new IllegalArgumentException("ChatBubbleManager.addChatBubble 未返回气泡 key");
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("创建女仆图片表情气泡失败", exception);
        } catch (InvocationTargetException exception) {
            throw rethrowInvocation("EmojiChatBubbleData.create", exception);
        }
    }

    private static void applyRoutineKaomoji(Entity maid, Object bubbleManager) {
        Class<?> maidType = classForName(ENTITY_MAID);
        Class<?> bubbleManagerType = classForName(CHAT_BUBBLE_MANAGER);
        if (!maidType.isInstance(maid)) {
            throw new IllegalArgumentException("表情气泡只能用于 TouhouLittleMaid 女仆实体");
        }
        if (!bubbleManagerType.isInstance(bubbleManager)) {
            throw new IllegalArgumentException("女仆聊天气泡管理器类型不匹配");
        }
        try {
            method(classForName(KAOMOJI_DATA), "showRoutineKaomoji", maidType, bubbleManagerType).invoke(null, maid, bubbleManager);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("添加女仆颜文字气泡失败", exception);
        } catch (InvocationTargetException exception) {
            throw rethrowInvocation("KaomojiData.showRoutineKaomoji", exception);
        }
    }

    private static int chatBubbleCount(Object bubbleManager) {
        Object collection = invoke(bubbleManager, "getChatBubbleDataCollection", new Class<?>[]{});
        Object size = invoke(collection, "size", new Class<?>[]{});
        if (size instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("ChatBubbleDataCollection.size 未返回数字");
    }

    private static Map<String, Object> actionMap(Object action) {
        if (!(action instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("女仆动作必须是对象");
        }
        var map = new LinkedHashMap<String, Object>();
        for (var entry : raw.entrySet()) {
            map.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return map;
    }

    private static String canonicalType(Map<String, Object> action) {
        var raw = requireString(action, "type", "action", "tool_id", "name")
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace('.', '_');
        return switch (raw) {
            case "sit", "set_sit", "sitting" -> "switch_sit";
            case "follow", "set_follow", "following" -> "switch_follow_state";
            case "schedule", "set_schedule" -> "switch_schedule";
            case "task", "work", "work_task", "set_task" -> "switch_work_task";
            case "emoji", "emoji_bubble", "show_emoji", "show_emoji_bubble", "show_maid_emoji", "show_external_emoji" -> "show_emoji_bubble";
            default -> raw;
        };
    }

    private static void ensureExternalEmojiEnabled() {
        if (!Config.enableExternalAgentEmoji) {
            throw new IllegalArgumentException("外部女仆 agent 表情气泡未启用");
        }
    }

    private static String externalEmojiKind(Map<String, Object> action) {
        String raw = optionalString(action, "kind", "emoji_kind", "mode", "value");
        if (raw.isBlank()) {
            return "image";
        }
        String normalized = raw.toLowerCase(Locale.ROOT).replace('-', '_').replace('.', '_');
        return switch (normalized) {
            case "image", "random_image", "emoji", "sticker" -> "image";
            case "kaomoji", "text", "routine_kaomoji" -> "kaomoji";
            default -> throw new IllegalArgumentException("不支持的女仆表情气泡类型：" + raw);
        };
    }

    private static String requireString(Map<String, Object> action, String... keys) {
        String value = MaidApiReflection.stringValue(action, keys);
        if (!value.isBlank()) {
            return value;
        }
        throw new IllegalArgumentException("女仆动作缺少必要字段，需提供以下之一：" + String.join(", ", keys));
    }

    private static String optionalString(Map<String, Object> action, String... keys) {
        return MaidApiReflection.stringValue(action, keys);
    }

    private static boolean requireBoolean(Map<String, Object> action, String... keys) {
        for (String key : keys) {
            Object value = action.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String text && !text.isBlank()) {
                if ("true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text) || "1".equals(text)) {
                    return true;
                }
                if ("false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text) || "0".equals(text)) {
                    return false;
                }
            }
        }
        throw new IllegalArgumentException("女仆动作布尔字段无效，需提供以下之一：" + String.join(", ", keys));
    }

    private static int optionalInt(Map<String, Object> action, String... keys) {
        for (String key : keys) {
            Object value = action.get(key);
            if (value == null || String.valueOf(value).trim().isBlank()) {
                continue;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("女仆动作整数字段无效：" + key, exception);
            }
        }
        return -1;
    }

    private static int nonHomeRange() {
        try {
            Class<?> config = classForName(MAID_CONFIG);
            Field field = config.getField("MAID_NON_HOME_RANGE");
            Object value = field.get(null);
            Object range = method(value.getClass(), "get").invoke(value);
            if (range instanceof Number number) {
                return number.intValue();
            }
            throw new IllegalArgumentException("MAID_NON_HOME_RANGE.get() 未返回数字");
        } catch (IllegalAccessException | NoSuchFieldException exception) {
            throw new IllegalArgumentException("读取 MAID_NON_HOME_RANGE 失败", exception);
        } catch (InvocationTargetException exception) {
            throw rethrowInvocation("MAID_NON_HOME_RANGE.get", exception);
        }
    }

    private static Object findTask(String taskId) {
        try {
            Class<?> managerType = classForName(MaidRegistryIntrospection.taskManagerClassName());
            Method findTask = method(managerType, "findTask", ResourceLocation.class);
            Object optional = findTask.invoke(null, ResourceLocation.parse(taskId));
            if (optional instanceof Optional<?> task && task.isPresent()) {
                return task.get();
            }
            throw new IllegalArgumentException("未知 task_id：" + taskId);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("解析 task_id 失败：" + taskId, exception);
        } catch (InvocationTargetException exception) {
            throw rethrowInvocation("TaskManager.findTask", exception);
        }
    }

    private static void invokeSetTask(Object maid, Object task) {
        Class<?> taskType = classForName(I_MAID_TASK);
        invoke(maid, "setTask", new Class<?>[]{taskType}, task);
    }

    private static Object invokeTaskSwitch(Object task, Object maid) {
        Class<?> maidType = classForName(ENTITY_MAID);
        Class<?> taskType = classForName(I_MAID_TASK);
        try {
            return method(taskType, "onFunctionCallSwitch", maidType).invoke(task, maid);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("执行女仆动作切换 hook 失败", exception);
        } catch (InvocationTargetException exception) {
            throw rethrowInvocation("IMaidTask.onFunctionCallSwitch", exception);
        }
    }

    private static boolean canAttack(Object task, Object maid, LivingEntity target) {
        Class<?> maidType = classForName(ENTITY_MAID);
        Class<?> attackTaskType = classForName(I_ATTACK_TASK);
        try {
            Object value = method(attackTaskType, "canAttack", maidType, LivingEntity.class).invoke(task, maid, target);
            if (value instanceof Boolean bool) {
                return bool;
            }
            throw new IllegalArgumentException("IAttackTask.canAttack 未返回布尔值");
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("执行女仆攻击目标检查失败", exception);
        } catch (InvocationTargetException exception) {
            throw rethrowInvocation("IAttackTask.canAttack", exception);
        }
    }

    private static boolean invokeBoolean(Object target, String methodName) {
        Object value = invoke(target, methodName, new Class<?>[]{});
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException(methodName + " 未返回布尔值");
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            return method(target.getClass(), methodName, parameterTypes).invoke(target, args);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("调用失败：" + methodName, exception);
        } catch (InvocationTargetException exception) {
            throw rethrowInvocation(methodName, exception);
        }
    }

    private static Method method(Class<?> type, String methodName, Class<?>... parameterTypes) {
        if (type == null) {
            throw new IllegalArgumentException("方法所属类型不能为空：" + methodName);
        }
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalArgumentException("未找到方法：" + type.getName() + "." + methodName);
    }

    private static Class<?> classForName(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            throw new IllegalArgumentException("未找到类：" + name, exception);
        }
    }

    private static RuntimeException rethrowInvocation(String methodName, InvocationTargetException exception) {
        var cause = exception.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalArgumentException("调用失败：" + methodName, cause);
    }
}
