package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.maid.ai.chat.client.MaidAIChatClientAccessState;
import com.github.litroenade.maidbridge.maid.ai.chat.client.MaidBridgePopupMenu;
import com.github.litroenade.maidbridge.network.RefreshMaidBridgeAgentsPacket;
import com.github.litroenade.maidbridge.network.SetExternalMaidAgentModePacket;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.AIChatScreen", remap = false)
public abstract class AIChatScreenMixin extends Screen {
    @Shadow
    @Final
    private EntityMaid maid;
    @Shadow
    private FlatColorButton configButton;
    @Shadow
    private FlatColorButton settingButton;
    @Shadow
    private FlatColorButton llmButton;
    @Shadow
    private FlatColorButton ttsButton;
    @Shadow
    private FlatColorButton langButton;
    @Shadow
    private EditBox input;

    @Unique
    private FlatColorButton maidbridge$modeButton;
    @Unique
    private FlatColorButton maidbridge$agentButton;
    @Unique
    private static final int maidbridge$POPUP_NONE = 0;
    @Unique
    private static final int maidbridge$POPUP_MODE = 1;
    @Unique
    private static final int maidbridge$POPUP_AGENT = 2;
    @Unique
    private int maidbridge$popupKind = maidbridge$POPUP_NONE;

    private AIChatScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void maidbridge$initModeControls(CallbackInfo ci) {
        maidbridge$addBridgeButtons();
        maidbridge$syncControlButtons();
        maidbridge$refreshModeButton();
    }

    @Inject(method = "refreshSelectorButtons", at = @At("TAIL"))
    private void maidbridge$keepChatOnlySelectorsLocked(CallbackInfo ci) {
        maidbridge$syncControlButtons();
        maidbridge$refreshModeButton();
    }

    @Inject(method = "togglePopup", at = @At("HEAD"), cancellable = true)
    private void maidbridge$blockChatOnlyPopup(@Coerce Object type, CallbackInfo ci) {
        if (maidbridge$shouldHideNativeControls()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSelectionSummaries", at = @At("HEAD"), cancellable = true)
    private void maidbridge$hideChatOnlySelectionSummaries(GuiGraphics graphics, CallbackInfo ci) {
        if (maidbridge$isExternalAgentMode()) {
            maidbridge$renderExternalAgentSummary(graphics);
            ci.cancel();
            return;
        }
        if (maidbridge$shouldLockControls()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void maidbridge$refreshModeButtonOnRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        maidbridge$syncControlButtons();
        maidbridge$refreshModeButton();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void maidbridge$renderModeButtonTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (this.maidbridge$modeButton != null && this.maidbridge$modeButton.visible) {
            this.maidbridge$modeButton.renderToolTip(graphics, this, mouseX, mouseY);
        }
        if (this.maidbridge$agentButton != null && this.maidbridge$agentButton.visible) {
            this.maidbridge$agentButton.renderToolTip(graphics, this, mouseX, mouseY);
        }
        if (this.maidbridge$popupKind != maidbridge$POPUP_NONE) {
            maidbridge$renderPopup(graphics, mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void maidbridge$mouseClickedPopup(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.maidbridge$popupKind == maidbridge$POPUP_NONE) {
            return;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && maidbridge$tryClickPopup(mouseX, mouseY)) {
            cir.setReturnValue(true);
            return;
        }
        if (this.maidbridge$modeButton != null && this.maidbridge$modeButton.isMouseOver(mouseX, mouseY)) {
            return;
        }
        if (this.maidbridge$agentButton != null && this.maidbridge$agentButton.isMouseOver(mouseX, mouseY)) {
            return;
        }
        this.maidbridge$popupKind = maidbridge$POPUP_NONE;
    }

    @Unique
    private boolean maidbridge$shouldLockControls() {
        return maidbridge$isChatOnly(this.maid) || maidbridge$isNonOwner(this.maid);
    }

    @Unique
    private boolean maidbridge$shouldHideNativeControls() {
        return maidbridge$shouldLockControls() || maidbridge$isExternalAgentMode();
    }

    @Unique
    private void maidbridge$syncControlButtons() {
        if (maidbridge$shouldHideNativeControls()) {
            maidbridge$setNativeButtonsVisible(false);
            return;
        }
        if (!maidbridge$shouldLockControls()) {
            maidbridge$setNativeButtonsVisible(true);
        }
    }

    @Unique
    private void maidbridge$addBridgeButtons() {
        if (this.langButton == null || maidbridge$shouldLockControls()) {
            return;
        }
        int size = this.langButton.getHeight();
        int gap = 2;
        int slot = size + gap;
        int x = this.langButton.getX();
        int y = this.langButton.getY();
        maidbridge$moveButton(this.llmButton, -slot);
        maidbridge$moveButton(this.ttsButton, -slot);
        maidbridge$moveButton(this.langButton, -slot);
        this.maidbridge$agentButton = this.addRenderableWidget(new FlatColorButton(x - slot, y, size, size, Component.literal("✦"), button -> maidbridge$togglePopup(maidbridge$POPUP_AGENT)).setTooltips("gui.maidbridge.chat.button.agent.tip"));
        this.maidbridge$modeButton = this.addRenderableWidget(new FlatColorButton(x, y, size, size, Component.literal("⇄"), button -> maidbridge$togglePopup(maidbridge$POPUP_MODE)).setTooltips("gui.maidbridge.chat.button.mode.tip"));
    }

    @Unique
    private void maidbridge$refreshModeButton() {
        if (this.maidbridge$modeButton == null) {
            return;
        }
        boolean editable = !maidbridge$shouldLockControls();
        boolean externalAgentMode = maidbridge$isExternalAgentMode();
        this.maidbridge$modeButton.visible = editable;
        this.maidbridge$modeButton.active = editable;
        this.maidbridge$modeButton.setSelect(this.maidbridge$popupKind == maidbridge$POPUP_MODE);
        this.maidbridge$modeButton.setTooltips(List.of(
                Component.translatable("gui.maidbridge.chat.button.mode.tip"),
                maidbridge$modeLabel(maidbridge$chatMode())
        ));
        if (this.maidbridge$agentButton != null) {
            this.maidbridge$agentButton.visible = editable && externalAgentMode;
            this.maidbridge$agentButton.active = editable && externalAgentMode;
            this.maidbridge$agentButton.setSelect(this.maidbridge$popupKind == maidbridge$POPUP_AGENT);
            this.maidbridge$agentButton.setTooltips(List.of(
                    Component.translatable("gui.maidbridge.chat.button.agent.tip"),
                    Component.literal(maidbridge$agentId())
            ));
        }
    }

    @Unique
    private void maidbridge$togglePopup(int popupKind) {
        this.maidbridge$popupKind = this.maidbridge$popupKind == popupKind ? maidbridge$POPUP_NONE : popupKind;
    }

    @Unique
    private String maidbridge$chatMode() {
        return MaidAIChatClientAccessState.chatMode(this.maid);
    }

    @Unique
    private void maidbridge$renderExternalAgentSummary(GuiGraphics graphics) {
        if (this.input == null) {
            return;
        }
        int left = this.input.getX() - 6;
        int right = this.input.getX() + this.input.getInnerWidth() + 6;
        int summaryY = this.input.getY() + 16;
        float scale = 0.5f;
        String agentId = maidbridge$agentId();
        String summary = Component.translatable("gui.maidbridge.chat.summary.external_agent", agentId).getString();
        int maxWidth = Math.round((right - left) / scale);
        String text = MaidBridgePopupMenu.trimToWidth(this.font, summary, maxWidth);

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(this.font, text, Math.round(left / scale), Math.round(summaryY / scale), 0xFFADADAD);
        graphics.pose().popPose();
    }

    @Unique
    private void maidbridge$renderPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        var geometry = maidbridge$popupGeometry();
        if (geometry.entries().isEmpty()) {
            return;
        }
        MaidBridgePopupMenu.render(graphics, this.font, geometry, mouseX, mouseY);
    }

    @Unique
    private boolean maidbridge$tryClickPopup(double mouseX, double mouseY) {
        var geometry = maidbridge$popupGeometry();
        if (!geometry.contains(mouseX, mouseY)) {
            return false;
        }
        int index = geometry.indexAt(mouseY);
        if (index < 0 || index >= geometry.entries().size()) {
            return true;
        }
        var entry = geometry.entries().get(index);
        if (entry.header()) {
            return true;
        }
        var popupKind = this.maidbridge$popupKind;
        this.maidbridge$popupKind = maidbridge$POPUP_NONE;
        if (entry.refresh()) {
            PacketDistributor.sendToServer(new RefreshMaidBridgeAgentsPacket(this.maid.getId()));
            return true;
        }
        if (popupKind == maidbridge$POPUP_MODE && !entry.mode().isBlank()) {
            PacketDistributor.sendToServer(new SetExternalMaidAgentModePacket(this.maid.getId(), entry.mode()));
            return true;
        }
        return true;
    }

    @Unique
    private MaidBridgePopupMenu.Geometry maidbridge$popupGeometry() {
        var entries = maidbridge$popupEntries();
        return MaidBridgePopupMenu.geometry(this.font, this.width, this.height, maidbridge$popupAnchor(), entries);
    }

    @Unique
    private List<MaidBridgePopupMenu.Entry> maidbridge$popupEntries() {
        if (this.maidbridge$popupKind == maidbridge$POPUP_MODE) {
            String mode = maidbridge$chatMode();
            return List.of(
                    MaidBridgePopupMenu.Entry.header(Component.translatable("gui.maidbridge.chat.popup.mode")),
                    MaidBridgePopupMenu.Entry.mode(Component.translatable("gui.maidbridge.chat.button.mode.native"), Config.MAID_CHAT_MODE_NATIVE, Config.MAID_CHAT_MODE_NATIVE.equals(mode)),
                    // TLM+Bridge 模式尚未接入完整链路，先不在聊天窗口暴露。
                    // MaidBridgePopupMenu.Entry.mode(Component.translatable("gui.maidbridge.chat.button.mode.tlm_bridge"), Config.MAID_CHAT_MODE_TLM_BRIDGE, Config.MAID_CHAT_MODE_TLM_BRIDGE.equals(mode)),
                    MaidBridgePopupMenu.Entry.mode(Component.translatable("gui.maidbridge.chat.button.mode.external"), Config.MAID_CHAT_MODE_EXTERNAL_AGENT, Config.MAID_CHAT_MODE_EXTERNAL_AGENT.equals(mode))
            );
        }
        var entries = new ArrayList<MaidBridgePopupMenu.Entry>();
        entries.add(MaidBridgePopupMenu.Entry.header(Component.translatable("gui.maidbridge.chat.popup.agent")));
        String activeAgentId = MaidAIChatClientAccessState.activeAgentId(this.maid);
        for (String agentId : MaidAIChatClientAccessState.agentIds(this.maid)) {
            entries.add(MaidBridgePopupMenu.Entry.agent(Component.literal(agentId), agentId.equals(activeAgentId)));
        }
        entries.add(MaidBridgePopupMenu.Entry.refresh(Component.translatable("gui.maidbridge.chat.popup.refresh")));
        return entries;
    }

    @Unique
    private Component maidbridge$modeLabel(String mode) {
        return switch (mode) {
            case Config.MAID_CHAT_MODE_EXTERNAL_AGENT -> Component.translatable("gui.maidbridge.chat.button.mode.external");
            // TLM+Bridge 模式暂时按原生模式显示，避免出现不可选择的半成品入口。
            // case Config.MAID_CHAT_MODE_TLM_BRIDGE -> Component.translatable("gui.maidbridge.chat.button.mode.tlm_bridge");
            default -> Component.translatable("gui.maidbridge.chat.button.mode.native");
        };
    }

    @Unique
    private FlatColorButton maidbridge$popupAnchor() {
        if (this.maidbridge$popupKind == maidbridge$POPUP_AGENT) {
            return this.maidbridge$agentButton;
        }
        return this.maidbridge$modeButton;
    }

    @Unique
    private static void maidbridge$moveButton(FlatColorButton button, int deltaX) {
        if (button != null) {
            button.setX(button.getX() + deltaX);
        }
    }

    @Unique
    private void maidbridge$setNativeButtonsVisible(boolean visible) {
        maidbridge$setButtonVisible(this.configButton, visible);
        maidbridge$setButtonVisible(this.settingButton, visible);
        maidbridge$setButtonVisible(this.llmButton, visible);
        maidbridge$setButtonVisible(this.ttsButton, visible);
        maidbridge$setButtonVisible(this.langButton, visible);
    }

    @Unique
    private static void maidbridge$setButtonVisible(FlatColorButton button, boolean visible) {
        if (button == null) {
            return;
        }
        button.visible = visible;
    }

    @Unique
    private static boolean maidbridge$isNonOwner(EntityMaid maid) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && !maid.isOwnedBy(player);
    }

    @Unique
    private static boolean maidbridge$isChatOnly(EntityMaid maid) {
        return MaidAIChatClientAccessState.isChatOnly(maid);
    }

    @Unique
    private boolean maidbridge$isExternalAgentMode() {
        return MaidAIChatClientAccessState.isExternalAgentMode(this.maid);
    }

    @Unique
    private String maidbridge$agentId() {
        String agentId = MaidAIChatClientAccessState.activeAgentId(this.maid);
        return agentId.isBlank() ? Component.translatable("gui.maidbridge.chat.summary.no_agent").getString() : agentId;
    }

}
