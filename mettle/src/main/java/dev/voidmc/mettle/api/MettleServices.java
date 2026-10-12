package dev.voidmc.mettle.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;

/**
 * Mettle's services and the modifier lists other code registers into. The lists live here, not in an implementation,
 * so a perk can register its modifier during init whatever order the modules start in. Until a module sets its
 * service, a neutral stand-in answers (nothing weighs, nothing is worn).
 */
public final class MettleServices {
	private static final List<WeightModifier> WEIGHT_MODIFIERS = new CopyOnWriteArrayList<>();
	private static final List<BandModifier> BAND_MODIFIERS = new CopyOnWriteArrayList<>();

	private static BurdenService burden = new BurdenService() {
		@Override
		public float weight(ItemInstance stack) {
			return 0f;
		}

		@Override
		public float weight(Player player, ItemInstance stack) {
			return 0f;
		}

		@Override
		public float load(Player player) {
			return 0f;
		}

		@Override
		public float capacity(Player player) {
			return 0f;
		}

		@Override
		public LoadBand band(Player player) {
			return LoadBand.LIGHT;
		}

		@Override
		public BandRules rules(Player player) {
			return BandRules.DEFAULT;
		}
	};

	private static GearService gear = new GearService() {
		@Override
		public ItemStack worn(Player player, GearSlot slot) {
			return ItemStack.EMPTY;
		}

		@Override
		public List<ItemStack> pockets(Player player, GearSlot slot) {
			return List.of();
		}

		@Override
		public boolean isGear(ItemStack stack) {
			return false;
		}
	};

	private static CardService cards = new CardService() {
		@Override
		public List<CardView> binder(ServerPlayer player) {
			return List.of();
		}

		@Override
		public List<CardView> deck(ServerPlayer player, ResourceKey<Skill> skill) {
			return List.of();
		}

		@Override
		public List<CardView> hand(ServerPlayer player, ResourceKey<Skill> skill) {
			return List.of();
		}

		@Override
		public Optional<CardView> card(MinecraftServer server, long id) {
			return Optional.empty();
		}

		@Override
		public Map<Identifier, Integer> activeSets(ServerPlayer player) {
			return Map.of();
		}

		@Override
		public int setCount(ServerPlayer player, Identifier keyword) {
			return 0;
		}

		@Override
		public double trait(ServerPlayer player, Identifier trait) {
			return 0;
		}

		@Override
		public float cooldownScale(ServerPlayer player) {
			return 1f;
		}

		@Override
		public List<Identifier> mythics(ServerPlayer player) {
			return List.of();
		}

		@Override
		public List<Identifier> activeMythics(ServerPlayer player) {
			return List.of();
		}
	};

	private MettleServices() {
	}

	/** Reading players' cards (docs/CARDS.md §25); set by the character module's cards. */
	public static CardService cards() {
		return cards;
	}

	public static void setCards(CardService service) {
		cards = service;
	}

	public static BurdenService burden() {
		return burden;
	}

	public static void setBurden(BurdenService service) {
		burden = service;
	}

	public static GearService gear() {
		return gear;
	}

	public static void setGear(GearService service) {
		gear = service;
	}

	public static void addWeightModifier(WeightModifier modifier) {
		WEIGHT_MODIFIERS.add(modifier);
	}

	public static List<WeightModifier> weightModifiers() {
		return WEIGHT_MODIFIERS;
	}

	public static void addBandModifier(BandModifier modifier) {
		BAND_MODIFIERS.add(modifier);
	}

	public static List<BandModifier> bandModifiers() {
		return BAND_MODIFIERS;
	}
}
