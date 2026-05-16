package com.github.litroenade.maidbridge.client;

import com.github.litroenade.maidbridge.maid.ai.chat.client.MaidAIChatAttributionClientCache;
import com.github.litroenade.maidbridge.maid.ai.chat.client.MaidAIChatClientAccessState;
import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.network.MaidBridgeClientPayloadDispatch;
import com.github.litroenade.maidbridge.network.OpenReadonlyMaidAIChatPacket;
import com.github.litroenade.maidbridge.network.SyncExternalEmojiPacket;
import com.github.litroenade.maidbridge.network.SyncMaidBridgeAgentStatePacket;
import com.github.litroenade.maidbridge.network.SyncMaidAIChatAttributionsPacket;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.AIChatScreen;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MaidBridgeClientPayloadHandlers {
    private MaidBridgeClientPayloadHandlers() {
    }

    public static void register() {
        MaidBridgeClientPayloadDispatch.register(
                MaidBridgeClientPayloadHandlers::handleSyncMaidAIChatAttributions,
                MaidBridgeClientPayloadHandlers::handleOpenReadonlyMaidAIChat,
                MaidBridgeClientPayloadHandlers::handleSyncMaidBridgeAgentState,
                MaidBridgeClientPayloadHandlers::handleSyncExternalEmoji
        );
    }

    public static void handleSyncMaidAIChatAttributions(SyncMaidAIChatAttributionsPacket packet) {
        MaidAIChatAttributionClientCache.replaceEntries(packet.maidUuid(), packet.entries());
        MaidAIChatClientAccessState.setAccessState(packet.maidUuid(), packet.chatOnly());
    }

    public static void handleOpenReadonlyMaidAIChat(OpenReadonlyMaidAIChatPacket packet) {
        var minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            minecraft.setScreen(null);
            return;
        }

        Entity entity = level.getEntity(packet.entityId());
        if (!(entity instanceof EntityMaid maid) || !packet.maidUuid().equals(maid.getUUID())) {
            minecraft.setScreen(null);
            return;
        }

        MaidAIChatAttributionClientCache.replaceEntries(packet.maidUuid(), packet.attributionEntries());
        MaidAIChatClientAccessState.setAccessState(packet.maidUuid(), true);
        var aiChatManager = maid.getAiChatManager();
        aiChatManager.readFromTag(packet.historyData());
        aiChatManager.llmSite = "";
        aiChatManager.llmModel = "";
        aiChatManager.ttsSite = "";
        aiChatManager.ttsModel = "";
        aiChatManager.ttsLanguage = "";
        aiChatManager.chatLanguage = "";
        aiChatManager.ownerName = "";
        aiChatManager.customSetting = "";

        var chatScreen = new AIChatScreen(maid);
        chatScreen.updateTokens(packet.currentTokens(), packet.maxTokens());
        minecraft.setScreen(chatScreen);
    }

    public static void handleSyncMaidBridgeAgentState(SyncMaidBridgeAgentStatePacket packet) {
        MaidAIChatClientAccessState.setBridgeAgentState(packet.chatMode(), packet.activeAgentId(), packet.agentIds());
    }

    public static void handleSyncExternalEmoji(SyncExternalEmojiPacket packet) {
        MaidBridge.LOGGER.debug(
                "收到外部表情包纹理同步 textureId={} size={}x{} bytes={}",
                packet.textureId(),
                packet.width(),
                packet.height(),
                packet.imageBytes().length
        );
        ExternalEmojiTextureCache.register(packet.textureId(), packet.imageBytes(), packet.width(), packet.height());
    }
}
