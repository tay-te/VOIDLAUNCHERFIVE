package dev.voidmc.mettle.burden;

import dev.voidmc.mettle.api.BandRules;
import dev.voidmc.mettle.api.GearService;
import dev.voidmc.mettle.api.GearSlot;
import dev.voidmc.mettle.api.LoadBand;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.core.MettleAttributes;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Adds up what a player carries (BURDEN.md §2): the 36 main slots, the four armour slots and the offhand, the three
 * worn gear items with their pockets, and the stack on the cursor. The ender chest is elsewhere and does not count.
 * Creative and spectator players are exempt: their load is still added up, but their band is always Light.
 */
public final class Loads {
	private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

	private Loads() {
	}

	public static float capacity(Player player) {
		return (float) player.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
	}

	public static boolean exempt(Player player) {
		return player.isCreative() || player.isSpectator();
	}

	public static LoadState compute(Player player) {
		WeightTable table = Weights.table(player);
		Tally tally = new Tally();
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			tally.add(s.getItem(), Weights.weigh(table, player, s, false, 0), s.isEmpty());
		}
		for (EquipmentSlot slot : WORN) {
			ItemStack s = player.getItemBySlot(slot);
			tally.add(s.getItem(), Weights.weigh(table, player, s, false, 0), s.isEmpty());
		}
		if (player.containerMenu != null) {
			ItemStack carried = player.containerMenu.getCarried();
			tally.add(carried.getItem(), Weights.weigh(table, player, carried, false, 0), carried.isEmpty());
		}
		GearService gear = MettleServices.gear();
		for (GearSlot slot : GearSlot.values()) {
			ItemStack worn = gear.worn(player, slot);
			if (worn.isEmpty()) {
				continue;
			}
			// The worn item without its pockets, then the pockets as the gear module reports them.
			float kg = Weights.weigh(table, player, worn, true, 0);
			for (ItemStack pocket : gear.pockets(player, slot)) {
				kg += Weights.weigh(table, player, pocket, false, 1);
			}
			tally.add(worn.getItem(), kg, false);
		}

		float capacity = capacity(player);
		boolean exempt = exempt(player);
		BandRules rules = Bands.rules(player);
		LoadBand band = exempt ? LoadBand.LIGHT : Bands.band(tally.load, capacity, rules);
		return new LoadState(round(tally.load), round(capacity), band, exempt,
			tally.heaviest == null ? LoadState.NONE : BuiltInRegistries.ITEM.getKey(tally.heaviest), round(tally.heaviestKg), tally.heaviestStacks);
	}

	private static float round(float kg) {
		return Math.round(kg * 1000f) / 1000f;
	}

	/** Load, plus weight and stack count per kind of item to find the heaviest. */
	private static final class Tally {
		float load;
		final Map<Item, float[]> byItem = new IdentityHashMap<>();
		Item heaviest;
		float heaviestKg;
		int heaviestStacks;

		void add(Item item, float kg, boolean empty) {
			if (empty || kg <= 0f) {
				return;
			}
			this.load += kg;
			float[] t = this.byItem.computeIfAbsent(item, i -> new float[2]);
			t[0] += kg;
			t[1] += 1;
			if (t[0] > this.heaviestKg) {
				this.heaviest = item;
				this.heaviestKg = t[0];
				this.heaviestStacks = (int) t[1];
			} else if (item == this.heaviest) {
				this.heaviestStacks = (int) t[1];
			}
		}
	}
}
