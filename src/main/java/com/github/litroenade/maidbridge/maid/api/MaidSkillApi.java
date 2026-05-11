package com.github.litroenade.maidbridge.maid.api;

import com.github.litroenade.maidbridge.protocol.BridgeProtocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MaidSkillApi {
    private MaidSkillApi() {
    }

    public static Map<String, Object> querySkills() {
        var skills = new ArrayList<Map<String, Object>>();
        for (var entry : MaidRegistryIntrospection.skillMap().entrySet()) {
            skills.add(skillItem(MaidApiReflection.clean(entry.getKey()), entry.getValue()));
        }
        return Map.of(
                "request_type", BridgeProtocol.TYPE_MAID_API_QUERY_SKILLS,
                "skills", skills,
                "count", skills.size()
        );
    }

    public static Map<String, Object> querySkill(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skill_id 必须是非空字符串");
        }
        Object skill = MaidRegistryIntrospection.skillById(skillId);
        if (skill == null) {
            throw new IllegalArgumentException("未找到技能：" + skillId);
        }
        return Map.of(
                "request_type", BridgeProtocol.TYPE_MAID_API_QUERY_SKILL,
                "skill", skillItem(skillId, skill)
        );
    }

    public static Map<String, Object> skillItem(
            String name,
            String description,
            Object metadata,
            boolean knowledgeType,
            int bodyCharacters,
            int referenceCount
    ) {
        var item = new LinkedHashMap<String, Object>();
        item.put("name", name);
        item.put("description", description);
        item.put("metadata", MaidApiReflection.metadataValue(metadata));
        item.put("knowledge_type", knowledgeType);
        item.put("body_characters", Math.max(0, bodyCharacters));
        item.put("reference_count", Math.max(0, referenceCount));
        return item;
    }

    private static Map<String, Object> skillItem(String mapKey, Object skill) {
        var name = MaidApiReflection.clean(MaidApiReflection.invoke(skill, "name"));
        if (name.isBlank()) {
            name = mapKey;
        }
        var description = MaidApiReflection.clean(MaidApiReflection.invoke(skill, "description"));
        var metadata = MaidApiReflection.invoke(skill, "metadata");
        var knowledgeType = Boolean.TRUE.equals(MaidApiReflection.invoke(skill, "isKnowledgeType"));
        var body = MaidApiReflection.clean(MaidApiReflection.invoke(skill, "body"));
        var references = MaidApiReflection.invoke(skill, "references");
        int referenceCount = references instanceof Map<?, ?> map ? map.size() : 0;
        return skillItem(name, description, metadata, knowledgeType, body.length(), referenceCount);
    }

}
