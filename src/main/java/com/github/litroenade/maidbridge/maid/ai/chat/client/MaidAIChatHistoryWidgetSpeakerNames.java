package com.github.litroenade.maidbridge.maid.ai.chat.client;

import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.HistoryChatWidget;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class MaidAIChatHistoryWidgetSpeakerNames {
    private static final Map<HistoryChatWidget, String> SPEAKER_NAMES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private MaidAIChatHistoryWidgetSpeakerNames() {
    }

    public static void bind(HistoryChatWidget widget, String speakerName) {
        if (StringUtils.isBlank(speakerName)) {
            SPEAKER_NAMES.remove(widget);
            return;
        }
        SPEAKER_NAMES.put(widget, speakerName);
    }

    public static String get(HistoryChatWidget widget) {
        return SPEAKER_NAMES.get(widget);
    }
}
