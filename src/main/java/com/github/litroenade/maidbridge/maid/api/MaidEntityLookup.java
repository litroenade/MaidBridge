package com.github.litroenade.maidbridge.maid.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;
import java.util.function.Function;

public final class MaidEntityLookup {
    private static final String ENTITY_MAID = "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid";

    private MaidEntityLookup() {
    }

    public static Entity findByUuid(MinecraftServer server, UUID uuid) {
        return findMaid(server, level -> level.getEntity(uuid));
    }

    private static Entity findMaid(MinecraftServer server, Function<ServerLevel, Entity> resolver) {
        for (ServerLevel level : serverLevels(server)) {
            Entity entity = resolver.apply(level);
            if (isTouhouMaid(entity)) {
                return entity;
            }
        }
        return null;
    }

    public static boolean isTouhouMaid(Object entity) {
        if (!(entity instanceof Entity)) {
            return false;
        }
        Class<?> type = entity.getClass();
        while (type != null) {
            if (ENTITY_MAID.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    public static String entityName(Entity entity) {
        return entity.getName().getString();
    }

    public static Iterable<ServerLevel> serverLevels(MinecraftServer server) {
        return server.getAllLevels();
    }
}
