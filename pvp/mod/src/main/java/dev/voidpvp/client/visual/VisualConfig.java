package dev.voidpvp.client.visual;

import dev.voidpvp.client.state.LiveState;
import dev.voidpvp.client.state.Loadout;

/**
 * The mirrored settings of the Wave 7 visual mods — block outline, shiny pots, clear glass,
 * nametags, scoreboard, GUI scale, fog, menu blur and time &amp; weather — read every frame by
 * their Mixins. Same contract as {@code perf/PerfConfig}: written under {@code LiveState}'s
 * monitor by {@link #apply}, read lock-free, every field {@code volatile}, defaults equal to the
 * registry's factory defaults, enums resolved to ints here rather than compared as strings per
 * frame.
 */
public final class VisualConfig {

    private VisualConfig() {
    }

    // -- block_outline ----------------------------------------------------------------------
    public static volatile boolean outlineOn;
    /** {@code outline_style} is {@code outline} or {@code both}. */
    public static volatile boolean outlineLines = true;
    /** {@code outline_style} is {@code fill} or {@code both}. */
    public static volatile boolean outlineFill;
    public static volatile int outlineColor = 0xCCEDEEEF;
    public static volatile float outlineWidth = 2.5f;
    public static volatile int outlineFillColor = 0x26EDEEEF;

    // -- shiny_pots -------------------------------------------------------------------------
    public static volatile boolean shinyPotsOn;
    public static volatile boolean shinyAllPotions = true;
    public static volatile int shinyPasses = 2;
    public static volatile boolean shinyTint;

    // -- clear_glass ------------------------------------------------------------------------
    public static volatile boolean clearGlassOn;
    public static volatile boolean clearPanes = true;
    public static volatile boolean clearStained;
    /**
     * Bumped whenever what clear glass draws changes, so the render thread can rebuild the loaded
     * chunks once ({@code GlassReload}). Glass is baked into chunk meshes; without a rebuild the
     * switch would only apply to chunks built afterwards.
     */
    public static volatile int clearGlassEpoch;

    // -- nametags ---------------------------------------------------------------------------
    public static volatile boolean nametagsOn;
    public static volatile boolean nametagShowOwn = true;
    public static volatile boolean nametagHideOthers;
    public static volatile float nametagBackground = 0.25f;
    public static volatile float nametagScale = 1f;
    public static volatile boolean nametagShadow = true;

    // -- scoreboard -------------------------------------------------------------------------
    public static volatile boolean scoreboardOn;
    public static volatile boolean scoreboardHidden;

    // -- gui_scale --------------------------------------------------------------------------
    public static volatile boolean guiScaleOn;
    public static volatile int hotbarScale = 2;
    public static volatile int inventoryScale = 3;

    // -- fog --------------------------------------------------------------------------------
    public static final int FOG_VANILLA = 0;
    public static final int FOG_REDUCED = 1;
    public static final int FOG_OFF = 2;
    public static volatile boolean fogOn;
    public static volatile int distanceFog = FOG_REDUCED;
    public static volatile int waterFog = FOG_REDUCED;
    public static volatile int lavaFog = FOG_VANILLA;

    // -- menu_blur --------------------------------------------------------------------------
    public static volatile boolean menuBlurOn;
    public static volatile int blurRadius = 4;
    public static volatile boolean blurFade = true;
    public static volatile float menuDim = 0.5f;

    // -- time_weather -----------------------------------------------------------------------
    public static volatile boolean timeWeatherOn;
    public static volatile boolean lockTime = true;
    public static volatile long timeTicks = 6000;
    public static volatile boolean clearWeather = true;

    /** Writes every field from the loadout. Called from {@code LiveState.applyActuatorFields}. */
    public static void apply(Loadout l) {
        outlineOn = l.isOn("block_outline");
        String style = l.stringSetting("block_outline", "outline_style", "outline");
        outlineLines = "outline".equals(style) || "both".equals(style);
        outlineFill = "fill".equals(style) || "both".equals(style);
        outlineColor = LiveState.parseColor(
                l.stringSetting("block_outline", "outline_color", "#EDEEEFCC"), 0xCCEDEEEF);
        outlineWidth = (float) l.numberSetting("block_outline", "outline_width", 2.5);
        outlineFillColor = LiveState.parseColor(
                l.stringSetting("block_outline", "fill_color", "#EDEEEF26"), 0x26EDEEEF);

        shinyPotsOn = l.isOn("shiny_pots");
        shinyAllPotions = l.boolSetting("shiny_pots", "all_potions", true);
        shinyPasses = (int) l.numberSetting("shiny_pots", "glint_passes", 2);
        shinyTint = l.boolSetting("shiny_pots", "tint_glint", false);

        boolean glassOn = l.isOn("clear_glass");
        boolean panes = l.boolSetting("clear_glass", "clear_panes", true);
        boolean stained = l.boolSetting("clear_glass", "clear_stained", false);
        if (glassOn != clearGlassOn || (glassOn && (panes != clearPanes || stained != clearStained))) {
            clearGlassEpoch++;
        }
        clearPanes = panes;
        clearStained = stained;
        clearGlassOn = glassOn;

        nametagsOn = l.isOn("nametags");
        nametagShowOwn = l.boolSetting("nametags", "show_own", true);
        nametagHideOthers = l.boolSetting("nametags", "hide_others", false);
        nametagBackground = (float) l.numberSetting("nametags", "tag_background", 0.25);
        nametagScale = (float) l.numberSetting("nametags", "tag_scale", 1);
        nametagShadow = l.boolSetting("nametags", "tag_shadow", true);

        scoreboardOn = l.isOn("scoreboard");
        scoreboardHidden = l.boolSetting("scoreboard", "hidden", false);

        guiScaleOn = l.isOn("gui_scale");
        hotbarScale = (int) l.numberSetting("gui_scale", "hotbar_scale", 2);
        inventoryScale = (int) l.numberSetting("gui_scale", "inventory_scale", 3);

        fogOn = l.isOn("fog");
        distanceFog = fogLevel(l.stringSetting("fog", "distance_fog", "reduced"));
        waterFog = fogLevel(l.stringSetting("fog", "water_fog", "reduced"));
        lavaFog = fogLevel(l.stringSetting("fog", "lava_fog", "vanilla"));

        menuBlurOn = l.isOn("menu_blur");
        blurRadius = (int) l.numberSetting("menu_blur", "blur_radius", 4);
        blurFade = l.boolSetting("menu_blur", "blur_fade", true);
        menuDim = (float) l.numberSetting("menu_blur", "menu_dim", 0.5);

        timeWeatherOn = l.isOn("time_weather");
        lockTime = l.boolSetting("time_weather", "lock_time", true);
        timeTicks = (long) l.numberSetting("time_weather", "time_ticks", 6000);
        clearWeather = l.boolSetting("time_weather", "clear_weather", true);
    }

    /**
     * {@code vanilla} / {@code reduced} / {@code off}. A stored value outside the enum reads as
     * vanilla — the answer that changes nothing — never as "off".
     */
    static int fogLevel(String v) {
        if ("off".equals(v)) {
            return FOG_OFF;
        }
        return "reduced".equals(v) ? FOG_REDUCED : FOG_VANILLA;
    }
}
