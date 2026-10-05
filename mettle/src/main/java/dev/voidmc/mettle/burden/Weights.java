package dev.voidmc.mettle.burden;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.api.WeightModifier;
import dev.voidmc.mettle.burden.WeightDeriver.How;
import dev.voidmc.mettle.burden.WeightDeriver.Part;
import dev.voidmc.mettle.burden.WeightDeriver.Rule;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ContainerComponent;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;

/**
 * The weight tables (one per side) and weighing a stack with everything inside it (BURDEN.md §1). Contents count
 * recursively: bundles, containers (shulker boxes), loaded crossbows, gear pockets ({@code mettle:pockets}, read by id so
 * nothing here compiles against the gear module) and Hearth coin pouches ({@code hearth:pouch_contents}, read
 * reflectively so Hearth stays optional).
 */
public final class Weights {
	public static final Identifier POCKETS = Mettle.id("pockets");
	public static final Identifier POUCH = Identifier.fromNamespaceAndPath("hearth", "pouch_contents");
	private static final String[] COINS = {"hearth:copper_bit", "hearth:iron_mark", "hearth:gold_crown"};
	private static final String[] POUCH_FIELDS = {"bits", "marks", "crowns"};
	/** Nesting deeper than this is not counted (a shulker in a bundle in a backpack is three). */
	private static final int MAX_DEPTH = 8;

	private static volatile WeightTable server = WeightTable.EMPTY;
	private static volatile WeightTable client = WeightTable.EMPTY;
	private static volatile @Nullable MinecraftServer running;
	private static final Map<Class<?>, Method[]> POUCH_READERS = new java.util.concurrent.ConcurrentHashMap<>();

	private Weights() {
	}

	// ---------------------------------------------------------------- tables

	public static WeightTable server() {
		return server;
	}

	public static WeightTable client() {
		return client;
	}

	public static void setClient(WeightTable table) {
		client = table;
	}

	static void setRunning(@Nullable MinecraftServer s) {
		running = s;
		if (s == null) {
			server = WeightTable.EMPTY;
		}
	}

	/** The table for the side this player lives on. */
	public static WeightTable table(Player player) {
		return player.level().isClientSide() ? client : server;
	}

	/** The table for the calling thread: the server's on the server thread, otherwise the client's if it has one. */
	public static WeightTable table() {
		MinecraftServer s = running;
		if (s != null && s.isSameThread()) {
			return server;
		}
		return client.size() > 0 ? client : server;
	}

	/** Recomputes the server table from the current recipes, tags and overrides. Server thread. */
	static WeightTable compute(MinecraftServer s, WeightOverrides overrides) {
		long start = System.nanoTime();
		Function<TagKey<Item>, Collection<Item>> tags = tag -> StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(tag).spliterator(), false)
			.map(Holder::value).toList();
		server = derive(tags, overrides, RecipeEdges.from(s.getRecipeManager()));
		Mettle.LOGGER.info("Weighed {} items in {} ms", server.size(), (System.nanoTime() - start) / 1_000_000);
		return server;
	}

	/** Leaves, then overrides on top, then recipes for the rest, over every registered item. */
	public static WeightTable derive(Function<TagKey<Item>, Collection<Item>> tags, WeightOverrides overrides, List<WeightDeriver.Edge> edges) {
		Map<Item, Rule> rules = new LinkedHashMap<>(Leaves.rules(tags));
		rules.putAll(overrides.rules(tags));
		List<Item> items = BuiltInRegistries.ITEM.stream().toList();
		WeightDeriver.Result result = WeightDeriver.derive(items, rules, edges, Weights::remainder, Item::getDefaultMaxStackSize, Leaves::technical,
			Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
		return new WeightTable(result.weights(), result.how());
	}

	public static @Nullable Item remainder(Item item) {
		ItemStackTemplate r = item.getCraftingRemainder();
		return r == null ? null : r.item().value();
	}

	// ---------------------------------------------------------------- weighing

	/** A whole stack: own weight × count, plus contents, no player modifiers. */
	public static float weigh(WeightTable table, ItemInstance stack) {
		return weigh(table, null, stack, false, 0);
	}

	/** A whole stack for a player (their {@link WeightModifier}s apply to it and to everything inside). */
	public static float weigh(WeightTable table, @Nullable Player player, ItemInstance stack, boolean skipPockets, int depth) {
		if (stack instanceof ItemStack s && s.isEmpty()) {
			return 0f;
		}
		Item item = stack.typeHolder().value();
		float total = table.get(item) * stack.count() * multiplier(player, stack);
		if (depth < MAX_DEPTH) {
			total += contents(table, player, stack, skipPockets, depth + 1);
		}
		return total;
	}

	/** The product of every registered modifier for this player and stack (1 without a player). */
	public static float multiplier(@Nullable Player player, ItemInstance stack) {
		if (player == null) {
			return 1f;
		}
		float m = 1f;
		for (WeightModifier modifier : MettleServices.weightModifiers()) {
			m *= Math.max(0f, modifier.multiplier(player, stack));
		}
		return m;
	}

	/** What is inside a stack, in kg. */
	public static float contents(WeightTable table, @Nullable Player player, ItemInstance stack, boolean skipPockets, int depth) {
		float sum = 0f;
		DataComponentType<?> pockets = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(POCKETS);
		DataComponentType<?> pouch = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(POUCH);
		if (stack instanceof ItemStack s) {
			for (TypedDataComponent<?> c : s.getComponents()) {
				if (!(skipPockets && c.type() == pockets)) {
					sum += component(table, player, c.type(), c.value(), pouch, depth);
				}
			}
			return sum;
		}
		for (DataComponentType<?> type : new DataComponentType<?>[] {DataComponents.BUNDLE_CONTENTS, DataComponents.CONTAINER, DataComponents.CHARGED_PROJECTILES, pockets, pouch}) {
			if (type != null && !(skipPockets && type == pockets)) {
				Object value = stack.get(type);
				if (value != null) {
					sum += component(table, player, type, value, pouch, depth);
				}
			}
		}
		return sum;
	}

	private static float component(WeightTable table, @Nullable Player player, DataComponentType<?> type, Object value,
		@Nullable DataComponentType<?> pouch, int depth) {
		float sum = 0f;
		if (type == pouch) {
			return pouch(table, player, value);
		}
		switch (value) {
			case BundleContents b -> {
				for (ItemStackTemplate t : b.items()) {
					sum += weigh(table, player, t, false, depth);
				}
			}
			case ItemContainerContents c -> {
				for (ItemStackTemplate t : c.nonEmptyItems()) {
					sum += weigh(table, player, t, false, depth);
				}
			}
			case ChargedProjectiles p -> {
				for (ItemStackTemplate t : p.items()) {
					sum += weigh(table, player, t, false, depth);
				}
			}
			case ContainerComponent<?> other -> {
				for (ItemStack s : other.itemCopies().toList()) {
					sum += weigh(table, player, s, false, depth);
				}
			}
			default -> {
			}
		}
		return sum;
	}

	/** Hearth's pouch holds coin counts, not stacks: counts × each coin's weight (and the player's coin modifiers). */
	private static float pouch(WeightTable table, @Nullable Player player, Object value) {
		Method[] readers = POUCH_READERS.computeIfAbsent(value.getClass(), Weights::pouchReaders);
		float sum = 0f;
		for (int i = 0; i < COINS.length; i++) {
			if (readers[i] == null) {
				continue;
			}
			Item coin = Leaves.item(COINS[i]).orElse(null);
			if (coin == null) {
				continue;
			}
			try {
				int count = ((Number) readers[i].invoke(value)).intValue();
				if (count > 0) {
					sum += table.get(coin) * count * multiplier(player, new ItemStack(coin));
				}
			} catch (ReflectiveOperationException | ClassCastException e) {
				return sum;
			}
		}
		return sum;
	}

	private static Method[] pouchReaders(Class<?> type) {
		Method[] readers = new Method[POUCH_FIELDS.length];
		RecordComponent[] parts = type.isRecord() ? type.getRecordComponents() : new RecordComponent[0];
		for (int i = 0; i < POUCH_FIELDS.length; i++) {
			for (RecordComponent part : parts) {
				if (part.getName().equals(POUCH_FIELDS[i])) {
					readers[i] = part.getAccessor();
				}
			}
		}
		return readers;
	}

	// ---------------------------------------------------------------- explaining

	/** "iron_block = 9 × iron_ingot (leaf 0.5) = 4.5 kg". */
	public static String explain(WeightTable table, Item item) {
		float w = table.get(item);
		How how = table.how(item);
		String name = name(item);
		return switch (how) {
			case null -> name + " = " + num(w) + " kg";
			case How.Fixed f -> name + " = " + f.source() + " " + num(w) + " kg";
			case How.Technical t -> name + " = technical item, " + num(w) + " kg";
			case How.Fallback f -> name + " = fallback " + num(WeightDeriver.FALLBACK_STACK_KG) + " kg"
				+ (f.maxStack() > 1 ? " ÷ stack of " + f.maxStack() : " (unstackable)") + " = " + num(w) + " kg";
			case How.Plus p -> name + " = " + term(table, p.base(), 1) + " + " + num(p.extra()) + " = " + num(w) + " kg";
			case How.Crafted c -> {
				List<String> in = new ArrayList<>();
				for (Part part : c.inputs()) {
					in.add(term(table, part.item(), part.n()));
				}
				String expr = String.join(" + ", in);
				for (Part part : c.remainders()) {
					expr += " − " + term(table, part.item(), part.n());
				}
				if (c.count() > 1) {
					expr = (in.size() > 1 || !c.remainders().isEmpty() ? "(" + expr + ")" : expr) + " ÷ " + c.count();
				}
				if (c.cooking()) {
					expr = "cooked from " + expr;
				} else if (c.recipe() != null && c.recipe().getNamespace().equals(Mettle.ID)) {
					// An equivalence (weathered copper, stripped wood…), not a recipe.
					expr = "same as " + expr;
				}
				yield name + " = " + expr + " = " + num(w) + " kg";
			}
		};
	}

	/** "9 × iron_ingot (leaf 0.5)". */
	private static String term(WeightTable table, Item item, int n) {
		How how = table.how(item);
		float w = table.get(item);
		String note = switch (how) {
			case How.Fixed f -> f.source() + " " + num(w);
			case How.Fallback f -> "fallback " + num(w);
			case null, default -> num(w);
		};
		return (n > 1 ? n + " × " : "") + name(item) + " (" + note + ")";
	}

	public static String name(Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
	}

	/** Up to three decimals, trailing zeros dropped: 4.5, 0.25, 0.063. */
	public static String num(float kg) {
		String s = String.format(Locale.ROOT, "%.3f", kg);
		s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
		return s.equals("-0") ? "0" : s;
	}

	/** One item for players: "0.25", "0.5", "1.5", "<0.01". */
	public static String kg(float kg) {
		if (kg <= 0f) {
			return "0";
		}
		if (kg < 0.01f) {
			return "<0.01";
		}
		if (kg < 1f) {
			String s = String.format(Locale.ROOT, "%.2f", kg);
			return s.endsWith("0") ? s.substring(0, s.length() - 1) : s;
		}
		return String.format(Locale.ROOT, "%.1f", kg);
	}

	/** A total for players, one decimal: "16.0", "0.4". */
	public static String total(float kg) {
		return kg > 0f && kg < 0.1f ? kg(kg) : String.format(Locale.ROOT, "%.1f", kg);
	}
}
