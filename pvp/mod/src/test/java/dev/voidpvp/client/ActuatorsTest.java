package dev.voidpvp.client;

import dev.voidpvp.client.actuator.DamageTint;
import dev.voidpvp.client.actuator.FovLock;
import dev.voidpvp.client.actuator.FreeLook;
import dev.voidpvp.client.actuator.HitTint;
import dev.voidpvp.client.actuator.OldAnimations;
import dev.voidpvp.client.actuator.OldInput;
import dev.voidpvp.client.actuator.SprintLatch;
import dev.voidpvp.client.actuator.ZoomController;
import dev.voidpvp.client.input.EdgeKey;
import dev.voidpvp.client.input.KeyNames;
import dev.voidpvp.client.render.CrosshairGeometry;
import dev.voidpvp.client.sensor.OwnHits;
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
    @DisplayName("the FOV lock drops the half it was asked for and keeps the other")
    void fovLockIsTwoIndependentSwitches() {
        // What 1.8.9's getSpeed() hands back is one number: the movement-speed term times the
        // bow term. These are a sprint (about 1.15) with the bow half-drawn (10 ticks -> 0.9625).
        float bow = FovLock.bowFactor(true, 10);
        assertEquals(1f - 0.25f * 0.15f, bow, 1e-6, "10 of 20 ticks is a quarter of the pull");
        float sprinting = 1.15f * bow;

        // Off, and on-but-with-nothing-locked, are both exactly vanilla: a mod that is not
        // suppressing anything must not perturb the number at all.
        assertEquals(sprinting, FovLock.apply(false, true, true, sprinting, bow), 1e-6);
        assertEquals(sprinting, FovLock.apply(true, false, false, sprinting, bow), 1e-6);

        // Both locked: the multiplier is the identity, so the player's own `fov` is what
        // reaches the projection.
        assertEquals(1f, FovLock.apply(true, true, true, sprinting, bow), 1e-6);

        // lock_sprint only — the mod's default. The speed term is gone and the bow's zoom,
        // which is draw feedback rather than noise, is exactly what it was.
        assertEquals(bow, FovLock.apply(true, true, false, sprinting, bow), 1e-6);

        // lock_bow only: the bow term is divided back out and the speed term survives whole.
        assertEquals(1.15f, FovLock.apply(true, false, true, sprinting, bow), 1e-5);
    }

    @Test
    @DisplayName("the bow factor is vanilla's curve, and holding past a full draw stops moving")
    void bowFactorMatchesVanilla() {
        assertEquals(1f, FovLock.bowFactor(false, 20), 1e-6, "no bow, no zoom");
        assertEquals(1f, FovLock.bowFactor(true, 0), 1e-6, "the frame the draw starts");
        // Vanilla squares the fraction before scaling it, so the pull is slow then fast.
        assertEquals(1f - 0.25f * 0.15f, FovLock.bowFactor(true, 10), 1e-6);
        assertEquals(0.85f, FovLock.bowFactor(true, 20), 1e-6, "a full draw is the whole 15%");
        assertEquals(0.85f, FovLock.bowFactor(true, 200), 1e-6,
                "and holding it longer does not zoom further");
    }

    @Test
    @DisplayName("the FOV lock never hands the projection a NaN")
    void fovLockSurvivesNonsense() {
        // getSpeed() has NaN and infinity guards of its own precisely because a zero walk speed
        // reaches it, and a NaN in the field of view is a black frame rather than a wide one.
        assertTrue(Float.isNaN(FovLock.apply(true, false, true, Float.NaN, 0.9f))
                || FovLock.apply(true, false, true, Float.NaN, 0.9f) == 1f,
                "a NaN in is a NaN or the identity out, never a silent number");
        assertEquals(1.2f, FovLock.apply(true, false, true, 1.2f, 0f), 1e-6,
                "a zero bow factor cannot divide, so vanilla stands");
        assertEquals(1f, FovLock.apply(true, true, true, Float.NaN, Float.NaN), 1e-6,
                "with both locked there is nothing to compute from");
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
    @DisplayName("center_dot adds one rectangle to every style that draws, and none to those that do not")
    void crosshairCenterDot() {
        // The dot rides on top of whatever the style draws around it.
        assertEquals(5, CrosshairGeometry.rects("cross", 5, 1, 2, 0, true).size());
        assertEquals(4, CrosshairGeometry.rects("t_shape", 5, 1, 2, 0, true).size());
        // A ring draws no rectangles of its own, so the dot is the only one it has.
        assertEquals(1, CrosshairGeometry.rects("circle", 5, 1, 2, 0, true).size());
        // Already a dot: drawing it twice would run the outline pass over one rectangle twice.
        assertEquals(1, CrosshairGeometry.rects("dot", 5, 1, 2, 0, true).size());
        // Off is off, and `default` is the vanilla pass — neither gains a dot.
        assertEquals(0, CrosshairGeometry.rects("none", 5, 1, 2, 0, true).size());
        assertEquals(0, CrosshairGeometry.rects("default", 5, 1, 2, 0, true).size());

        // On the centre point, whatever the gap is.
        CrosshairGeometry.Rect dot = CrosshairGeometry.rects("cross", 5, 3, 7, 0, true).get(0);
        assertEquals(-1.5f, dot.x, 1e-6);
        assertEquals(-1.5f, dot.y, 1e-6);
        assertEquals(3f, dot.w, 1e-6);
        assertEquals(3f, dot.h, 1e-6);
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

    /**
     * The character filter that decides what reaches a focused field.
     *
     * <p>Regression: Down arrow used to append a character to the quick palette's query on every
     * press, because LWJGL 2 on macOS reports the event character for the navigation keys as
     * AppKit's private-use codepoints ({@code NSDownArrowFunctionKey} is U+F701) and the old test
     * — "at least 32 and not 127" — waved them through. The key event itself was always correct;
     * it was the phantom character behind it that reset the selection.</p>
     */
    @Test
    @DisplayName("only real typed text reaches a focused field")
    void typedText() {
        for (char c : new char[] {'a', 'Z', '0', ' ', '/', '\u00e9', '\u4e2d', '\u20ac'}) {
            assertTrue(KeyNames.isTypedText(c), "U+" + Integer.toHexString(c) + " is typed text");
        }
        // C0 controls and DEL: Enter, Tab, Backspace and Escape all arrive with one of these,
        // and every one of them is a command rather than something to insert.
        for (char c : new char[] {'\0', '\b', '\t', '\n', '\r', '\u001b', '\u007f'}) {
            assertFalse(KeyNames.isTypedText(c), "U+" + Integer.toHexString(c) + " is not text");
        }
        // AppKit's function-key block, and the private-use area it sits in.
        assertFalse(KeyNames.isTypedText('\uF700'), "NSUpArrowFunctionKey");
        assertFalse(KeyNames.isTypedText('\uF701'), "NSDownArrowFunctionKey");
        assertFalse(KeyNames.isTypedText('\uF702'), "NSLeftArrowFunctionKey");
        assertFalse(KeyNames.isTypedText('\uF703'), "NSRightArrowFunctionKey");
        assertFalse(KeyNames.isTypedText('\uE000'), "start of the private-use area");
        assertFalse(KeyNames.isTypedText('\uF8FF'), "end of the private-use area");
        // The characters either side of the range are ordinary text and must survive.
        assertTrue(KeyNames.isTypedText('\uDFFF'));
        assertTrue(KeyNames.isTypedText('\uF900'));
    }


    // -----------------------------------------------------------------
    // Freelook
    // -----------------------------------------------------------------

    @Test
    @DisplayName("hold engages on the key and lets go with it, starting from the player's own view")
    void freelookHold() {
        FreeLook look = new FreeLook();
        assertFalse(look.update(true, true, false, true, 30f, -5f));
        assertTrue(look.update(true, true, true, true, 30f, -5f), "pressed: engaged");
        assertEquals(30f, look.yaw(), 1e-6, "the camera starts where the player was looking");
        assertEquals(-5f, look.pitch(), 1e-6);
        assertFalse(look.justReleased());
        assertFalse(look.update(true, true, false, true, 30f, -5f), "released: over");
        assertTrue(look.justReleased(), "and the release is announced once, for snap_back");
    }

    @Test
    @DisplayName("toggle latches on the press and lets go on the next one")
    void freelookToggle() {
        FreeLook look = new FreeLook();
        assertTrue(look.update(true, false, true, true, 0f, 0f), "pressed: engaged");
        assertTrue(look.update(true, false, true, true, 0f, 0f), "held: still the same press");
        assertTrue(look.update(true, false, false, true, 0f, 0f), "released: still engaged");
        // On the press and not on the release: a toggle that waited for the key to come up would
        // be a hold with a delay, and the second tap is the player asking for their view back
        // now. `SprintLatch` differs here for a reason of its own — it ORs the raw key back in
        // because sprint must follow the key while it is down — and freelook has no such term.
        assertFalse(look.update(true, false, true, true, 0f, 0f), "pressed again: it lets go");
        assertFalse(look.update(true, false, false, true, 0f, 0f), "and stays let go");
    }

    @Test
    @DisplayName("a screen or a switched-off mod ends it, and re-engaging re-reads the player")
    void freelookReleases() {
        FreeLook look = new FreeLook();
        look.update(true, false, true, true, 90f, 0f);
        assertTrue(look.isEngaged());
        assertFalse(look.update(true, false, false, false, 90f, 0f), "canLook false ends it");
        assertTrue(look.justReleased());

        look.update(true, false, false, true, 90f, 0f);
        look.update(true, false, true, true, 90f, 0f);
        assertTrue(look.isEngaged());
        assertFalse(look.update(false, false, true, true, 90f, 0f), "the mod going off ends it");
        assertFalse(look.update(true, false, true, true, 90f, 0f),
                "and a key still held from before does not re-engage it by itself");

        // forceRelease is the game loop's own release and must not touch the key edge: after it,
        // a key that is still down does not re-latch the toggle by itself.
        look.update(true, false, false, true, 10f, 20f);
        look.update(true, false, true, true, 10f, 20f);
        assertTrue(look.isEngaged());
        assertTrue(look.forceRelease());
        assertTrue(look.justReleased());
        assertFalse(look.forceRelease(), "releasing twice is not two releases");

        look.update(true, false, false, true, 45f, 1f);
        look.update(true, false, true, true, 45f, 1f);
        assertTrue(look.isEngaged());
        assertEquals(45f, look.yaw(), 1e-6, "re-engaging re-reads the player's own view");
    }

    /**
     * The camera turn is vanilla's {@code Entity.increaseTransforms}, so a freelook sweep and a
     * vanilla sweep of the same mouse travel land on the same angle. The reference below is that
     * method transcribed from the 1.8.9 bytecode.
     */
    @Test
    @DisplayName("the camera turns at vanilla's own arithmetic and vanilla's own precision")
    void freelookTurnsLikeVanilla() {
        float yaw = 17.35f;
        float pitch = -3.25f;
        float[] deltas = {0.7f, -12.4f, 133.75f, -0.031f, 0f};
        for (float d : deltas) {
            float vanillaYaw = (float) ((double) yaw + (double) d * 0.15D);
            float vanillaPitch = (float) ((double) pitch - (double) d * 0.15D);
            vanillaPitch = vanillaPitch < -90f ? -90f : (vanillaPitch > 90f ? 90f : vanillaPitch);
            yaw = FreeLook.turnYaw(yaw, d);
            pitch = FreeLook.turnPitch(pitch, d);
            assertEquals(vanillaYaw, yaw, 0f, "yaw must match bit for bit, not approximately");
            assertEquals(vanillaPitch, pitch, 0f);
        }
    }

    @Test
    @DisplayName("the camera pitch clamps where vanilla clamps and the yaw does not wrap")
    void freelookPitchClamps() {
        FreeLook look = new FreeLook();
        look.update(true, true, true, true, 0f, 0f);
        look.look(0f, -10000f);
        assertEquals(90f, look.pitch(), 0f, "MathHelper.clamp(pitch, -90, 90), upper end");
        look.look(0f, 10000f);
        assertEquals(-90f, look.pitch(), 0f, "and the lower");
        // Yaw is deliberately unwrapped, exactly as vanilla leaves it: `transformCamera` feeds it
        // to sin/cos, which do not care, and normalising here would be a second opinion.
        look.look(10000f, 0f);
        assertTrue(look.yaw() > 360f);
    }

    /**
     * The one place the mod is allowed to say anything about where the eye sits — and the
     * complete set of answers it can give is vanilla's own three F5 states.
     */
    @Test
    @DisplayName("perspective can only ever be one of vanilla's own three, and free is the pivot")
    void freelookPerspectiveIsVanillasOwn() {
        assertEquals(0, FreeLook.perspectiveFor("free"),
                "free is vanilla's first-person arm: no third-person translation, the eye is "
                        + "the pivot");
        assertEquals(1, FreeLook.perspectiveFor("third_back"));
        assertEquals(2, FreeLook.perspectiveFor("third_front"));
        // Every string that can reach this, from anywhere, lands inside {0,1,2}.
        String[] hostile = {null, "", "freecam", "free ", "THIRD_BACK", "3", "-1", "spectator"};
        for (String s : hostile) {
            int p = FreeLook.perspectiveFor(s);
            assertTrue(p >= 0 && p <= 2, s + " escaped vanilla's perspective range");
        }
        assertEquals(0, FreeLook.perspectiveFor("freecam"),
                "anything unrecognised falls to the arm with no translation in it");
    }

    // -----------------------------------------------------------------
    // Hit colour
    // -----------------------------------------------------------------

    /**
     * The number the whole §11 classification of {@code hit_color} rests on.
     * {@code LivingEntityRenderer.method_10252} puts {@code 1, 0, 0, 0.3f} into the texture-env
     * colour on the hurt branch, and the lightmap unit interpolates by that alpha — so 0.3 is
     * the entire strength of vanilla's flash and there is nothing above it.
     */
    @Test
    @DisplayName("intensity 1 is vanilla's own 0.3 alpha, and nothing can exceed it")
    void hitTintCeilingIsVanillas() {
        assertEquals(0.3f, HitTint.VANILLA_ALPHA, 0f);
        assertEquals(0.3f, HitTint.alpha(1f), 0f, "1 is exactly what the game already draws");
        assertEquals(0.15f, HitTint.alpha(0.5f), 1e-6, "a fraction of vanilla's, not of opaque");
        assertEquals(0f, HitTint.alpha(0f), 0f, "0 recolours nothing");
        assertEquals(0.3f, HitTint.alpha(4f), 0f, "out-of-schema values clamp at vanilla's");
        assertEquals(0f, HitTint.alpha(-1f), 0f);
        assertEquals(0f, HitTint.alpha(Float.NaN), 0f);
    }

    @Test
    @DisplayName("colour owns hue and only hue: an alpha byte in it is not read")
    void hitTintColourCarriesNoAlpha() {
        int teal = 0x2FB8A6;
        assertEquals(0x2F / 255f, HitTint.red(teal), 1e-6);
        assertEquals(0xB8 / 255f, HitTint.green(teal), 1e-6);
        assertEquals(0xA6 / 255f, HitTint.blue(teal), 1e-6);
        // The same three channels out of a value that still carries an alpha byte in bits 24-31.
        int withAlpha = 0x002FB8A6 | 0xFF000000;
        assertEquals(HitTint.red(teal), HitTint.red(withAlpha), 0f);
        assertEquals(HitTint.green(teal), HitTint.green(withAlpha), 0f);
        assertEquals(HitTint.blue(teal), HitTint.blue(withAlpha), 0f);
    }

    @Test
    @DisplayName("the recolour can only ever land on a flash vanilla already drew")
    void hitTintNeverAddsAFlash() {
        assertFalse(HitTint.tints(true, false, false, true),
                "vanilla drew nothing, so there is nothing to recolour");
        assertFalse(HitTint.tints(false, true, false, true), "the mod is off");
        assertTrue(HitTint.tints(true, true, false, false),
                "own_hits_only off: every flash vanilla drew");
        assertFalse(HitTint.tints(true, true, true, false),
                "own_hits_only on: somebody else's fight keeps vanilla's red");
        assertTrue(HitTint.tints(true, true, true, true));
    }

    @Test
    @DisplayName("a swing is 'mine' for vanilla's hurt animation plus a round trip, then not")
    void ownHitsWindow() {
        OwnHits hits = new OwnHits();
        assertFalse(hits.isOwn(42, 1000L), "never swung at");
        hits.swungAt(42, 1000L);
        assertTrue(hits.isOwn(42, 1000L));
        assertTrue(hits.isOwn(42, 1000L + OwnHits.WINDOW_MS), "the boundary is inclusive");
        assertFalse(hits.isOwn(42, 1001L + OwnHits.WINDOW_MS));
        assertFalse(hits.isOwn(43, 1000L), "and it is per entity");

        hits.swungAt(42, 5000L);
        assertTrue(hits.isOwn(42, 5000L), "a fresh swing refreshes rather than duplicates");
        hits.clear();
        assertFalse(hits.isOwn(42, 5000L), "a world change reissues entity ids");
    }

    // -----------------------------------------------------------------
    // Damage tint
    // -----------------------------------------------------------------

    @Test
    @DisplayName("the hurt roll keeps its direction and loses its amplitude, or all of it")
    void hurtShakeScales() {
        assertEquals(14f, DamageTint.VANILLA_SHAKE, 0f, "GameRenderer.bobViewWhenHurt's ldc");
        assertEquals(14f, DamageTint.shakeDegrees(false, "off"),
                0f, "with the mod off the substitution is the identity");
        assertEquals(14f, DamageTint.shakeDegrees(true, "vanilla"), 0f);
        assertEquals(0f, DamageTint.shakeDegrees(true, "off"), 0f);
        assertEquals(3.5f, DamageTint.shakeDegrees(true, "reduced"), 1e-6);
        assertEquals(14f, DamageTint.shakeDegrees(true, "sideways"),
                0f, "an unknown mode is the value that changes nothing");
        assertEquals(14f, DamageTint.shakeDegrees(true, null), 0f);
    }

    @Test
    @DisplayName("the vignette ramps from nothing at the threshold to strength at zero health")
    void vignetteRamps() {
        assertEquals(0f, DamageTint.vignetteAlpha(false, 1f, 6, 0.6f), 0f, "the mod is off");
        assertEquals(0f, DamageTint.vignetteAlpha(true, 20f, 6, 0.6f), 0f, "full health");
        assertEquals(0f, DamageTint.vignetteAlpha(true, 6f, 6, 0.6f), 0f,
                "at the threshold the ramp has not started");
        assertEquals(0.3f, DamageTint.vignetteAlpha(true, 3f, 6, 0.6f), 1e-6, "half way down");
        assertEquals(0.6f, DamageTint.vignetteAlpha(true, 0f, 6, 0.6f), 1e-6, "dead: the peak");
        // getHealth() is a float, so the boundary is crossed at exactly the value that was set.
        assertTrue(DamageTint.vignetteAlpha(true, 5.9f, 6, 0.6f) > 0f);
        assertEquals(0f, DamageTint.vignetteAlpha(true, 6.1f, 6, 0.6f), 0f);
        assertEquals(0f, DamageTint.vignetteAlpha(true, 3f, 6, 0f), 0f, "strength 0 draws nothing");
        assertEquals(0f, DamageTint.vignetteAlpha(true, Float.NaN, 6, 0.6f), 0f);
        // Out-of-schema thresholds clamp instead of dividing by zero or running off the scale.
        assertEquals(0f, DamageTint.vignetteAlpha(true, 2f, 0, 0.6f), 0f);
        assertTrue(DamageTint.vignetteAlpha(true, 19f, 99, 1f) > 0f);
    }

    @Test
    @DisplayName("the vignette band is measured off the shorter axis")
    void vignetteBand() {
        assertEquals(DamageTint.bandPixels(1920, 1080), DamageTint.bandPixels(1080, 1920), 0f);
        assertEquals(1080 * 0.35f, DamageTint.bandPixels(1920, 1080), 1e-4);
        assertTrue(DamageTint.bandPixels(1, 1) >= 1f, "a degenerate window still has a band");
    }

    /** Ultralight's modifier bits: 1 alt, 2 ctrl, 4 meta, 8 shift. */
    @Test
    @DisplayName("a chord is a command, not a character")
    void commandChords() {
        assertFalse(KeyNames.isCommandChord(0), "no modifiers");
        assertFalse(KeyNames.isCommandChord(8), "shift is how capitals are typed");
        assertFalse(KeyNames.isCommandChord(1), "alt alone types on macOS (Option-e)");
        assertFalse(KeyNames.isCommandChord(3), "ctrl+alt is AltGr and types");
        assertFalse(KeyNames.isCommandChord(11), "shift+AltGr types too");
        assertTrue(KeyNames.isCommandChord(4), "Cmd-K is a command, not a 'k'");
        assertTrue(KeyNames.isCommandChord(2), "Ctrl-K likewise");
        assertTrue(KeyNames.isCommandChord(12), "Cmd+Shift");
        assertTrue(KeyNames.isCommandChord(6), "Cmd+Ctrl");
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

    // -----------------------------------------------------------------
    // Old animations (§6.7)
    // -----------------------------------------------------------------

    /**
     * The number 1.8 added to the blocking pose, checked against what it is supposed to be
     * rather than against itself: {@code (float) (-Math.PI / 6)}, a −30° yaw.
     */
    @Test
    @DisplayName("the block-pose arm yaw is exactly minus thirty degrees")
    void blockArmYawIsMinusThirtyDegrees() {
        assertEquals((float) (-Math.PI / 6), OldAnimations.VANILLA_BLOCK_ARM_YAW, 0f);
        assertEquals(0f, OldAnimations.ONE_SEVEN_BLOCK_ARM_YAW, 0f,
                "1.7 left rightArm.posY where setAngles had already zeroed it");
    }

    @Test
    @DisplayName("block_hit is one float in each render path, and vanilla is the identity")
    void blockHitSubstitutesOneValuePerPath() {
        // First person. 1.8.9's BLOCK arm hard-codes the swing progress to zero; the revert is
        // the live value and nothing else.
        assertEquals(0f, OldAnimations.blockSwingProgress(false, true, 0.75f), 0f,
                "mod off: vanilla's own fconst_0");
        assertEquals(0f, OldAnimations.blockSwingProgress(true, false, 0.75f), 0f,
                "block_hit: vanilla is what a player picks to compare");
        assertEquals(0.75f, OldAnimations.blockSwingProgress(true, true, 0.75f), 0f);
        assertEquals(0f, OldAnimations.blockSwingProgress(true, true, Float.NaN), 0f,
                "a NaN swing would poison the matrix, not tilt it");

        // Third person, the same switch. `posY` is a rotation in legacy yarn — `pivotY` is the
        // translation — so this is a yaw, and reverting it is writing 1.7's zero.
        assertEquals(OldAnimations.VANILLA_BLOCK_ARM_YAW,
                OldAnimations.blockArmYaw(false, true), 0f, "mod off: the identity substitution");
        assertEquals(OldAnimations.VANILLA_BLOCK_ARM_YAW,
                OldAnimations.blockArmYaw(true, false), 0f);
        assertEquals(OldAnimations.ONE_SEVEN_BLOCK_ARM_YAW,
                OldAnimations.blockArmYaw(true, true), 0f);
    }

    /**
     * {@code LivingEntity.swingHand()}'s own re-entry guard, which is the whole of what
     * {@code swing_during_delay} reproduces locally.
     *
     * <p>The multiplier is 6 with no status effect, {@code 6 - (1 + amplifier)} under Haste and
     * {@code 6 + (1 + amplifier) * 2} under Mining Fatigue, and the comparison is against
     * {@code multiplier / 2} in <em>integer</em> division. Both halves are checked here because
     * getting the truncation wrong is the difference between an arm that keeps up with a fast
     * click and one that stutters.</p>
     */
    @Test
    @DisplayName("a swing restarts on the same guard vanilla uses")
    void swingRestartGuard() {
        assertTrue(OldAnimations.restartsSwing(false, 0, 6), "not swinging: always restarts");
        assertFalse(OldAnimations.restartsSwing(true, 1, 6), "one tick in: absorbed");
        assertFalse(OldAnimations.restartsSwing(true, 2, 6), "still short of 6 / 2");
        assertTrue(OldAnimations.restartsSwing(true, 3, 6), "at the half-way point: restarts");
        assertTrue(OldAnimations.restartsSwing(true, -1, 6),
                "the tick vanilla itself sets on a fresh swing");

        // Haste II is 6 - (1 + 1) = 4, so the half-way point moves down to 2.
        assertTrue(OldAnimations.restartsSwing(true, 2, 4));
        assertFalse(OldAnimations.restartsSwing(true, 1, 4));

        // Mining Fatigue I is 6 + (1 + 0) * 2 = 8, so it moves up to 4.
        assertFalse(OldAnimations.restartsSwing(true, 3, 8));
        assertTrue(OldAnimations.restartsSwing(true, 4, 8));

        // An odd multiplier truncates, exactly as vanilla's `idiv` does: 5 / 2 is 2, so a
        // multiplier of 5 has the same half-way point as one of 4 and not one of 6.
        assertEquals(OldAnimations.restartsSwing(true, 2, 4),
                OldAnimations.restartsSwing(true, 2, 5));
        assertEquals(OldAnimations.restartsSwing(true, 1, 4),
                OldAnimations.restartsSwing(true, 1, 5));
        assertTrue(OldAnimations.restartsSwing(true, 2, 5));
        assertFalse(OldAnimations.restartsSwing(true, 2, 6), "and 6 / 2 is a tick further out");
    }

    // -----------------------------------------------------------------
    // Old input (§6.7)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("the two interlocks only ever answer a guard that was already true")
    void oldInputInterlocksOnlyRelax() {
        // use_while_digging: `doUse` returns early while `isBreakingBlock()`.
        assertFalse(OldInput.isBreakingBlock(false, true, true),
                "not breaking is not breaking, whatever the setting says");
        assertTrue(OldInput.isBreakingBlock(true, false, true), "mod off: vanilla's answer");
        assertTrue(OldInput.isBreakingBlock(true, true, false), "setting off: vanilla's answer");
        assertFalse(OldInput.isBreakingBlock(true, true, true), "and this is the feature");

        // dig_while_using: the mirror, in handleBlockBreaking. Scoped there by method name — the
        // other two isUsingItem() calls in MinecraftClient.tick are byte-identical to 1.7's.
        assertFalse(OldInput.isUsingItemForBreaking(false, true, true));
        assertTrue(OldInput.isUsingItemForBreaking(true, false, true));
        assertTrue(OldInput.isUsingItemForBreaking(true, true, false));
        assertFalse(OldInput.isUsingItemForBreaking(true, true, true));
    }

    /**
     * The scoping the obvious mixin gets wrong.
     *
     * <p>1.8.9 sends both the MISS arm of {@code doAttack}'s {@code tableswitch} and a BLOCK hit
     * that resolved to {@code Material.AIR} to the same {@code hasLimitedAttackSpeed()} /
     * {@code attackCooldown = 10} tail, and <b>1.7.10 armed that cooldown on the air-block path
     * too</b>. So the injection point alone cannot say which case it is in, and only MISS may be
     * relaxed; anything wider goes past 1.7 rather than back to it.</p>
     */
    @Test
    @DisplayName("no_miss_delay is the MISS arm alone, not the cooldown")
    void noMissDelayIsScopedToTheMissArm() {
        assertTrue(OldInput.hasLimitedAttackSpeed(true, false, true, true), "mod off");
        assertTrue(OldInput.hasLimitedAttackSpeed(true, true, false, true), "setting off");
        assertFalse(OldInput.hasLimitedAttackSpeed(true, true, true, true), "a whiff, and this");

        assertTrue(OldInput.hasLimitedAttackSpeed(true, true, true, false),
                "a BLOCK hit that resolved to air still costs the cooldown — 1.7 charged it too");

        assertFalse(OldInput.hasLimitedAttackSpeed(false, true, true, true),
                "creative has no limited attack speed and never had a cooldown to remove");
        assertFalse(OldInput.hasLimitedAttackSpeed(false, false, false, false));
    }
}
