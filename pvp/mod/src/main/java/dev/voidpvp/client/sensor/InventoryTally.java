package dev.voidpvp.client.sensor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What is in the player's inventory, merged into one entry per distinct thing — the
 * {@code inventory} field of {@code bridge.json}'s tick payload (§6.6).
 *
 * <p>Plain and free of Minecraft types, like {@link HitTally} and {@link ReachTally}: the caller
 * reads stacks off {@code PlayerInventory.main} and hands over facts, and everything that
 * <em>decides</em> — what counts as the same thing, what order the entries come out in, whether
 * anything changed since last tick — lives here where {@code SensorsTest} can walk it.</p>
 *
 * <h2>What "the same thing" means, and why potions are not an exception to it</h2>
 *
 * <p>A count is only useful if it merges the stacks a player thinks of as one thing and splits
 * the ones they do not. For almost every item the registry name is that identity: three stacks of
 * ender pearls in three slots are sixteen pearls, and nobody counts them by slot.</p>
 *
 * <p><b>Potions break it, and pot PvP is the mode that cares.</b> Every potion in 1.8.9 is
 * {@code minecraft:potion} — a water bottle, a splash of healing II and a lingering weakness are
 * one registry name and three completely different things, and a mod that counted them together
 * would tell a player they had eight heals when three were water. So a potion's identity is the
 * registry name <em>plus</em> the effect it grants and whether it is throwable, which is what a
 * player means when they say "splash heals".</p>
 *
 * <p>The effect is carried as the <b>numeric potion id</b> rather than a name, because that is
 * already how an effect crosses this bridge: {@code potion_effect.id} on the {@code fx} array is
 * "the numeric potion id as used by 1.8.9", and the page already has a table from it to a label.
 * Inventing a second spelling here would be inventing a second table to keep in step with it.</p>
 *
 * <h2>Why the identity is not simply the item's metadata</h2>
 *
 * <p>{@code ItemStack.getData()} distinguishes potions and it also distinguishes a sword with
 * three durability left from the same sword undamaged. Keying on it would fragment every tool and
 * every piece of armour into as many entries as there are wear states — an inventory of nine
 * distinct things reported as thirty. Damage is wear for most items and identity for a few, and
 * only the caller knows which, which is why this takes a resolved effect rather than a raw
 * number.</p>
 */
public final class InventoryTally {

    /** No potion effect — the entry is an ordinary item, or a water bottle. */
    public static final int NO_EFFECT = 0;

    /** One merged line: a thing, and how many of it. */
    public static final class Entry {
        /** Registry name, e.g. {@code minecraft:ender_pearl}. */
        public final String item;
        /** Numeric potion id ({@code potion_effect.id}), or {@link #NO_EFFECT}. */
        public final int effect;
        /** Whether the potion is throwable. Meaningless unless {@link #effect} is set. */
        public final boolean splash;
        /** Total across every slot holding this thing. */
        public int count;

        Entry(String item, int effect, boolean splash, int count) {
            this.item = item;
            this.effect = effect;
            this.splash = splash;
            this.count = count;
        }

        boolean sameThingAs(String otherItem, int otherEffect, boolean otherSplash) {
            return item.equals(otherItem) && effect == otherEffect
                    && (effect == NO_EFFECT || splash == otherSplash);
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("item", item);
            o.addProperty("count", Integer.valueOf(count));
            // Omitted rather than sent as 0 on an ordinary item: `bridge.json` says an absent
            // field means "nothing to report", and every stack of cobblestone carrying
            // `"effect": 0, "splash": false` would be two thirds of this payload saying so.
            if (effect != NO_EFFECT) {
                o.addProperty("effect", Integer.valueOf(effect));
                o.addProperty("splash", Boolean.valueOf(splash));
            }
            return o;
        }
    }

    /**
     * Most distinct things one 1.8.9 inventory can hold: 36 main slots.
     *
     * <p>A cap rather than a trusted invariant, because the caller is a loop over an array a mod
     * or a server could have grown. The payload is bounded by the same number in the schema, so a
     * larger inventory truncates rather than making the wire message the thing that fails.</p>
     */
    private static final int MAX_ENTRIES = 36;

    private final List<Entry> entries = new ArrayList<Entry>();

    /**
     * Add one stack.
     *
     * @param item   registry name; ignored when null or empty
     * @param effect numeric potion id, or {@link #NO_EFFECT} for anything that is not a potion
     *               with an effect
     * @param splash whether the potion is throwable; ignored unless {@code effect} is set
     * @param count  stack size; ignored when not positive
     */
    public void add(String item, int effect, boolean splash, int count) {
        if (item == null || item.isEmpty() || count <= 0) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.sameThingAs(item, effect, splash)) {
                entry.count += count;
                return;
            }
        }
        if (entries.size() >= MAX_ENTRIES) {
            return;
        }
        entries.add(new Entry(item, effect, splash, count));
    }

    /** The merged lines, in the order the slots were walked. */
    public List<Entry> entries() {
        return entries;
    }

    /**
     * Whether this reads the same as {@code other}, for {@link TickCoalescer}'s value check.
     *
     * <p>Order-sensitive on purpose. Two tallies with the same totals in a different order are the
     * player having moved a stack between slots, which is a real change to what the inventory
     * screen looks like and is a change this field is allowed to report. Making it
     * order-insensitive would cost a sort on every tick to hide an update that costs one small
     * object — the wrong trade, and the comparison would stop being obviously correct.</p>
     */
    public boolean sameAs(InventoryTally other) {
        if (other == null || other.entries.size() != entries.size()) {
            return false;
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry a = entries.get(i);
            Entry b = other.entries.get(i);
            if (a.count != b.count || !a.sameThingAs(b.item, b.effect, b.splash)) {
                return false;
            }
        }
        return true;
    }

    public JsonArray toJson() {
        JsonArray a = new JsonArray();
        for (int i = 0; i < entries.size(); i++) {
            a.add(entries.get(i).toJson());
        }
        return a;
    }
}
