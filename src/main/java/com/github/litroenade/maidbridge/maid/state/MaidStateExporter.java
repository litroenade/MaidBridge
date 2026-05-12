package com.github.litroenade.maidbridge.maid.state;

import com.github.litroenade.maidbridge.maid.api.MaidRegistryIntrospection;
import com.github.litroenade.maidbridge.trace.ReflectiveAccess;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导出外部 agent 回合可见的女仆现场。
 * <p>state 对齐 TLM 每轮 prompt context；action_context 放动作规划额外需要的目标信息。</p>
 */
public final class MaidStateExporter {
    // 与 TLM nearby_entities 工具保持同一上限，避免外部 agent 看到比原生工具更宽的目标集合。
    private static final int ACTION_CONTEXT_ENTITY_LIMIT = 20;

    private MaidStateExporter() {
    }

    public static Map<String, Object> compactIdentity(Object maid) {
        var identity = new LinkedHashMap<String, Object>();
        identity.put("uuid", clean(ReflectiveAccess.invoke(maid, "getUUID")));
        String displayName = ReflectiveAccess.componentText(ReflectiveAccess.invoke(maid, "getName"));
        putIfPresent(identity, "name", displayName);
        putIfPresent(identity, "display_name", displayName);
        if (maid instanceof EntityMaid entityMaid) {
            var customName = entityMaid.getCustomName();
            if (customName != null) {
                putIfPresent(identity, "custom_name", customName.getString());
            }
        }
        putIfPresent(identity, "model_id", clean(ReflectiveAccess.invoke(maid, "getModelId")));
        putIfPresent(identity, "model_name", ReflectiveAccess.componentText(ReflectiveAccess.invoke(maid, "getTypeName")));
        return identity;
    }

    /**
     * 导出本轮已被动观察到的事实；不要把按需查询或动作目标混进 state。
     */
    public static Map<String, Object> compactTurnState(Object maid) {
        var state = new LinkedHashMap<String, Object>();
        state.put("prompt_context", promptContext(maid));
        state.put("status", status(maid));
        state.put("behavior", behavior(maid));
        putIfNotEmpty(state, "world", world(maid));
        return state;
    }

    /**
     * 导出动作规划所需的现场辅助信息，避免污染 TLM 原生 prompt context 语义。
     */
    public static Map<String, Object> actionContext(Object maid) {
        var context = new LinkedHashMap<String, Object>();
        if (maid instanceof EntityMaid entityMaid) {
            context.put("nearby_living_entities", nearbyLivingEntities(entityMaid));
        }
        return context;
    }

    private static Map<String, Object> promptContext(Object maid) {
        var payload = new LinkedHashMap<String, Object>();
        var categories = new ArrayList<Map<String, Object>>();
        if (maid instanceof EntityMaid entityMaid) {
            for (Object category : MaidRegistryIntrospection.promptContextCategories()) {
                String id = clean(ReflectiveAccess.invoke(category, "id"));
                if (id.isBlank()) {
                    continue;
                }
                var lines = stringList(MaidRegistryIntrospection.context(id, entityMaid));
                if (lines.isEmpty()) {
                    continue;
                }
                var item = new LinkedHashMap<String, Object>();
                item.put("id", id);
                item.put("summary", clean(ReflectiveAccess.invoke(category, "summary")));
                item.put("lines", lines);
                categories.add(item);
            }
        }
        payload.put("categories", categories);
        return payload;
    }

    private static Map<String, Object> status(Object maid) {
        var status = new LinkedHashMap<String, Object>();
        if (maid instanceof Entity entity) {
            status.put("alive", entity.isAlive());
            Object vehicle = entity.getVehicle();
            if (vehicle instanceof Entity vehicleEntity) {
                var riding = new LinkedHashMap<String, Object>();
                riding.put("entity_id", vehicleEntity.getId());
                riding.put("type", BuiltInRegistries.ENTITY_TYPE.getKey(vehicleEntity.getType()).toString());
                status.put("riding", riding);
            }
        }
        if (maid instanceof LivingEntity living) {
            status.put("health", Map.of(
                    "current", living.getHealth(),
                    "max", living.getMaxHealth()
            ));
            status.put("sleeping", living.isSleeping());
        }
        Object sitting = ReflectiveAccess.invoke(maid, "isMaidInSittingPose");
        if (sitting instanceof Boolean bool) {
            status.put("sitting", bool);
        }
        return status;
    }

    private static Map<String, Object> behavior(Object maid) {
        var behavior = new LinkedHashMap<String, Object>();
        Object homeMode = ReflectiveAccess.invoke(maid, "isHomeModeEnable");
        if (homeMode instanceof Boolean value) {
            behavior.put("movement", value ? "home" : "following");
        }
        putIfPresent(behavior, "schedule", clean(ReflectiveAccess.invoke(maid, "getSchedule")));
        putIfPresent(behavior, "activity", scheduleDetailName(ReflectiveAccess.invoke(maid, "getScheduleDetail")));
        putIfNotEmpty(behavior, "task", task(ReflectiveAccess.invoke(maid, "getTask")));
        return behavior;
    }

    @SuppressWarnings("resource")
    private static Map<String, Object> world(Object maid) {
        if (!(maid instanceof Entity entity)) {
            return Map.of();
        }
        var world = new LinkedHashMap<String, Object>();
        // Level 由 Minecraft 生命周期持有，这里只通过实体入口读取，不持有或关闭世界引用。
        long dayTime = entity.level().getDayTime();
        long hours = (dayTime / 1000 + 6) % 24;
        long minutes = (dayTime % 1000) / (50 / 3);
        world.put("day_time", dayTime);
        world.put("clock", "%02d:%02d".formatted(hours, minutes));
        world.put("weather", weather(entity));
        world.put("dimension", entity.level().dimension().location().toString());
        ResourceLocation biome = biome(entity);
        if (biome != null) {
            world.put("biome", biome.toString());
        }
        return world;
    }

    @SuppressWarnings("resource")
    private static List<Map<String, Object>> nearbyLivingEntities(EntityMaid maid) {
        var box = maid.searchDimension();
        // Level 由 Minecraft 生命周期持有，这里只通过女仆入口读取，不持有或关闭世界引用。
        var entities = new ArrayList<>(maid.level().getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity != maid && entity.isAlive()
        ));
        entities.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(maid)));
        var items = new ArrayList<Map<String, Object>>();
        var owner = maid.getOwner();
        for (LivingEntity entity : entities) {
            if (items.size() >= ACTION_CONTEXT_ENTITY_LIMIT) {
                break;
            }
            var payload = new LinkedHashMap<String, Object>();
            payload.put("entity_id", entity.getId());
            payload.put("uuid", entity.getUUID().toString());
            payload.put("name", entity.getName().getString());
            payload.put("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            payload.put("distance_to_self", roundDistance(maid.distanceTo(entity)));
            if (owner != null) {
                payload.put("distance_to_owner", roundDistance(owner.distanceTo(entity)));
            }
            items.add(payload);
        }
        return items;
    }

    private static Map<String, Object> task(Object task) {
        if (unavailable(task)) {
            return Map.of();
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("id", clean(ReflectiveAccess.invoke(task, "getUid")));
        payload.put("description", clean(ReflectiveAccess.invoke(task, "getMaidActionSummary")));
        return payload;
    }

    private static String scheduleDetailName(Object scheduleDetail) {
        String name = clean(ReflectiveAccess.invoke(scheduleDetail, "getName"));
        return name.isBlank() ? clean(scheduleDetail) : name;
    }

    @SuppressWarnings("resource")
    private static String weather(Entity entity) {
        if (entity.level().isThundering()) {
            return "thundering";
        }
        if (entity.level().isRaining()) {
            return "raining";
        }
        return "sunny";
    }

    @SuppressWarnings("resource")
    private static ResourceLocation biome(Entity entity) {
        Biome biome = entity.level().getBiome(entity.blockPosition()).value();
        return entity.level().registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
    }

    private static double roundDistance(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream().map(MaidStateExporter::clean).filter(text -> !text.isBlank()).toList();
    }

    private static void putIfNotEmpty(Map<String, Object> payload, String key, Map<String, Object> value) {
        if (!value.isEmpty()) {
            payload.put(key, value);
        }
    }

    private static void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }

    private static String clean(Object value) {
        if (unavailable(value)) {
            return "";
        }
        return String.valueOf(value);
    }

    private static boolean unavailable(Object value) {
        return value == null || isReflectError(value);
    }

    private static boolean isReflectError(Object value) {
        return value instanceof String text && text.startsWith("<reflect-error:");
    }
}
