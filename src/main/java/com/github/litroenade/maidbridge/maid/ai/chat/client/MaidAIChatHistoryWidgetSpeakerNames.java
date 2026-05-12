package com.github.litroenade.maidbridge.maid.ai.chat.client;

import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.HistoryChatWidget;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class MaidAIChatHistoryWidgetSpeakerNames {
    private static final Map<HistoryChatWidget, Speaker> SPEAKERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private MaidAIChatHistoryWidgetSpeakerNames() {
    }

    public static void bind(HistoryChatWidget widget, UUID speakerUuid, String speakerName) {
        if (speakerUuid == null && StringUtils.isBlank(speakerName)) {
            SPEAKERS.remove(widget);
            return;
        }
        SPEAKERS.put(widget, new Speaker(speakerUuid, speakerName, resolvePlayerSkin(speakerUuid, speakerName)));
    }

    public static String getName(HistoryChatWidget widget) {
        var speaker = SPEAKERS.get(widget);
        return speaker == null ? null : speaker.name();
    }

    public static ResourceLocation getPlayerSkin(HistoryChatWidget widget) {
        var speaker = SPEAKERS.get(widget);
        return speaker == null ? null : speaker.playerSkin();
    }

    private static ResourceLocation resolvePlayerSkin(UUID speakerUuid, String speakerName) {
        if (speakerUuid == null) {
            return null;
        }
        var profile = new GameProfile(speakerUuid, StringUtils.isBlank(speakerName) ? "" : speakerName.trim());
        return Minecraft.getInstance().getSkinManager().getInsecureSkin(profile).texture();
    }

    private record Speaker(UUID uuid, String name, ResourceLocation playerSkin) {
    }
}
