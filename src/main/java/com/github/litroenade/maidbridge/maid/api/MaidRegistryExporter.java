package com.github.litroenade.maidbridge.maid.api;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.trace.AiChainEventSink;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.github.litroenade.maidbridge.maid.api.MaidRegistryIntrospection.clean;
import static com.github.litroenade.maidbridge.maid.api.MaidRegistryIntrospection.collectionValue;
import static com.github.litroenade.maidbridge.maid.api.MaidRegistryIntrospection.firstNonBlank;

/**
 * 导出 TouhouLittleMaid 原生 AI 注册目录。
 * <p>这里输出工具、技能、上下文、任务和站点的注册表视图，不是某只女仆的实时状态。
 */
public final class MaidRegistryExporter {
    private MaidRegistryExporter() {
    }

    public static void emitCatalogs() {
        if (!Config.enableMaidApiExposure || !ModList.get().isLoaded(MaidRegistryIntrospection.TOUHOU_LITTLE_MAID_MOD_ID)) {
            return;
        }
        for (var entry : catalogs("").entrySet()) {
            emitCatalogEvent(entry.getKey(), entry.getValue());
        }
    }

    public static Map<String, List<Map<String, Object>>> catalogs(String requestedKind) {
        var catalogs = new LinkedHashMap<String, List<Map<String, Object>>>();
        appendCatalog(catalogs, requestedKind, "tools", MaidRegistryExporter::toolItems);
        appendCatalog(catalogs, requestedKind, "skills", MaidRegistryExporter::skillItems);
        appendCatalog(catalogs, requestedKind, "contexts", MaidRegistryExporter::contextItems);
        appendCatalog(catalogs, requestedKind, "tasks", MaidRegistryExporter::taskItems);
        appendCatalog(catalogs, requestedKind, "sites", MaidRegistryExporter::siteItems);
        return catalogs;
    }

    public static Map<String, List<Map<String, Object>>> formalCatalogs(String requestedKind) {
        /*
         * 注册目录事件用于调试面，保留较多 Java/TouhouLittleMaid 来源信息。
         * maid.api.query.* 返回给外部调用方的是更小的正式查询视图。
         */
        var catalogs = new LinkedHashMap<String, List<Map<String, Object>>>();
        appendFormalCatalog(catalogs, requestedKind, "tools");
        appendFormalCatalog(catalogs, requestedKind, "skills");
        appendFormalCatalog(catalogs, requestedKind, "contexts");
        appendFormalCatalog(catalogs, requestedKind, "tasks");
        return catalogs;
    }

    public static List<Map<String, Object>> compactRegistryItemsForTurn(List<Map<String, Object>> items) {
        var compactItems = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : items) {
            var compact = new LinkedHashMap<String, Object>();
            copyIfPresent(compact, item, "id");
            copyIfPresent(compact, item, "name");
            copyIfPresent(compact, item, "description");
            copyIfPresent(compact, item, "service_type");
            copyIfPresent(compact, item, "api_type");
            copyIfPresent(compact, item, "enabled");
            copyIfPresent(compact, item, "requires_maid_context");
            copyIfPresent(compact, item, "prompt_context");
            copyIfPresent(compact, item, "context_keys");
            compactItems.add(compact);
        }
        return compactItems;
    }

    private static void appendCatalog(
            Map<String, List<Map<String, Object>>> catalogs,
            String requestedKind,
            String kind,
            Supplier<List<Map<String, Object>>> supplier
    ) {
        if (requestedKind == null || requestedKind.isBlank() || kind.equals(requestedKind)) {
            catalogs.put(kind, supplier.get());
        }
    }

    private static void appendFormalCatalog(Map<String, List<Map<String, Object>>> catalogs, String requestedKind, String kind) {
        if (requestedKind != null && !requestedKind.isBlank() && !kind.equals(requestedKind)) {
            return;
        }
        var items = catalogs(kind).getOrDefault(kind, List.of());
        catalogs.put(kind, compactRegistryItemsForTurn(items));
    }

    public static void emitCatalogEvent(String kind, List<Map<String, Object>> items) {
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("registry_id", kind + ":" + System.currentTimeMillis());
            payload.put("server_id", firstNonBlank(Config.sourceEndpoint, Config.DEFAULT_SOURCE_ENDPOINT));
            payload.put("endpoint_id", firstNonBlank(Config.sourceEndpoint, Config.DEFAULT_SOURCE_ENDPOINT));
            payload.put("source", MaidRegistryIntrospection.TOUHOU_LITTLE_MAID_MOD_ID);
            payload.put(kind, items);
            AiChainEventSink.emit(BridgeProtocol.PREFIX_MAID_API_REGISTRY + kind, payload);
        } catch (RuntimeException exception) {
            MaidBridge.LOGGER.warn("导出 TouhouLittleMaid {} 注册表失败", kind, exception);
        }
    }

    private static List<Map<String, Object>> toolItems() {
        var tools = MaidRegistryIntrospection.toolMap();
        var items = new ArrayList<Map<String, Object>>();
        tools.forEach((key, tool) -> {
            String id = firstNonBlank(MaidApiReflection.invoke(tool, "id"), key);
            var item = baseItem(id, tool);
            item.put("description", "女仆本体 AI 工具；summary 和 parameters 需要 EntityMaid 上下文。");
            item.put("source", "ToolRegister.getAllTools");
            item.put("requires_maid_context", true);
            items.add(item);
        });
        return items;
    }

    private static List<Map<String, Object>> skillItems() {
        var skills = MaidRegistryIntrospection.skillMap();
        var items = new ArrayList<Map<String, Object>>();
        skills.forEach((key, skill) -> {
            String id = firstNonBlank(MaidApiReflection.invoke(skill, "name"), key);
            var item = baseItem(id, skill);
            item.put("description", clean(MaidApiReflection.invoke(skill, "description")));
            item.put("metadata", MaidApiReflection.metadataValue(MaidApiReflection.invoke(skill, "metadata")));
            item.put("knowledge_type", MaidApiReflection.invoke(skill, "isKnowledgeType"));
            item.put("source", "SkillLoader.getAllSkills");
            items.add(item);
        });
        return items;
    }

    private static List<Map<String, Object>> contextItems() {
        var items = new ArrayList<Map<String, Object>>();
        appendContextItems(
                items,
                MaidRegistryIntrospection.toolContextCategories(),
                false
        );
        appendContextItems(
                items,
                MaidRegistryIntrospection.promptContextCategories(),
                true
        );
        return items;
    }

    private static void appendContextItems(List<Map<String, Object>> items, Collection<?> categories, boolean promptContext) {
        for (Object category : categories) {
            String id = clean(MaidApiReflection.invoke(category, "id"));
            var item = baseItem(id, category);
            item.put("description", clean(MaidApiReflection.invoke(category, "summary")));
            item.put("prompt_context", promptContext);
            item.put("context_keys", collectionValue(MaidApiReflection.invoke(category, "contextKeys")));
            item.put("source", promptContext ? "GameContextRegister.allPromptCategories" : "GameContextRegister.allToolCategories");
            items.add(item);
        }
    }

    private static List<Map<String, Object>> taskItems() {
        var tasks = MaidRegistryIntrospection.taskMap();
        var items = new ArrayList<Map<String, Object>>();
        tasks.forEach((key, task) -> {
            String id = firstNonBlank(MaidApiReflection.invoke(task, "getUid"), key);
            var item = baseItem(id, task);
            item.put("description", clean(MaidApiReflection.invoke(task, "getMaidActionSummary")));
            item.put("source", "TaskManager.getTaskMap");
            items.add(item);
        });
        return items;
    }

    private static List<Map<String, Object>> siteItems() {
        var items = new ArrayList<Map<String, Object>>();
        appendSiteItems(items, "llm", MaidRegistryIntrospection.sitesField("LLM_SITES"));
        appendSiteItems(items, "tts", MaidRegistryIntrospection.sitesField("TTS_SITES"));
        appendSiteItems(items, "stt", MaidRegistryIntrospection.sitesField("STT_SITES"));
        return items;
    }

    private static void appendSiteItems(List<Map<String, Object>> items, String serviceType, Object rawSites) {
        var sites = MaidRegistryIntrospection.requireMap(rawSites, "AvailableSites." + serviceType);
        sites.forEach((key, site) -> {
            String id = firstNonBlank(MaidApiReflection.invoke(site, "id"), key);
            var item = baseItem(id, site);
            item.put("description", "女仆本体 " + serviceType.toUpperCase() + " 站点元数据；不会导出密钥。");
            item.put("service_type", serviceType);
            item.put("api_type", clean(MaidApiReflection.invoke(site, "getApiType")));
            item.put("enabled", MaidApiReflection.invoke(site, "enabled"));
            item.put("source", "AvailableSites");
            items.add(item);
        });
    }

    private static Map<String, Object> baseItem(String id, Object sourceObject) {
        var item = new LinkedHashMap<String, Object>();
        item.put("id", id);
        item.put("name", id);
        item.put("class_name", sourceObject == null ? "" : sourceObject.getClass().getName());
        return item;
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

}
