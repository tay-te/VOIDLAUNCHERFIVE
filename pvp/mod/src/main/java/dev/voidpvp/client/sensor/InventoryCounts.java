package dev.voidpvp.client.sensor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code counts} reading on the tick payload: inventory-wide totals for the Item counter's
 * named {@code track} values and the Potion counter ({@code bridge.json},
 * {@code tick_payload.counts}).
 *
 * <p>Plain, like {@link HitTally}: {@code VoidClient} walks {@code PlayerInventory.main} and hands
 * each stack over as three facts — the registry path, the metadata and the count — so every rule
 * about <em>what counts as what</em> is here, where {@code SensorsTest} can hold it. The ids are
 * 1.8.9's registry paths without the {@code minecraft:} namespace (the same strings
 * {@code VoidClient.itemId} already produces for the armor sensor), checked against the named jar:
 * the cobweb's item is registered under its block's id {@code web}, and soup is
 * {@code mushroom_stew}.</p>
 *
 * <p>Use: {@link #begin}, then {@link #add} once per main-inventory slot, then {@link #result}.
 * Reused across ticks so the walk allocates nothing but the result map, which the coalescer
 * keeps a copy of to compare against.</p>
 */
public final class InventoryCounts {

    /** The always-present keys, in {@code bridge.json}'s order. {@code held_total} is separate. */
    public static final String[] NAMED = {
        "ender_pearl", "golden_apple", "arrow", "snowball", "egg", "cobweb", "wool", "tnt",
        "fire_charge", "iron_ingot", "gold_ingot", "diamond", "emerald", "health_potion", "soup"
    };

    /** The schema's per-key ceiling: 36 slots of 64. */
    public static final int MAX = 36 * 64;

    /** {@code PotionItem}'s metadata layout: the low nibble is the effect, 16384 is splash. */
    private static final int POTION_EFFECT_MASK = 15;
    private static final int POTION_HEALING = 5;
    private static final int POTION_SPLASH_BIT = 16384;

    private final int[] totals = new int[NAMED.length];
    private String heldId;
    private int heldMeta;
    private boolean heldHasSubtypes;
    private int heldTotal;

    /**
     * Starts one count.
     *
     * @param heldId          the held stack's registry path, or null for an empty hand — in
     *                        which case {@code held_total} is absent, not 0
     * @param heldMeta        its metadata ({@code ItemStack.getData()})
     * @param heldHasSubtypes {@code ItemStack.hasSubTypes()}: whether the metadata names a
     *                        variant (wool colour, potion type, golden vs enchanted apple)
     *                        rather than wear. A damaged sword is still the same sword; a red
     *                        wool block is not white wool.
     */
    public void begin(String heldId, int heldMeta, boolean heldHasSubtypes) {
        for (int i = 0; i < totals.length; i++) {
            totals[i] = 0;
        }
        this.heldId = heldId;
        this.heldMeta = heldMeta;
        this.heldHasSubtypes = heldHasSubtypes;
        this.heldTotal = 0;
    }

    /** One main-inventory slot. Null ids and empty stacks are ignored. */
    public void add(String id, int meta, int count) {
        if (id == null || count <= 0) {
            return;
        }
        if (heldId != null && heldId.equals(id) && (!heldHasSubtypes || meta == heldMeta)) {
            heldTotal += count;
        }
        int key = keyOf(id, meta);
        if (key >= 0) {
            totals[key] += count;
        }
    }

    /**
     * The {@code counts} object for this walk: {@code held_total} first when the hand holds
     * something, then every named key, zeros included — the widget shows "0 pearls", which is
     * different from showing nothing.
     */
    public Map<String, Integer> result() {
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        if (heldId != null) {
            out.put("held_total", Integer.valueOf(Math.min(MAX, heldTotal)));
        }
        for (int i = 0; i < NAMED.length; i++) {
            out.put(NAMED[i], Integer.valueOf(Math.min(MAX, totals[i])));
        }
        return out;
    }

    /** Index into {@link #NAMED}, or -1 for an item no named key tracks. */
    static int keyOf(String id, int meta) {
        switch (id) {
            case "ender_pearl": return 0;
            case "golden_apple": return 1; // both metas: golden and enchanted golden
            case "arrow": return 2;
            case "snowball": return 3;
            case "egg": return 4;
            case "web": return 5;
            case "wool": return 6; // every colour
            case "tnt": return 7;
            case "fire_charge": return 8;
            case "iron_ingot": return 9;
            case "gold_ingot": return 10;
            case "diamond": return 11;
            case "emerald": return 12;
            case "potion": return isSplashHealing(meta) ? 13 : -1;
            case "mushroom_stew": return 14;
            default: return -1;
        }
    }

    /**
     * Splash Healing I or II: effect nibble 5 (instant health) with the splash bit set — 16421
     * and 16453 in vanilla's creative tab. Drinkable healing is not a pot in a fight: you cannot
     * throw it, so the Potion counter would read a number you cannot spend.
     */
    static boolean isSplashHealing(int meta) {
        return (meta & POTION_EFFECT_MASK) == POTION_HEALING && (meta & POTION_SPLASH_BIT) != 0;
    }
}
