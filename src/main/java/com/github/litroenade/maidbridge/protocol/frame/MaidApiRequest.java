package com.github.litroenade.maidbridge.protocol.frame;

import java.math.BigDecimal;
import java.util.Map;

public record MaidApiRequest(
        String type,
        String id,
        String traceId,
        String sourceEndpoint,
        String targetEndpoint,
        Map<String, Object> payload
) {
    public String stringPayload(String key) {
        return stringValue(payload.get(key));
    }

    public String nestedStringPayload(String objectKey, String key) {
        var nested = payload.get(objectKey);
        if (!(nested instanceof Map<?, ?> values)) {
            return "";
        }
        return stringValue(values.get(key));
    }

    public String maidUuid() {
        return nestedStringPayload("maid", "uuid");
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number number) {
            return numberString(number);
        }
        return String.valueOf(value).trim();
    }

    private static String numberString(Number number) {
        if (number instanceof Double doubleValue) {
            if (Double.isFinite(doubleValue)
                    && doubleValue == Math.rint(doubleValue)
                    && doubleValue >= Long.MIN_VALUE
                    && doubleValue <= Long.MAX_VALUE) {
                return Long.toString(doubleValue.longValue());
            }
            return Double.toString(doubleValue);
        }
        if (number instanceof Float floatValue) {
            if (Float.isFinite(floatValue)
                    && floatValue == Math.rint(floatValue)
                    && floatValue >= Long.MIN_VALUE
                    && floatValue <= Long.MAX_VALUE) {
                return Long.toString(floatValue.longValue());
            }
            return Float.toString(floatValue);
        }
        if (number instanceof BigDecimal decimal) {
            var normalized = decimal.stripTrailingZeros();
            return normalized.scale() <= 0 ? normalized.toPlainString() : decimal.toPlainString();
        }
        return String.valueOf(number).trim();
    }
}
