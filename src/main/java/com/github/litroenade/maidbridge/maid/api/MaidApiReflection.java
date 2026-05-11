package com.github.litroenade.maidbridge.maid.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MaidApiReflection {
    static final Gson GSON = new Gson();

    private MaidApiReflection() {
    }

    public static Object staticInvoke(String className, String methodName, Object... args) {
        try {
            return invoke(Class.forName(className), null, methodName, args);
        } catch (InvocationTargetException exception) {
            throw invocationFailure("TouhouLittleMaid static method " + className + "." + methodName, exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("调用 TouhouLittleMaid 静态方法失败：" + className + "." + methodName, exception);
        }
    }

    public static Object staticField(String className, String fieldName) {
        try {
            Class<?> type = Class.forName(className);
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("读取 TouhouLittleMaid 静态字段失败：" + className + "." + fieldName, exception);
        }
    }

    public static Object invoke(Object target, String methodName, Object... args) {
        if (target == null) {
            throw new IllegalArgumentException("不能在 null TouhouLittleMaid 对象上调用：" + methodName);
        }
        Class<?> targetType = target.getClass();
        try {
            return invoke(targetType, target, methodName, args);
        } catch (InvocationTargetException exception) {
            throw invocationFailure("TouhouLittleMaid method " + targetType.getName() + "." + methodName, exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("调用 TouhouLittleMaid 方法失败：" + targetType.getName() + "." + methodName, exception);
        }
    }

    public static void invokeExact(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            throw new IllegalArgumentException("不能在 null TouhouLittleMaid 对象上调用：" + methodName);
        }
        Class<?> targetType = target.getClass();
        try {
            Method method = targetType.getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw invocationFailure("TouhouLittleMaid method " + targetType.getName() + "." + methodName, exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("调用 TouhouLittleMaid 方法失败：" + targetType.getName() + "." + methodName, exception);
        }
    }

    static Object construct(String className, Class<?>[] parameterTypes, Object... args) {
        try {
            var constructor = Class.forName(className).getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (InvocationTargetException exception) {
            throw invocationFailure("TouhouLittleMaid constructor " + className, exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("构造 TouhouLittleMaid 类失败：" + className, exception);
        }
    }

    public static String clean(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static Object jsonCompatible(Object value) {
        return jsonElementToJava(GSON.toJsonTree(value));
    }

    static Object jsonElementToJava(JsonElement element) {
        if (element == null || element instanceof JsonNull || element.isJsonNull()) {
            return null;
        }
        if (element instanceof JsonObject object) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (var entry : object.entrySet()) {
                map.put(entry.getKey(), jsonElementToJava(entry.getValue()));
            }
            return map;
        }
        if (element instanceof JsonArray array) {
            List<Object> list = new ArrayList<>();
            for (JsonElement child : array) {
                list.add(jsonElementToJava(child));
            }
            return list;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsNumber();
        }
        return primitive.getAsString();
    }

    static String stringValue(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).trim().isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    static Map<String, Object> metadataValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        map.forEach((key, nested) -> metadata.put(clean(key), clean(nested)));
        return metadata;
    }

    private static Object invoke(Class<?> owner, Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(owner, methodName, target == null, args);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static RuntimeException invocationFailure(String callSite, InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        String message = cause == null || cause.getMessage() == null ? exception.getMessage() : cause.getMessage();
        if (cause instanceof RuntimeException runtimeException) {
            return new IllegalStateException("调用失败：" + callSite + "：" + message, runtimeException);
        }
        return new IllegalStateException("调用失败：" + callSite + "：" + message, cause);
    }

    private static Method findMethod(Class<?> owner, String methodName, boolean staticOnly, Object[] args) throws NoSuchMethodException {
        if (owner == null) {
            throw new NoSuchMethodException("<空>." + methodName);
        }
        Class<?> type = owner;
        while (type != null) {
            Method declaredMethod = findMatchingMethod(type.getDeclaredMethods(), methodName, staticOnly, args);
            if (declaredMethod != null) {
                return declaredMethod;
            }
            Method interfaceMethod = findInterfaceMethod(type, methodName, staticOnly, args);
            if (interfaceMethod != null) {
                return interfaceMethod;
            }
            type = type.getSuperclass();
        }
        throw new NoSuchMethodException(owner.getName() + "." + methodName);
    }

    private static Method findInterfaceMethod(Class<?> owner, String methodName, boolean staticOnly, Object[] args) {
        if (owner == null) {
            return null;
        }
        for (Class<?> interfaceType : owner.getInterfaces()) {
            Method method = findMatchingMethod(interfaceType.getMethods(), methodName, staticOnly, args);
            if (method != null) {
                return method;
            }
            Method nested = findInterfaceMethod(interfaceType, methodName, staticOnly, args);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static Method findMatchingMethod(Method[] methods, String methodName, boolean staticOnly, Object[] args) {
        for (Method method : methods) {
            if (method == null) {
                continue;
            }
            if (!methodName.equals(method.getName()) || method.getParameterCount() != args.length) {
                continue;
            }
            if (staticOnly && !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (parametersMatch(method.getParameterTypes(), args)) {
                return method;
            }
        }
        return null;
    }

    private static boolean parametersMatch(Class<?>[] parameterTypes, Object[] args) {
        for (int index = 0; index < parameterTypes.length; index++) {
            Object arg = args[index];
            if (arg == null) {
                continue;
            }
            Class<?> parameterType = wrap(parameterTypes[index]);
            if (!parameterType.isAssignableFrom(arg.getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}
