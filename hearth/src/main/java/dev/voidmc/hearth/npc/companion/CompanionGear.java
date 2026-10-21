package dev.voidmc.hearth.npc.companion;

import dev.voidmc.hearth.api.Resident;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;

/**
 * What a companion wears and carries (docs/LIVING_WORLD_2.md §8.1, §8.3): the armour and weapon the player gave them,
 * kept in {@code extra("companion").gear} so it outlives their folk being folded, drawn by the folk renderer's gear
 * layer, and handed back on "give that back" or when they go home (dropped where they fall if they die). Until the
 * Companion gear screen is designed (F3) gear changes only through "take this" / "give that back".
 */
public final class CompanionGear {
	/** The slots a companion can be given something for. */
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
		EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

	private CompanionGear() {
	}

	/** Their gear by slot. */
	public static Map<EquipmentSlot, ItemStack> read(CompanionState st, HolderLookup.Provider registries) {
		Map<EquipmentSlot, ItemStack> out = new EnumMap<>(EquipmentSlot.class);
		var ops = registries.createSerializationContext(NbtOps.INSTANCE);
		ListTag list = st.gear();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			EquipmentSlot slot = slot(t.getStringOr("slot", ""));
			Tag item = t.get("item");
			if (slot == null || item == null) {
				continue;
			}
			ItemStack.CODEC.parse(ops, item).result().ifPresent(stack -> out.put(slot, stack));
		}
		return out;
	}

	public static void write(CompanionState st, Map<EquipmentSlot, ItemStack> gear, HolderLookup.Provider registries) {
		var ops = registries.createSerializationContext(NbtOps.INSTANCE);
		ListTag list = new ListTag();
		for (Map.Entry<EquipmentSlot, ItemStack> e : gear.entrySet()) {
			if (e.getValue().isEmpty()) {
				continue;
			}
			Tag item = ItemStack.CODEC.encodeStart(ops, e.getValue()).result().orElse(null);
			if (item != null) {
				CompoundTag t = new CompoundTag();
				t.putString("slot", e.getKey().getSerializedName());
				t.put("item", item);
				list.add(t);
			}
		}
		st.setGear(list);
	}

	private static EquipmentSlot slot(String name) {
		for (EquipmentSlot s : SLOTS) {
			if (s.getSerializedName().equals(name)) {
				return s;
			}
		}
		return null;
	}

	/**
	 * The slot a stack goes in when the player hands it over: armour by where it is worn, a shield in the off hand,
	 * anything else in the main hand; null for what a companion does not carry (a block, food: their pack has its own).
	 */
	public static EquipmentSlot slotFor(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		var equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null) {
			EquipmentSlot s = equippable.slot();
			return s == EquipmentSlot.BODY || s == EquipmentSlot.SADDLE ? null : s;
		}
		if (stack.is(net.minecraft.world.item.Items.SHIELD)) {
			return EquipmentSlot.OFFHAND;
		}
		if (stack.has(DataComponents.WEAPON) || stack.has(DataComponents.TOOL) || stack.getItem() instanceof net.minecraft.world.item.BowItem
			|| stack.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
			return EquipmentSlot.MAINHAND;
		}
		return null;
	}

	/** Puts their gear on their folk (each time they are embodied); slots with nothing given are left as the routine has them. */
	public static void dress(Mob folk, CompanionState st, HolderLookup.Provider registries) {
		for (Map.Entry<EquipmentSlot, ItemStack> e : decoded(st, registries).entrySet()) {
			if (!ItemStack.matches(folk.getItemBySlot(e.getKey()), e.getValue())) {
				folk.setItemSlot(e.getKey(), e.getValue().copy());
				folk.setDropChance(e.getKey(), 0.0F);
			}
		}
	}

	/** Gear lists decoded lately, by their saved form: a companion is dressed again every few seconds, decoding is the dear part. */
	private static final Map<ListTag, Map<EquipmentSlot, ItemStack>> DECODED = new java.util.LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<ListTag, Map<EquipmentSlot, ItemStack>> eldest) {
			return size() > 128;
		}
	};

	/** {@link #read}, kept while the saved gear is the same (the stacks are never handed out: dressing copies them). */
	private static synchronized Map<EquipmentSlot, ItemStack> decoded(CompanionState st, HolderLookup.Provider registries) {
		ListTag list = st.gear();
		if (list.isEmpty()) {
			return Map.of();
		}
		Map<EquipmentSlot, ItemStack> out = DECODED.get(list);
		if (out == null) {
			out = read(st, registries);
			DECODED.put(list.copy(), out);
		}
		return out;
	}

	/** Takes their gear off the folk (they are going home): what the routine gave them back to bare hands. */
	public static void undress(Mob folk, CompanionState st, HolderLookup.Provider registries) {
		for (EquipmentSlot slot : read(st, registries).keySet()) {
			folk.setItemSlot(slot, ItemStack.EMPTY);
		}
	}

	/** Everything they carry of the player's, emptied out of their record. */
	public static List<ItemStack> takeAll(CompanionState st, HolderLookup.Provider registries) {
		List<ItemStack> out = new ArrayList<>(read(st, registries).values());
		st.setGear(new ListTag());
		return out;
	}

	/** Drops what they carried where they fell (a companion's death: the player's gear is not lost with them). */
	public static void dropAt(ServerLevel level, net.minecraft.world.phys.Vec3 at, List<ItemStack> stacks) {
		for (ItemStack s : stacks) {
			if (!s.isEmpty()) {
				net.minecraft.world.entity.item.ItemEntity e = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y + 0.5, at.z, s);
				e.setDefaultPickUpDelay();
				level.addFreshEntity(e);
			}
		}
	}

	/** Hands a stack to the player: into their pack, or at their feet when it is full. */
	public static void give(net.minecraft.server.level.ServerPlayer player, ItemStack stack) {
		if (!stack.isEmpty() && !player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	/** Whether a resident carries any of a player's gear. */
	public static boolean carries(Resident r) {
		CompanionState st = CompanionState.of(r);
		return st != null && !st.gear().isEmpty();
	}
}
