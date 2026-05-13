package com.github.litroenade.maidbridge.maid.ai.chat.client;

import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 复用 TLM AI 聊天窗口的下拉菜单样式，避免把 MaidBridge 菜单绘制逻辑堆在 mixin 里。
 */
public final class MaidBridgePopupMenu {
    public static final int ROW_HEIGHT = 16;

    private MaidBridgePopupMenu() {
    }

    public static Geometry geometry(Font font, int screenWidth, int screenHeight, FlatColorButton anchor, List<Entry> entries) {
        int width = Math.max(150, anchor == null ? 150 : anchor.getWidth());
        for (var entry : entries) {
            int rowWidth = font.width(entry.label()) + (entry.header() ? 12 : 26);
            width = Math.max(width, rowWidth);
        }
        width = Math.min(220, width);
        int height = entries.size() * ROW_HEIGHT;
        int anchorX = anchor == null ? screenWidth - 8 : anchor.getX() + anchor.getWidth();
        int anchorY = anchor == null ? screenHeight - 8 : anchor.getY();
        int x = anchorX - width;
        int y = anchorY - height - 4;
        if (x < 8) {
            x = 8;
        }
        if (x + width > screenWidth - 8) {
            x = screenWidth - 8 - width;
        }
        if (y < 8) {
            y = 8;
        }
        return new Geometry(x, y, width, entries);
    }

    public static void render(GuiGraphics graphics, Font font, Geometry geometry, int mouseX, int mouseY) {
        int x = geometry.x();
        int y = geometry.y();
        int width = geometry.width();
        graphics.fill(x, y, x + width, y + geometry.height(), 0xF0101010);
        var entries = geometry.entries();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            int top = y + i * ROW_HEIGHT;
            boolean hover = x <= mouseX && mouseX < x + width && top <= mouseY && mouseY < top + ROW_HEIGHT;
            if (entry.header()) {
                drawText(graphics, font, entry.label().getString(), x + 6, top + 4, width - 12, 0xFFF2F2F2);
                continue;
            }
            if (entry.selected()) {
                graphics.fill(x + 1, top + 1, x + width - 1, top + ROW_HEIGHT - 1, 0x1F55ff55);
                graphics.fill(x + 1, top + 1, x + 3, top + ROW_HEIGHT - 1, 0xFF55ff55);
            } else if (hover) {
                graphics.fill(x + 1, top + 1, x + width - 1, top + ROW_HEIGHT - 1, 0xFF3C3C3C);
            }
            int textColor = entry.selected() ? 0xFF55ff55 : hover ? 0xFFF3EFE0 : 0xFF989898;
            drawText(graphics, font, entry.label().getString(), x + 12, top + 4, width - 28, textColor);
        }
    }

    public static String trimToWidth(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String ellipsis = "...";
        int contentWidth = Math.max(1, width - font.width(ellipsis));
        return font.plainSubstrByWidth(text, contentWidth) + ellipsis;
    }

    private static void drawText(GuiGraphics graphics, Font font, String text, int x, int y, int maxWidth, int color) {
        graphics.drawString(font, trimToWidth(font, text, maxWidth), x, y, color, false);
    }

    public record Geometry(int x, int y, int width, List<Entry> entries) {
        public int height() {
            return entries.size() * ROW_HEIGHT;
        }

        public boolean contains(double mouseX, double mouseY) {
            return this.x <= mouseX && mouseX < this.x + this.width
                    && this.y <= mouseY && mouseY < this.y + this.height();
        }

        public int indexAt(double mouseY) {
            return (int) ((mouseY - this.y) / ROW_HEIGHT);
        }
    }

    public record Entry(Component label, boolean header, boolean selected, String mode, boolean refresh) {
        public static Entry header(Component label) {
            return new Entry(label, true, false, "", false);
        }

        public static Entry mode(Component label, String mode, boolean selected) {
            return new Entry(label, false, selected, mode, false);
        }

        public static Entry agent(Component label, boolean selected) {
            return new Entry(label, false, selected, "", false);
        }

        public static Entry refresh(Component label) {
            return new Entry(label, false, false, "", true);
        }
    }
}
