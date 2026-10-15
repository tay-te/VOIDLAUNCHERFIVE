package dev.voidpvp.client;

import dev.voidpvp.client.actuator.ChatTweaks;
import dev.voidpvp.client.actuator.ZoomController;
import dev.voidpvp.client.render.HitboxGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The depth workstream's actuators: zoom scroll and sensitivity, hitbox colours, chat. */
class DepthActuatorsTest {

    // -----------------------------------------------------------------
    // zoom.scroll_adjust
    // -----------------------------------------------------------------

    @Test
    @DisplayName("the wheel zooms multiplicatively from fov_divisor while held")
    void zoomScrollSteps() {
        ZoomController z = new ZoomController();
        z.update(true, 4, false, 1);
        assertTrue(z.scroll(true, 120, 4));
        assertEquals(1.0 / 5.0, z.update(true, 4, false, 1), 1e-9, "4 x 1.25 = 5");
        assertTrue(z.scroll(true, -120, 4));
        assertTrue(z.scroll(true, -1, 4), "only the sign of the delta matters");
        assertEquals(1.0 / 3.2, z.update(true, 4, false, 1), 1e-9, "5 / 1.25 / 1.25");
    }

    @Test
    @DisplayName("scrolling is clamped to 1.1..10 and still consumes the wheel at the clamp")
    void zoomScrollClamps() {
        ZoomController z = new ZoomController();
        z.update(true, 9, false, 1);
        assertTrue(z.scroll(true, 120, 9));
        assertEquals(10, z.divisor(9), 1e-9);
        assertTrue(z.scroll(true, 120, 9), "at the clamp the hotbar still must not scroll");
        assertEquals(10, z.divisor(9), 1e-9);
        for (int i = 0; i < 40; i++) {
            z.scroll(true, -120, 9);
        }
        assertEquals(ZoomController.MIN_DIVISOR, z.divisor(9), 1e-9);
        assertEquals(1.0 / 1.1, z.update(true, 9, false, 1), 1e-9);
    }

    @Test
    @DisplayName("not held: the wheel is the hotbar's")
    void zoomScrollNotHeld() {
        ZoomController z = new ZoomController();
        assertFalse(z.scroll(false, 120, 4));
        assertFalse(z.scroll(true, 0, 4), "no delta, nothing to consume");
        assertEquals(4, z.divisor(4), 1e-9);
    }

    @Test
    @DisplayName("release forgets the scrolled level; the next press starts from fov_divisor")
    void zoomScrollResetsOnRelease() {
        ZoomController z = new ZoomController();
        z.update(true, 4, false, 1);
        z.scroll(true, 120, 4);
        z.scroll(true, 120, 4);
        assertEquals(6.25, z.divisor(4), 1e-9);
        assertEquals(1.0, z.update(false, 4, false, 1), 1e-9);
        assertEquals(4, z.divisor(4), 1e-9);
        assertEquals(0.25, z.update(true, 4, false, 1), 1e-9);
    }

    @Test
    @DisplayName("a scroll before the first held frame still counts for that hold")
    void zoomScrollBeforeFirstFrame() {
        ZoomController z = new ZoomController();
        assertTrue(z.scroll(true, 120, 4), "the caller saw the key down on the tick");
        assertEquals(0.2, z.update(true, 4, false, 1), 1e-9);
    }

    @Test
    @DisplayName("with smooth on, a scroll eases to the new level instead of snapping")
    void zoomScrollEases() {
        ZoomController z = new ZoomController();
        for (int i = 0; i < 100; i++) {
            z.update(true, 4, true, 1);
        }
        assertEquals(0.25, z.factor(), 1e-9);
        z.scroll(true, 120, 4);
        double next = z.update(true, 4, true, 1);
        assertTrue(next < 0.25 && next > 0.2, "partway: " + next);
        for (int i = 0; i < 100; i++) {
            z.update(true, 4, true, 1);
        }
        assertEquals(0.2, z.factor(), 1e-9);
    }

    @Test
    @DisplayName("reset forgets the scrolled level too")
    void zoomResetForgetsScroll() {
        ZoomController z = new ZoomController();
        z.update(true, 4, false, 1);
        z.scroll(true, 120, 4);
        z.reset();
        assertEquals(4, z.divisor(4), 1e-9);
        assertFalse(z.isActive());
    }

    // -----------------------------------------------------------------
    // zoom.scale_sensitivity
    // -----------------------------------------------------------------

    @Test
    @DisplayName("scaled sensitivity turns the view by exactly the FOV factor")
    void sensitivityScalesLinearlyWithFov() {
        float[] sensitivities = {0f, 0.1f, 0.5f, 1f};
        double[] factors = {0.25, 0.5, 1.0 / 1.1, 0.1};
        for (float s : sensitivities) {
            for (double f : factors) {
                float scaled = ZoomController.scaledSensitivity(s, f);
                assertEquals(ZoomController.turnMultiplier(s) * f,
                        ZoomController.turnMultiplier(scaled), 1e-5,
                        "s=" + s + " factor=" + f);
            }
        }
    }

    @Test
    @DisplayName("a factor of 1 is the player's sensitivity, bit for bit")
    void sensitivityIdentity() {
        assertEquals(0.5f, ZoomController.scaledSensitivity(0.5f, 1.0), 0f);
        assertEquals(0.37f, ZoomController.scaledSensitivity(0.37f, 1.0), 0f);
        assertEquals(0.5f, ZoomController.scaledSensitivity(0.5f, 0), 0f, "nonsense factor");
        assertTrue(ZoomController.scaledSensitivity(0.5f, 0.25) < 0.5f, "zoomed is slower");
    }

    // -----------------------------------------------------------------
    // hitboxes.state_colors
    // -----------------------------------------------------------------

    @Test
    @DisplayName("box colour: damaged beats hittable beats the base colour")
    void hitboxStateColors() {
        int base = 0xFFFFFFFF;
        int hittable = 0xFF2FB8A6;
        int damaged = 0xFFFF5A5A;
        assertEquals(base, HitboxGeometry.boxColor(false, base, hittable, damaged, true, true),
                "off: always the base colour");
        assertEquals(base, HitboxGeometry.boxColor(true, base, hittable, damaged, false, false));
        assertEquals(hittable,
                HitboxGeometry.boxColor(true, base, hittable, damaged, false, true));
        assertEquals(damaged, HitboxGeometry.boxColor(true, base, hittable, damaged, true, false));
        assertEquals(damaged, HitboxGeometry.boxColor(true, base, hittable, damaged, true, true),
                "a hurt target is in its i-frames: damaged wins");
    }

    // -----------------------------------------------------------------
    // chat
    // -----------------------------------------------------------------

    @Test
    @DisplayName("timestamps and stack suffixes read [HH:mm] and (xN)")
    void chatText() {
        assertEquals("[14:03] ", ChatTweaks.timestamp(14, 3));
        assertEquals("[00:00] ", ChatTweaks.timestamp(0, 0));
        assertEquals("[09:59] ", ChatTweaks.timestamp(9, 59));
        assertEquals(" (x2)", ChatTweaks.stackSuffix(2));
        assertEquals(" (x12)", ChatTweaks.stackSuffix(12));
    }

    @Test
    @DisplayName("each new message gets its own negative id; a repeat reuses it with a count")
    void chatStacking() {
        ChatTweaks chat = new ChatTweaks();
        ChatTweaks.Placement a = chat.place("hello", 0, true);
        assertTrue(a.id < 0);
        assertEquals(1, a.count);
        ChatTweaks.Placement a2 = chat.place("hello", 0, true);
        assertEquals(a.id, a2.id, "re-added under the same id, so vanilla removes the old line");
        assertEquals(2, a2.count);
        assertEquals(3, chat.place("hello", 0, true).count);

        ChatTweaks.Placement b = chat.place("world", 0, true);
        assertTrue(b.id < 0 && b.id != a.id);
        assertEquals(1, b.count);
        ChatTweaks.Placement a3 = chat.place("hello", 0, true);
        assertTrue(a3.id != a.id, "a, b, a is three lines");
        assertEquals(1, a3.count);
    }

    @Test
    @DisplayName("stack_repeats off: repeats get fresh ids")
    void chatStackingOff() {
        ChatTweaks chat = new ChatTweaks();
        ChatTweaks.Placement a = chat.place("spam", 0, false);
        ChatTweaks.Placement b = chat.place("spam", 0, false);
        assertTrue(a.id != b.id);
        assertEquals(1, b.count);
    }

    @Test
    @DisplayName("a caller's own id passes through and breaks the run")
    void chatForeignIds() {
        ChatTweaks chat = new ChatTweaks();
        chat.place("x", 0, true);
        ChatTweaks.Placement own = chat.place("x", 42, true);
        assertEquals(42, own.id);
        assertEquals(1, own.count);
        assertEquals(1, chat.place("x", 0, true).count, "the run was broken");
    }

    @Test
    @DisplayName("forget ends the run")
    void chatForget() {
        ChatTweaks chat = new ChatTweaks();
        ChatTweaks.Placement a = chat.place("x", 0, true);
        chat.forget();
        ChatTweaks.Placement b = chat.place("x", 0, true);
        assertTrue(a.id != b.id);
        assertEquals(1, b.count);
    }

    @Test
    @DisplayName("the original text is kept per id for copying")
    void chatOriginals() {
        ChatTweaks chat = new ChatTweaks();
        ChatTweaks.Placement a = chat.place("§aGreen §rtext", 0, true);
        assertEquals("§aGreen §rtext", chat.original(a.id));
        assertEquals("Green text", ChatTweaks.stripFormatting(chat.original(a.id)));
        assertNull(chat.original(12345));
        assertEquals("plain", ChatTweaks.stripFormatting("plain"));
        assertEquals("", ChatTweaks.stripFormatting("§"));
    }

    @Test
    @DisplayName("background opacity: identity at 0.5, scales the band's alpha otherwise")
    void chatBackground() {
        int vanilla = (200 / 2) << 24;
        assertEquals(vanilla, ChatTweaks.backgroundColor(vanilla, 0.5f));
        assertEquals(0, ChatTweaks.backgroundColor(vanilla, 0f) >>> 24);
        assertEquals(200, ChatTweaks.backgroundColor(vanilla, 1f) >>> 24);
        assertEquals(50, ChatTweaks.backgroundColor(vanilla, 0.25f) >>> 24);
        assertEquals(0x123456, ChatTweaks.backgroundColor(vanilla | 0x123456, 1f) & 0xFFFFFF,
                "only the alpha moves");
        assertEquals(254, ChatTweaks.backgroundColor(127 << 24, 5f) >>> 24, "opacity clamped to 1");
    }

    @Test
    @DisplayName("line index math matches ChatHud.getTextAt's")
    void chatLineIndex() {
        // GUI scale 2, chat scale 1, width 320, font 9, 10 lines visible, 30 stored, no scroll.
        // Bottom line: y in [0, 9) after (mouseY/2 - 27).
        assertEquals(0, ChatTweaks.lineIndexAt(20, 54, 2, 1f, 320, 9, 10, 30, 0));
        assertEquals(0, ChatTweaks.lineIndexAt(20, 54 + 17, 2, 1f, 320, 9, 10, 30, 0));
        assertEquals(1, ChatTweaks.lineIndexAt(20, 54 + 18, 2, 1f, 320, 9, 10, 30, 0));
        assertEquals(3, ChatTweaks.lineIndexAt(20, 54 + 18, 2, 1f, 320, 9, 10, 30, 2),
                "scrolled lines shift the index");
        assertEquals(-1, ChatTweaks.lineIndexAt(20, 40, 2, 1f, 320, 9, 10, 30, 0),
                "below the chat");
        assertEquals(-1, ChatTweaks.lineIndexAt(2, 60, 2, 1f, 320, 9, 10, 30, 0),
                "left of the chat");
        assertEquals(-1, ChatTweaks.lineIndexAt(2 * (320 + 3) + 4, 60, 2, 1f, 320, 9, 10, 30, 0),
                "right of the chat");
        // 10 lines: y < 9 * 10 + 10 = 100 is inside (vanilla's extra pixel per line kept).
        assertEquals(9, ChatTweaks.lineIndexAt(20, 2 * (27 + 89), 2, 1f, 320, 9, 10, 30, 0));
        assertEquals(-1, ChatTweaks.lineIndexAt(20, 2 * (27 + 100), 2, 1f, 320, 9, 10, 30, 0));
        assertEquals(-1, ChatTweaks.lineIndexAt(20, 54 + 18, 2, 1f, 320, 9, 10, 1, 0),
                "only one line stored");
        assertEquals(2, ChatTweaks.lineIndexAt(20, 2 * (27 + 9), 2, 0.5f, 320, 9, 10, 30, 0),
                "chat scale divides the offset");
    }
}
