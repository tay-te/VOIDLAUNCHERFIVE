package dev.voidmc.hub.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * VOID Mettle's server side, for the hub's Mettle test: exact cards, a forced Mythic roll, the Draught of Forgetting
 * and a stand-in trading partner. The hub never links against Mettle, so this is reflection, as Mettle's own client
 * tour does it directly (mettle/src/gametest: ShrineJourney, MythicJourney, TradeJourney). Server thread only.
 */
final class MettleServer {

	private static final String CARDS = "dev.voidmc.mettle.character.cards.";

	private MettleServer() {
	}

	private static Class<?> cls(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			throw new AssertionError("Mettle class " + name + " is missing", e);
		}
	}

	private static Method method(Class<?> c, String name, int params) {
		for (Method m : c.getMethods()) {
			if (m.getName().equals(name) && m.getParameterCount() == params) {
				return m;
			}
		}
		throw new AssertionError("Mettle has no " + c.getSimpleName() + "." + name + " with " + params + " parameters");
	}

	static Object call(String cls, String name, Object... args) {
		try {
			return method(cls(cls), name, args.length).invoke(null, args);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Mettle " + cls + "." + name + " failed", e.getCause() == null ? e : e.getCause());
		}
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("mettle", path);
	}

	/**
	 * Gives the player an exact card ({@code Cards.giveExact}): perk, rarity (COMMON, RARE, EPIC, CURSED), keywords,
	 * traits as {@code "mining/deepborn=9"}, and the skill level it needs. Returns its ledger id.
	 */
	static long giveExact(ServerPlayer p, String perk, String rarity, List<String> keywords, List<String> traits, int needs) {
		try {
			Class<?> draft = cls("dev.voidmc.mettle.core.DraftState");
			Class<?> card = cls("dev.voidmc.mettle.core.DraftState$Card");
			Class<?> rarityEnum = cls("dev.voidmc.mettle.core.DraftState$Rarity");
			Class<?> roll = cls("dev.voidmc.mettle.core.CardData$TraitRoll");
			Object key = call("dev.voidmc.mettle.character.Perks", "key", id(perk));
			@SuppressWarnings({"unchecked", "rawtypes"})
			Object r = Enum.valueOf((Class) rarityEnum, rarity);
			List<Identifier> kws = new ArrayList<>();
			for (String k : keywords) {
				kws.add(id(k));
			}
			List<Object> rolls = new ArrayList<>();
			for (String t : traits) {
				String[] kv = t.split("=");
				rolls.add(roll.getDeclaredConstructor(Identifier.class, double.class).newInstance(id(kv[0]), Double.parseDouble(kv[1])));
			}
			Object offer = card.getDeclaredConstructors()[0].newInstance(key, 1, r, false, kws, rolls, needs);
			Object data = call(CARDS + "Cards", "giveExact", p, offer);
			return (Long) data.getClass().getMethod("id").invoke(data);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("giveExact " + perk + " failed", e.getCause() == null ? e : e.getCause());
		}
	}

	/** Sets the drawn-Mythic chance per draft (0.005 normally; 1 makes the next deal hold one). Returns the old one. */
	static double mythicChance(double chance) {
		try {
			Object config = call(CARDS + "CardConfig", "get");
			var f = config.getClass().getField("mythic_draw_chance");
			double was = f.getDouble(config);
			f.setDouble(config, chance);
			return was;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("CardConfig.mythic_draw_chance", e);
		}
	}

	/** A Draught of Forgetting for a skill ({@code Draughts.forgetting}): the key to a shrine rebuild. */
	static ItemStack draught(String skill) {
		return (ItemStack) call("dev.voidmc.mettle.character.respec.Draughts", "forgetting", id(skill));
	}

	static void clearCards(ServerPlayer p) {
		call(CARDS + "Cards", "clear", p);
	}

	static void freeSwaps(ServerPlayer p, int n) {
		call(CARDS + "Cards", "setFreeSwaps", p, n);
	}

	/** Empties a skill's draft queue and hand, then queues {@code count} fresh drafts (a hand is dealt for the first). */
	static void freshDrafts(ServerPlayer p, String skill, int count) {
		try {
			Object key = call("dev.voidmc.mettle.character.Progress", "skill", skill);
			Object st = call("dev.voidmc.mettle.core.CharacterStore", "get", p);
			Object d = st.getClass().getMethod("drafts").invoke(st);
			d = d.getClass().getMethod("withQueued", net.minecraft.resources.ResourceKey.class, int.class).invoke(d, key, 0);
			d = d.getClass().getMethod("withHand", net.minecraft.resources.ResourceKey.class, List.class).invoke(d, key, List.of());
			Object st2 = st.getClass().getMethod("withDraft", d.getClass()).invoke(st, d);
			call("dev.voidmc.mettle.core.CharacterStore", "set", p, st2);
			call("dev.voidmc.mettle.character.draft.Drafts", "give", p, key, count);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("fresh drafts for " + skill, e.getCause() == null ? e : e.getCause());
		}
	}

	/** Trades.* with a stand-in partner (a Fabric fake player Mettle treats as a player). */
	static Object trade(String name, Object... args) {
		return call(CARDS + "Trades", name, args);
	}
}
