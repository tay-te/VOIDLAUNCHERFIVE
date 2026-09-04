package dev.voidpvp.client.input;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * LWJGL 2 key names, the {@code keybind} vocabulary of
 * {@code schema/mods.json#/definitions/keybind}: an upper-case key name as
 * produced by Minecraft 1.8.9's {@code Keyboard.getKeyName}, a mouse button as
 * {@code MOUSE0..MOUSE7}, or {@code NONE} for unbound.
 *
 * <p>Kept free of any Minecraft or LWJGL import so it is unit-testable: the
 * numeric codes below are LWJGL 2's {@code Keyboard.KEY_*} constants, which are
 * fixed by the DirectInput scan-code table and will not move.</p>
 */
public final class KeyNames {

    /** Sentinel used for {@code NONE} and for names we do not know. */
    public static final int KEY_NONE = 0;

    public static final int KEY_ESCAPE = 1;
    public static final int KEY_LSHIFT = 42;
    public static final int KEY_RSHIFT = 54;

    /** Mouse buttons are encoded as negative key codes, as Minecraft does. */
    public static final int MOUSE_BASE = -100;

    private static final Pattern KEYBIND = Pattern.compile(
            "^(?:NONE|[A-Z0-9]|F[1-9]|F1[0-2]|NUMPAD[0-9]|MOUSE[0-7]|SPACE|TAB|ESCAPE|RETURN|BACK"
                    + "|DELETE|INSERT|HOME|END|PRIOR|NEXT|UP|DOWN|LEFT|RIGHT|LSHIFT|RSHIFT|LCONTROL"
                    + "|RCONTROL|LMENU|RMENU|CAPITAL|LBRACKET|RBRACKET|SEMICOLON|APOSTROPHE|COMMA"
                    + "|PERIOD|SLASH|BACKSLASH|MINUS|EQUALS|GRAVE)$");

    private static final Map<String, Integer> BY_NAME = new LinkedHashMap<String, Integer>();
    private static final Map<Integer, String> BY_CODE = new LinkedHashMap<Integer, String>();
    /** Windows virtual-key codes, which is what Ultralight's key events expect. */
    private static final Map<Integer, Integer> VK = new LinkedHashMap<Integer, Integer>();

    private KeyNames() {
    }

    private static void put(String name, int code, int virtualKey) {
        BY_NAME.put(name, Integer.valueOf(code));
        if (!BY_CODE.containsKey(Integer.valueOf(code))) {
            BY_CODE.put(Integer.valueOf(code), name);
        }
        VK.put(Integer.valueOf(code), Integer.valueOf(virtualKey));
    }

    static {
        put("ESCAPE", 1, 0x1B);
        put("1", 2, 0x31);
        put("2", 3, 0x32);
        put("3", 4, 0x33);
        put("4", 5, 0x34);
        put("5", 6, 0x35);
        put("6", 7, 0x36);
        put("7", 8, 0x37);
        put("8", 9, 0x38);
        put("9", 10, 0x39);
        put("0", 11, 0x30);
        put("MINUS", 12, 0xBD);
        put("EQUALS", 13, 0xBB);
        put("BACK", 14, 0x08);
        put("TAB", 15, 0x09);
        put("Q", 16, 0x51);
        put("W", 17, 0x57);
        put("E", 18, 0x45);
        put("R", 19, 0x52);
        put("T", 20, 0x54);
        put("Y", 21, 0x59);
        put("U", 22, 0x55);
        put("I", 23, 0x49);
        put("O", 24, 0x4F);
        put("P", 25, 0x50);
        put("LBRACKET", 26, 0xDB);
        put("RBRACKET", 27, 0xDD);
        put("RETURN", 28, 0x0D);
        put("LCONTROL", 29, 0x11);
        put("A", 30, 0x41);
        put("S", 31, 0x53);
        put("D", 32, 0x44);
        put("F", 33, 0x46);
        put("G", 34, 0x47);
        put("H", 35, 0x48);
        put("J", 36, 0x4A);
        put("K", 37, 0x4B);
        put("L", 38, 0x4C);
        put("SEMICOLON", 39, 0xBA);
        put("APOSTROPHE", 40, 0xDE);
        put("GRAVE", 41, 0xC0);
        put("LSHIFT", 42, 0x10);
        put("BACKSLASH", 43, 0xDC);
        put("Z", 44, 0x5A);
        put("X", 45, 0x58);
        put("C", 46, 0x43);
        put("V", 47, 0x56);
        put("B", 48, 0x42);
        put("N", 49, 0x4E);
        put("M", 50, 0x4D);
        put("COMMA", 51, 0xBC);
        put("PERIOD", 52, 0xBE);
        put("SLASH", 53, 0xBF);
        put("RSHIFT", 54, 0x10);
        put("LMENU", 56, 0x12);
        put("SPACE", 57, 0x20);
        put("CAPITAL", 58, 0x14);
        put("F1", 59, 0x70);
        put("F2", 60, 0x71);
        put("F3", 61, 0x72);
        put("F4", 62, 0x73);
        put("F5", 63, 0x74);
        put("F6", 64, 0x75);
        put("F7", 65, 0x76);
        put("F8", 66, 0x77);
        put("F9", 67, 0x78);
        put("F10", 68, 0x79);
        put("NUMPAD7", 71, 0x67);
        put("NUMPAD8", 72, 0x68);
        put("NUMPAD9", 73, 0x69);
        put("NUMPAD4", 75, 0x64);
        put("NUMPAD5", 76, 0x65);
        put("NUMPAD6", 77, 0x66);
        put("NUMPAD1", 79, 0x61);
        put("NUMPAD2", 80, 0x62);
        put("NUMPAD3", 81, 0x63);
        put("NUMPAD0", 82, 0x60);
        put("F11", 87, 0x7A);
        put("F12", 88, 0x7B);
        put("RCONTROL", 157, 0x11);
        put("RMENU", 184, 0x12);
        put("HOME", 199, 0x24);
        put("UP", 200, 0x26);
        put("PRIOR", 201, 0x21);
        put("LEFT", 203, 0x25);
        put("RIGHT", 205, 0x27);
        put("END", 207, 0x23);
        put("DOWN", 208, 0x28);
        put("NEXT", 209, 0x22);
        put("INSERT", 210, 0x2D);
        put("DELETE", 211, 0x2E);
    }

    /** True when {@code name} matches the schema's {@code keybind} pattern. */
    public static boolean isValidKeybind(String name) {
        return name != null && KEYBIND.matcher(name).matches();
    }

    /**
     * Resolves a schema keybind name to an LWJGL key code. Mouse buttons come
     * back as {@code MOUSE_BASE + n}, matching Minecraft's own convention;
     * {@code NONE} and unknown names come back as {@link #KEY_NONE}.
     */
    public static int codeOf(String name) {
        if (name == null) {
            return KEY_NONE;
        }
        String up = name.toUpperCase(Locale.ROOT);
        if ("NONE".equals(up)) {
            return KEY_NONE;
        }
        if (up.length() == 6 && up.startsWith("MOUSE")) {
            char c = up.charAt(5);
            if (c >= '0' && c <= '7') {
                return MOUSE_BASE + (c - '0');
            }
            return KEY_NONE;
        }
        Integer code = BY_NAME.get(up);
        return code == null ? KEY_NONE : code.intValue();
    }

    /** Inverse of {@link #codeOf}; {@code NONE} when the code is not known. */
    public static String nameOf(int code) {
        if (code == KEY_NONE) {
            return "NONE";
        }
        if (code >= MOUSE_BASE && code <= MOUSE_BASE + 7) {
            return "MOUSE" + (code - MOUSE_BASE);
        }
        String name = BY_CODE.get(Integer.valueOf(code));
        return name == null ? "NONE" : name;
    }

    /** True for codes that denote a mouse button rather than a keyboard key. */
    public static boolean isMouse(int code) {
        return code >= MOUSE_BASE && code <= MOUSE_BASE + 7;
    }

    /**
     * Windows virtual-key code for an LWJGL key code, which is the
     * {@code virtualKey} argument of {@code View.fireKeyEvent}. Unknown keys
     * map to 0, which Ultralight treats as "no virtual key".
     */
    public static int virtualKey(int lwjglCode) {
        Integer vk = VK.get(Integer.valueOf(lwjglCode));
        return vk == null ? 0 : vk.intValue();
    }

    /** Every name this table knows, for tests and diagnostics. */
    public static Map<String, Integer> names() {
        return Collections.unmodifiableMap(BY_NAME);
    }
}
