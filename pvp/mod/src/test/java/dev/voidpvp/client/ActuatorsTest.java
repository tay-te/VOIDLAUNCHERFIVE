package dev.voidpvp.client;

import dev.voidpvp.client.actuator.SprintLatch;
import dev.voidpvp.client.actuator.ZoomController;
import dev.voidpvp.client.input.EdgeKey;
import dev.voidpvp.client.input.KeyNames;
import dev.voidpvp.client.render.CrosshairGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plain half of the actuator Mixins (§6.7). */
class ActuatorsTest {

    @Test
    @DisplayName("toggle mode latches on the press, releases on the next one")
    void sprintLatches() {
        SprintLatch latch = new SprintLatch();
        assertFalse(latch.update(true, false, false, true));
        assertTrue(latch.update(true, false, true, true), "pressed: sprinting");
        assertTrue(latch.update(true, false, false, true), "released: still sprinting");
        assertTrue(latch.isLatched());
        assertTrue(latch.update(true, false, true, true), "pressed again");
        assertFalse(latch.update(true, false, false, true), "and it lets go");
    }

    @Test
    @DisplayName("hold mode is vanilla again")
    void sprintHoldMode() {
        SprintLatch latch = new SprintLatch();
        assertTrue(latch.update(true, true, true, true));
        assertFalse(latch.update(true, true, false, true));
        assertFalse(latch.isLatched());
    }

    @Test
    @DisplayName("the latch drops when the mod is off or the player cannot move")
    void sprintDropsWhenDisabled() {
        SprintLatch latch = new SprintLatch();
        latch.update(true, false, true, true);
        assertTrue(latch.isLatched());
        latch.update(false, false, false, true);
        assertFalse(latch.isLatched(), "turning the mod off unlatches");

        latch.update(true, false, true, true);
        latch.update(true, false, false, false);
        assertFalse(latch.isLatched(), "a screen opening unlatches");
    }

    @Test
    @DisplayName("zoom snaps when smooth is off and eases when it is on")
    void zoom() {
        ZoomController zoom = new ZoomController();
        assertEquals(1.0, zoom.update(false, 4, false, 1), 1e-9);
        assertEquals(0.25, zoom.update(true, 4, false, 1), 1e-9, "FOV divided by four");
        assertEquals(1.0, zoom.update(false, 4, false, 1), 1e-9);

        ZoomController smooth = new ZoomController();
        double first = smooth.update(true, 4, true, 1);
        assertTrue(first < 1.0 && first > 0.25, "eased, not snapped: " + first);
        for (int i = 0; i < 200; i++) {
            smooth.update(true, 4, true, 1);
        }
        assertEquals(0.25, smooth.factor(), 1e-6, "and it does arrive");
        assertTrue(smooth.isActive());
        for (int i = 0; i < 200; i++) {
            smooth.update(false, 4, true, 1);
        }
        assertEquals(1.0, smooth.factor(), 1e-6);
        assertFalse(smooth.isActive());
    }

    @Test
    @DisplayName("the zoom divisor is clamped to the schema's range")
    void zoomClampsDivisor() {
        ZoomController zoom = new ZoomController();
        assertEquals(1.0 / 1.1, zoom.update(true, 0.001, false, 1), 1e-9);
        assertEquals(0.1, zoom.update(true, 9999, false, 1), 1e-9);
    }

    @Test
    @DisplayName("a cross is four arms, a t-shape is three, a dot is one")
    void crosshairShapes() {
        assertEquals(4, CrosshairGeometry.rects("cross", 5, 1, 2, 0).size());
        assertEquals(3, CrosshairGeometry.rects("t_shape", 5, 1, 2, 0).size());
        assertEquals(1, CrosshairGeometry.rects("dot", 5, 1, 2, 0).size());
        assertEquals(0, CrosshairGeometry.rects("none", 5, 1, 2, 0).size());
        assertEquals(0, CrosshairGeometry.rects("default", 5, 1, 2, 0).size());
        assertEquals(0, CrosshairGeometry.rects("circle", 5, 1, 2, 0).size(),
                "a circle is a ring, not rectangles");
        assertTrue(CrosshairGeometry.isRing("circle"));
        assertTrue(CrosshairGeometry.keepsVanilla("default"));
        assertFalse(CrosshairGeometry.keepsVanilla("cross"));
    }

    @Test
    @DisplayName("the crosshair is symmetric about the exact centre")
    void crosshairIsCentred() {
        List<CrosshairGeometry.Rect> rects = CrosshairGeometry.rects("cross", 5, 1, 2, 0);
        double sumX = 0;
        double sumY = 0;
        for (CrosshairGeometry.Rect r : rects) {
            sumX += r.x + r.w / 2f;
            sumY += r.y + r.h / 2f;
        }
        assertEquals(0.0, sumX, 1e-6, "the arms balance horizontally");
        assertEquals(0.0, sumY, 1e-6, "and vertically");

        CrosshairGeometry.Rect left = rects.get(0);
        assertEquals(-7f, left.x, 1e-6, "gap 2 plus size 5");
        assertEquals(5f, left.w, 1e-6);
    }

    @Test
    @DisplayName("the dynamic setting only spreads while sprinting")
    void crosshairSpread() {
        assertEquals(0f, CrosshairGeometry.dynamicSpread(false, true), 1e-6);
        assertEquals(0f, CrosshairGeometry.dynamicSpread(true, false), 1e-6);
        assertTrue(CrosshairGeometry.dynamicSpread(true, true) > 0);

        List<CrosshairGeometry.Rect> spread = CrosshairGeometry.rects("cross", 5, 1, 2, 2f);
        assertEquals(-9f, spread.get(0).x, 1e-6, "the gap widened by two");
    }

    @Test
    @DisplayName("a hotkey fires once per press")
    void edgeKey() {
        EdgeKey key = new EdgeKey();
        assertTrue(key.pressed(true));
        assertFalse(key.pressed(true), "holding it is still one press");
        assertFalse(key.pressed(false));
        assertTrue(key.pressed(true));
    }

    @Test
    @DisplayName("key names round-trip through their LWJGL codes")
    void keyNames() {
        for (String name : new String[] {"RSHIFT", "L", "C", "V", "GRAVE", "F5", "NUMPAD3",
                "ESCAPE", "SPACE", "UP", "0", "Z"}) {
            int code = KeyNames.codeOf(name);
            assertTrue(code != KeyNames.KEY_NONE, name + " has no code");
            assertEquals(name, KeyNames.nameOf(code), name + " did not round-trip");
            assertTrue(KeyNames.isValidKeybind(name), name + " is not a legal keybind");
        }
        assertEquals(KeyNames.KEY_NONE, KeyNames.codeOf("NONE"));
        assertEquals("NONE", KeyNames.nameOf(KeyNames.KEY_NONE));
        assertEquals(54, KeyNames.codeOf("RSHIFT"));
        assertEquals(38, KeyNames.codeOf("L"));
    }

    @Test
    @DisplayName("mouse buttons share the key-code space, as Minecraft does")
    void mouseCodes() {
        assertEquals(KeyNames.MOUSE_BASE, KeyNames.codeOf("MOUSE0"));
        assertEquals(KeyNames.MOUSE_BASE + 3, KeyNames.codeOf("MOUSE3"));
        assertEquals("MOUSE3", KeyNames.nameOf(KeyNames.MOUSE_BASE + 3));
        assertTrue(KeyNames.isMouse(KeyNames.codeOf("MOUSE1")));
        assertFalse(KeyNames.isMouse(KeyNames.codeOf("A")));
    }

    @Test
    @DisplayName("keys map to the virtual-key codes Ultralight expects")
    void virtualKeys() {
        assertEquals(0x1B, KeyNames.virtualKey(KeyNames.codeOf("ESCAPE")));
        assertEquals(0x41, KeyNames.virtualKey(KeyNames.codeOf("A")));
        assertEquals(0x20, KeyNames.virtualKey(KeyNames.codeOf("SPACE")));
        assertEquals(0x08, KeyNames.virtualKey(KeyNames.codeOf("BACK")));
        assertEquals(0, KeyNames.virtualKey(9999), "an unknown key has no virtual key");
    }

    @Test
    @DisplayName("every keybind mods.json can express has a code")
    void everySchemaKeybindResolves() {
        for (String name : KeyNames.names().keySet()) {
            assertTrue(KeyNames.isValidKeybind(name),
                    name + " is in the table but not in the schema pattern");
        }
    }
}
