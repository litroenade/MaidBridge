package com.github.litroenade.maidbridge.trace;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;

public final class ReflectiveAccess {
    private ReflectiveAccess() {
    }

    public static Object invoke(Object target, String methodName) {
        if (target == null) {
            return "";
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return "<reflect-error:%s>".formatted(exception.getClass().getSimpleName());
            }
        }
        return "";
    }

    public static Object field(Object target, String fieldName) {
        if (target == null) {
            return "";
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return "<reflect-error:%s>".formatted(exception.getClass().getSimpleName());
            }
        }
        return "";
    }

    public static Object staticInvoke(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className);
            Method method = type.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "<reflect-error:%s>".formatted(exception.getClass().getSimpleName());
        }
    }

    public static String componentText(Object component) {
        Object text = invoke(component, "getString");
        return String.valueOf(text);
    }

    public static int size(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        return 0;
    }

    public static String joinCollection(Object value, String delimiter) {
        if (!(value instanceof Collection<?> collection)) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(delimiter);
        for (Object item : collection) {
            joiner.add(String.valueOf(item));
        }
        return joiner.toString();
    }
}
