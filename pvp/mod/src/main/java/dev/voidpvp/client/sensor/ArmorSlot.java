package dev.voidpvp.client.sensor;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/** One entry of the {@code tick} payload's {@code armor} array. */
public final class ArmorSlot {

    public final String slot;
    public final String item;
    public final int damage;
    public final int maxDamage;
    public final int count;
    public final boolean enchanted;

    public ArmorSlot(String slot, String item, int damage, int maxDamage, int count,
                     boolean enchanted) {
        this.slot = slot;
        this.item = item;
        this.damage = damage;
        this.maxDamage = maxDamage;
        this.count = count;
        this.enchanted = enchanted;
    }

    /** An empty slot: {@code item} is null and nothing else is reported. */
    public static ArmorSlot empty(String slot) {
        return new ArmorSlot(slot, null, 0, 0, 0, false);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("slot", slot);
        if (item == null) {
            o.add("item", JsonNull.INSTANCE);
            return o;
        }
        o.addProperty("item", item);
        o.addProperty("damage", Integer.valueOf(damage));
        o.addProperty("max_damage", Integer.valueOf(maxDamage));
        o.addProperty("count", Integer.valueOf(count));
        o.addProperty("enchanted", Boolean.valueOf(enchanted));
        return o;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArmorSlot)) {
            return false;
        }
        ArmorSlot a = (ArmorSlot) o;
        return slot.equals(a.slot) && (item == null ? a.item == null : item.equals(a.item))
                && damage == a.damage && maxDamage == a.maxDamage && count == a.count
                && enchanted == a.enchanted;
    }

    @Override
    public int hashCode() {
        return slot.hashCode() * 31 + (item == null ? 0 : item.hashCode()) + damage;
    }
}
