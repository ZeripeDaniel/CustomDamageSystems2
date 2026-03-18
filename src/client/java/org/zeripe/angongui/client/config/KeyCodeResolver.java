package org.zeripe.angongui.client.config;

import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;

/**
 * 문자열 키 이름을 GLFW 키 코드로 변환한다.
 */
public final class KeyCodeResolver {
    private static final Map<String, Integer> ALIASES = Map.ofEntries(
            Map.entry("SPACE", GLFW.GLFW_KEY_SPACE),
            Map.entry("TAB", GLFW.GLFW_KEY_TAB),
            Map.entry("ENTER", GLFW.GLFW_KEY_ENTER),
            Map.entry("ESC", GLFW.GLFW_KEY_ESCAPE),
            Map.entry("ESCAPE", GLFW.GLFW_KEY_ESCAPE),
            Map.entry("LEFT_SHIFT", GLFW.GLFW_KEY_LEFT_SHIFT),
            Map.entry("RIGHT_SHIFT", GLFW.GLFW_KEY_RIGHT_SHIFT),
            Map.entry("LEFT_CTRL", GLFW.GLFW_KEY_LEFT_CONTROL),
            Map.entry("RIGHT_CTRL", GLFW.GLFW_KEY_RIGHT_CONTROL),
            Map.entry("LEFT_ALT", GLFW.GLFW_KEY_LEFT_ALT),
            Map.entry("RIGHT_ALT", GLFW.GLFW_KEY_RIGHT_ALT)
    );

    private KeyCodeResolver() {}

    public static int resolveOrDefault(String keyName, int defaultCode) {
        if (keyName == null || keyName.isBlank()) {
            return defaultCode;
        }

        String normalized = keyName.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');

        Integer alias = ALIASES.get(normalized);
        if (alias != null) return alias;

        if (normalized.length() == 1) {
            char c = normalized.charAt(0);
            if (c >= 'A' && c <= 'Z') {
                return GLFW.GLFW_KEY_A + (c - 'A');
            }
            if (c >= '0' && c <= '9') {
                return GLFW.GLFW_KEY_0 + (c - '0');
            }
        }

        // 예: F1, F12, LEFT_SHIFT, KP_1 등 GLFW 상수명 직접 매칭
        String fieldName = "GLFW_KEY_" + normalized;
        try {
            Field field = GLFW.class.getField(fieldName);
            return field.getInt(null);
        } catch (Exception ignored) {
            return defaultCode;
        }
    }
}
