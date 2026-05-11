package com.github.litroenade.maidbridge.maid.api;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.trace.ReflectiveAccess;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import com.github.litroenade.maidbridge.protocol.frame.MaidApiRequest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maid API 的服务端执行层。
 * <p>
 * 对外只返回清洗后的女仆状态和能力视图，不把 TouhouLittleMaid 内部对象直接交给适配器。
 */
public final class MaidApiFacade {
    private static final String TOUHOU_LITTLE_MAID_MOD_ID = "touhou_little_maid";

    private MaidApiFacade() {
    }

    public static Map<String, Object> execute(MinecraftServer server, MaidApiRequest request) {
        return switch (request.type()) {
            case BridgeProtocol.TYPE_MAID_API_QUERY_MAIDS -> queryMaids(server);
            case BridgeProtocol.TYPE_MAID_API_QUERY_MAID -> queryMaid(server, request);
            case BridgeProtocol.TYPE_MAID_API_QUERY_REGISTRY -> queryRegistry(request);
            case BridgeProtocol.TYPE_MAID_API_CALL_MAID_ACTION -> callMaidAction(server, request);
            case BridgeProtocol.TYPE_MAID_API_QUERY_MAID_TOOL_SCHEMA -> queryMaidToolSchema(server, request);
            case BridgeProtocol.TYPE_MAID_API_QUERY_MAID_CONTEXT -> queryMaidContext(server, request);
            case BridgeProtocol.TYPE_MAID_API_CALL_MAID_TOOL -> callMaidTool(server, request);
            case BridgeProtocol.TYPE_MAID_API_QUERY_SKILLS -> querySkills();
            case BridgeProtocol.TYPE_MAID_API_QUERY_SKILL -> querySkill(request);
            default -> throw new IllegalArgumentException("不支持的 Maid API 帧类型：" + request.type());
        };
    }

    private static Map<String, Object> queryMaids(MinecraftServer server) {
        ensureApiAvailable();
        var maids = new ArrayList<Map<String, Object>>();
        for (ServerLevel level : MaidEntityLookup.serverLevels(server)) {
            var rawEntities = ReflectiveAccess.invoke(level, "getAllEntities");
            if (rawEntities instanceof Iterable<?> entities) {
                for (Object entity : entities) {
                    if (entity instanceof Entity maid && MaidEntityLookup.isTouhouMaid(maid)) {
                        maids.add(maidSummary(maid));
                    }
                }
            }
        }
        return Map.of(
                "request_type", BridgeProtocol.TYPE_MAID_API_QUERY_MAIDS,
                "maids", maids,
                "count", maids.size()
        );
    }

    private static Map<String, Object> queryMaid(MinecraftServer server, MaidApiRequest request) {
        Entity maid = findMaid(server, request);
        ensureApiAvailable();
        if (maid == null) {
            throw new IllegalArgumentException("未找到女仆");
        }
        return Map.of(
                "request_type", BridgeProtocol.TYPE_MAID_API_QUERY_MAID,
                "maid", maidSummary(maid)
        );
    }

    private static Map<String, Object> queryRegistry(MaidApiRequest request) {
        ensureApiAvailable();
        var kind = request.stringPayload("kind");
        var catalogs = MaidRegistryExporter.formalCatalogs(kind);
        if (!kind.isBlank() && catalogs.isEmpty()) {
            throw new IllegalArgumentException("未知注册表类型：" + kind);
        }
        return Map.of(
                "request_type", BridgeProtocol.TYPE_MAID_API_QUERY_REGISTRY,
                "registry_kind", kind,
                "registries", catalogs
        );
    }

    private static Map<String, Object> callMaidAction(MinecraftServer server, MaidApiRequest request) {
        ensureApiExposureEnabled();
        if (!Config.enableMaidApiActions) {
            throw new IllegalArgumentException(BridgeProtocol.TYPE_MAID_API_CALL_MAID_ACTION + " 已禁用");
        }
        Entity maid = findMaid(server, request);
        ensureMaidModLoaded();
        if (maid == null) {
            throw new IllegalArgumentException("未找到女仆");
        }
        var action = new LinkedHashMap<>(request.payload());
        if (request.stringPayload("type").isBlank()
                && request.stringPayload("action").isBlank()
                && request.stringPayload("tool_id").isBlank()
                && request.stringPayload("name").isBlank()) {
            action.put("type", "set_task");
        }
        var actionResult = MaidActionExecutor.apply(maid, action);
        return Map.of(
                "request_type", BridgeProtocol.TYPE_MAID_API_CALL_MAID_ACTION,
                "maid", maidSummary(maid),
                "action", actionResult
        );
    }

    private static Map<String, Object> queryMaidToolSchema(MinecraftServer server, MaidApiRequest request) {
        Entity maid = findMaid(server, request);
        ensureApiAvailable();
        if (maid == null) {
            throw new IllegalArgumentException("未找到女仆");
        }
        var response = new LinkedHashMap<>(MaidToolApi.queryToolSchemas(maid, request.stringPayload("tool_id")));
        response.put("maid", maidSummary(maid));
        return response;
    }

    private static Map<String, Object> queryMaidContext(MinecraftServer server, MaidApiRequest request) {
        Entity maid = findMaid(server, request);
        ensureApiAvailable();
        if (maid == null) {
            throw new IllegalArgumentException("未找到女仆");
        }
        var response = new LinkedHashMap<>(MaidContextApi.queryContext(
                maid,
                firstNonBlank(request.stringPayload("category"), request.stringPayload("category_id")),
                firstNonBlank(request.stringPayload("key"), request.stringPayload("context_key"))
        ));
        response.put("maid", maidSummary(maid));
        return response;
    }

    private static Map<String, Object> callMaidTool(MinecraftServer server, MaidApiRequest request) {
        ensureApiExposureEnabled();
        if (!Config.enableMaidApiActions) {
            throw new IllegalArgumentException(BridgeProtocol.TYPE_MAID_API_CALL_MAID_TOOL + " 已禁用");
        }
        Entity maid = findMaid(server, request);
        ensureMaidModLoaded();
        if (maid == null) {
            throw new IllegalArgumentException("未找到女仆");
        }
        var response = new LinkedHashMap<>(MaidToolApi.callTool(maid, request.payload()));
        response.put("maid", maidSummary(maid));
        return response;
    }

    private static Map<String, Object> querySkills() {
        ensureApiAvailable();
        return MaidSkillApi.querySkills();
    }

    private static Map<String, Object> querySkill(MaidApiRequest request) {
        ensureApiAvailable();
        return MaidSkillApi.querySkill(firstNonBlank(request.stringPayload("skill_id"), request.stringPayload("name")));
    }

    private static void ensureApiAvailable() {
        ensureApiExposureEnabled();
        ensureMaidModLoaded();
    }

    private static void ensureApiExposureEnabled() {
        if (!Config.enableMaidApiExposure) {
            throw new IllegalArgumentException("Maid API 暴露未启用");
        }
    }

    private static void ensureMaidModLoaded() {
        if (!ModList.get().isLoaded(TOUHOU_LITTLE_MAID_MOD_ID)) {
            throw new IllegalArgumentException("TouhouLittleMaid 未加载");
        }
    }

    private static Entity findMaid(MinecraftServer server, MaidApiRequest request) {
        var maidUuid = request.maidUuid();
        if (maidUuid.isBlank()) {
            return null;
        }
        return MaidEntityLookup.findByUuid(server, parseMaidUuid(maidUuid));
    }

    private static UUID parseMaidUuid(String maidUuid) {
        try {
            return UUID.fromString(maidUuid);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("无效 maid_uuid：" + maidUuid, exception);
        }
    }

    private static Map<String, Object> maidSummary(Entity maid) {
        var summary = new LinkedHashMap<String, Object>();
        summary.put("uuid", ReflectiveAccess.invoke(maid, "getUUID"));
        summary.put("entity_id", ReflectiveAccess.invoke(maid, "getId"));
        summary.put("name", MaidEntityLookup.entityName(maid));
        summary.put("owner", Map.of("uuid", ReflectiveAccess.invoke(maid, "getOwnerUUID")));
        summary.put("model_id", ReflectiveAccess.invoke(maid, "getModelId"));
        summary.put("task_id", taskId(ReflectiveAccess.invoke(maid, "getTask")));
        summary.put("alive", maid.isAlive());
        summary.put("dimension", dimension(maid));
        return summary;
    }

    @SuppressWarnings("resource")
    private static String dimension(Entity entity) {
        return entity.level().dimension().location().toString();
    }

    private static String taskId(Object task) {
        var uid = ReflectiveAccess.invoke(task, "getUid");
        return uid == null ? "" : String.valueOf(uid);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

}
