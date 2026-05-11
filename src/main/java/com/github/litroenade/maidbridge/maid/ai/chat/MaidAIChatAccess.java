package com.github.litroenade.maidbridge.maid.ai.chat;

import com.github.litroenade.maidbridge.Config;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;

public final class MaidAIChatAccess {
    private MaidAIChatAccess() {
    }

    public static boolean canOpenChat(EntityMaid maid, ServerPlayer player) {
        return maid.isAlive() && !player.isSpectator();
    }

    public static boolean canSendChat(EntityMaid maid, ServerPlayer player) {
        if (!canOpenChat(maid, player)) {
            return false;
        }
        // 历史查看是只读能力，多人开关只控制非主人是否可以写入聊天。
        return maid.isOwnedBy(player) || Config.enableMultiplayerMaidChat;
    }

    public static boolean canEditSettings(EntityMaid maid, ServerPlayer player) {
        return canOpenChat(maid, player) && maid.isOwnedBy(player);
    }
}
