package com.github.litroenade.maidbridge.mixin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MaidBridgeMixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_MODE_PROPERTY = "maidbridge.maid_ai_chain_mixin";
    private static final String TOUHOU_LITTLE_MAID_SENTINEL_CLASS =
            "com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager";
    private static final Set<String> MAID_AI_CHAIN_DIAGNOSTIC_MIXINS = Set.of(
            "com.github.litroenade.maidbridge.mixin.LLMOpenAIClientMixin",
            "com.github.litroenade.maidbridge.mixin.MaidAIChatManagerMixin"
    );
    private static final Set<String> TOUHOU_LITTLE_MAID_CLIENT_CHAT_MIXINS = Set.of(
            "com.github.litroenade.maidbridge.mixin.AIChatScreenMixin",
            "com.github.litroenade.maidbridge.mixin.HistoryAIChatScreenMixin",
            "com.github.litroenade.maidbridge.mixin.HistoryChatWidgetMixin",
            "com.github.litroenade.maidbridge.mixin.PressAIChatKeyEventMixin"
    );
    private static final Set<String> TOUHOU_LITTLE_MAID_CHAT_MIXINS = Set.of(
            "com.github.litroenade.maidbridge.mixin.ClearMaidAIDataPacketMixin",
            "com.github.litroenade.maidbridge.mixin.MaidAIChatDataMixin",
            "com.github.litroenade.maidbridge.mixin.HistorySummaryManagerMixin",
            "com.github.litroenade.maidbridge.mixin.LLMCallbackMixin",
            "com.github.litroenade.maidbridge.mixin.OpenAIConfigPacketMixin",
            "com.github.litroenade.maidbridge.mixin.OpenMaidAIChatPacketMixin",
            "com.github.litroenade.maidbridge.mixin.SaveLLMSitePacketMixin",
            "com.github.litroenade.maidbridge.mixin.SaveMaidAIDataPackageMixin",
            "com.github.litroenade.maidbridge.mixin.SaveTTSSitePacketMixin",
            "com.github.litroenade.maidbridge.mixin.SendUserChatPackageMixin",
            "com.github.litroenade.maidbridge.mixin.MaidAIChatManagerGuardMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    @Nullable
    @SuppressWarnings("SameReturnValue")
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (MAID_AI_CHAIN_DIAGNOSTIC_MIXINS.contains(mixinClassName)) {
            if (isExplicitlyDisabled()) {
                return false;
            }
            return classExists(TOUHOU_LITTLE_MAID_SENTINEL_CLASS) && classExists(targetClassName);
        }
        if (TOUHOU_LITTLE_MAID_CLIENT_CHAT_MIXINS.contains(mixinClassName)) {
            return FMLEnvironment.dist == Dist.CLIENT
                    && classExists(TOUHOU_LITTLE_MAID_SENTINEL_CLASS)
                    && classExists(targetClassName);
        }
        if (TOUHOU_LITTLE_MAID_CHAT_MIXINS.contains(mixinClassName)) {
            return classExists(TOUHOU_LITTLE_MAID_SENTINEL_CLASS) && classExists(targetClassName);
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    @Nullable
    @SuppressWarnings("SameReturnValue")
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isExplicitlyDisabled() {
        String mode = System.getProperty(MIXIN_MODE_PROPERTY, "auto").trim().toLowerCase(Locale.ROOT);
        return mode.equals("false") || mode.equals("off") || mode.equals("disabled") || mode.equals("none");
    }

    private static boolean classExists(String className) {
        var resourceName = className.replace('.', '/') + ".class";
        var loader = Thread.currentThread().getContextClassLoader();
        return loader != null && loader.getResource(resourceName) != null;
    }

    public static boolean isAiChainDiagnosticsDisabled() {
        return isExplicitlyDisabled();
    }
}
