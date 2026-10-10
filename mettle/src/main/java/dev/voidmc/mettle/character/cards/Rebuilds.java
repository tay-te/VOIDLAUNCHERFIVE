package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.respec.Draughts;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rebuilding (docs/CARDS.md §7): a <b>sitting</b> opened at a Card Shrine (any skill, any card; the fee is burned) or by a
 * Hearth trainer (the trainer's skills, cards up to the trainer's cap; the fee goes to the trainer; a city master scars
 * one card in two). In a sitting the player takes cards out of a deck: free when a free way covers it (§7.4), else one
 * Draught of Forgetting for that skill per sitting, a fee per card by its requirement, and a scar per card. A shrine
 * sitting also releases cards for rerolls (§7.5). Server thread; a sitting ends when the player walks away.
 */
public final class Rebuilds {
	public enum Kind {
		SHRINE, TRAINER
	}

	/**
	 * An open sitting.
	 *
	 * @param skills the skills it rebuilds (a trainer's); empty = any (a shrine)
	 * @param cap    the highest requirement it can take out (a trainer's tier cap; 20 at a shrine)
	 * @param master a city master's hand: one card in two keeps its trait
	 * @param payee  who the fee goes to (compat's trainer handle), or null (a shrine burns it)
	 * @param spent  skills whose draught this sitting already took
	 */
	public record Sitting(Kind kind, List<ResourceKey<Skill>> skills, int cap, boolean master, ResourceKey<Level> dimension, Vec3 at, long opened,
		@Nullable Object payee, Set<ResourceKey<Skill>> spent) {
		public boolean allows(ResourceKey<Skill> skill) {
			return this.skills.isEmpty() || this.skills.contains(skill);
		}
	}

	private static final Map<UUID, Sitting> SITTINGS = new HashMap<>();
	private static final double REACH = 6.0;
	private static final long TIMEOUT = 20 * 60 * 10;
	private static CardFees fees = CardFees.EMERALDS;

	private Rebuilds() {
	}

	static void init() {
		ServerPlayerEvents.LEAVE.register(p -> SITTINGS.remove(p.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0 || SITTINGS.isEmpty()) {
				return;
			}
			for (UUID id : List.copyOf(SITTINGS.keySet())) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p == null || sitting(p).isEmpty()) {
					SITTINGS.remove(id);
				}
			}
		});
	}

	/** compat.hearth puts the Hearth coin payer in place. */
	public static void setFees(CardFees f) {
		fees = f;
	}

	public static CardFees fees() {
		return fees;
	}

	// ---------------------------------------------------------------- sittings

	public static void openShrine(ServerPlayer player, BlockPos pos) {
		open(player, new Sitting(Kind.SHRINE, List.of(), 20, false, player.level().dimension(), Vec3.atCenterOf(pos), now(player), null, new HashSet<>()));
	}

	/** A Hearth trainer's rebuild (compat): their skills, their cap, a city master or not, the fee to {@code payee}. */
	public static void openTrainer(ServerPlayer player, List<ResourceKey<Skill>> skills, int cap, boolean master, Vec3 at, Object payee) {
		open(player, new Sitting(Kind.TRAINER, List.copyOf(skills), cap, master, player.level().dimension(), at, now(player), payee, new HashSet<>()));
	}

	static void open(ServerPlayer player, Sitting sitting) {
		Cards.ensure(player);
		SITTINGS.put(player.getUUID(), sitting);
		send(player, sitting);
		SignaturePerks.feedback(player, Mettle.id("cards/sitting"), Component.translatable(sitting.kind() == Kind.SHRINE
			? "mettle.cards.sitting.shrine" : sitting.master() ? "mettle.cards.sitting.master" : "mettle.cards.sitting.trainer"));
		list(player);
	}

	public static void close(ServerPlayer player) {
		if (SITTINGS.remove(player.getUUID()) != null) {
			send(player, null);
		}
	}

	/** The player's open sitting, if they are still at it (within 6 blocks, same dimension, under ten minutes). */
	public static Optional<Sitting> sitting(ServerPlayer player) {
		Sitting s = SITTINGS.get(player.getUUID());
		if (s == null) {
			return Optional.empty();
		}
		if (!player.level().dimension().equals(s.dimension()) || player.position().distanceTo(s.at()) > REACH + 2 || now(player) - s.opened() > TIMEOUT
			|| !player.isAlive()) {
			close(player);
			return Optional.empty();
		}
		return Optional.of(s);
	}

	public static boolean atShrine(ServerPlayer player) {
		return sitting(player).map(s -> s.kind() == Kind.SHRINE).orElse(false);
	}

	private static void send(ServerPlayer player, @Nullable Sitting s) {
		ServerPlayNetworking.send(player, s == null
			? new MettlePayloads.CardSitting(false, 0, List.of(), 0, false, fees.coin())
			: new MettlePayloads.CardSitting(true, s.kind().ordinal(), s.skills(), s.cap(), s.master(), fees.coin()));
	}

	private static long now(ServerPlayer player) {
		return player.level().getServer().overworld().getGameTime();
	}

	// ---------------------------------------------------------------- rebuilding

	/** One card's line in a rebuild: its fee and what its scar takes. */
	public record Line(CardData card, int bits, int emeralds, CardRules.Scar scar, boolean dormant) {
	}

	/** What taking these cards out would cost now: a free way, or the draught, the fees and the scars. */
	public record Preview(List<Line> lines, int bits, int emeralds, boolean draught, Optional<Cards.Way> free) {
	}

	public static Preview preview(ServerPlayer player, Sitting sitting, List<CardData> picked) {
		CardsState state = CardStore.get(player);
		Optional<Cards.Way> free = Cards.freeWay(player, picked);
		List<Line> lines = new ArrayList<>();
		int bits = 0;
		int emeralds = 0;
		for (CardData c : picked) {
			boolean dormant = state.isDormant(c.id());
			// A Mythic: 1,000 bits (24 emeralds), no draught, never scarred (§7.1, §9.1).
			int b = dormant || free.isPresent() ? 0 : c.mythic() ? CardConfig.get().mythic_fee : CardRules.feeBits(c.needs());
			int e = dormant || free.isPresent() ? 0 : c.mythic() ? CardConfig.get().rebuild_emeralds[4] : CardRules.feeEmeralds(c.needs());
			CardRules.Scar scar = dormant || free.isPresent() ? new CardRules.Scar(Optional.empty(), true) : CardRules.scar(c, sitting.master());
			lines.add(new Line(c, b, e, scar, dormant));
			bits += b;
			emeralds += e;
		}
		ResourceKey<Skill> skill = picked.isEmpty() ? null : picked.getFirst().place().deck().orElse(picked.getFirst().skill());
		boolean ordinary = lines.stream().anyMatch(l -> !l.dormant() && !l.card().mythic());
		boolean draught = free.isEmpty() && ordinary && skill != null && !sitting.spent().contains(skill);
		return new Preview(lines, bits, emeralds, draught, free);
	}

	/**
	 * Takes slotted cards of one deck out at the player's sitting: a free way when one covers them, else a Draught of
	 * Forgetting for the skill (once a sitting), the fees and a scar on each card that was doing something.
	 */
	public static Optional<CardRules.Refusal> rebuild(ServerPlayer player, List<Long> ids) {
		Optional<Sitting> open = sitting(player);
		if (open.isEmpty()) {
			return Optional.of(CardRules.Refusal.NOT_AT_SHRINE);
		}
		Sitting sitting = open.get();
		CardsState state = CardStore.get(player);
		List<CardData> picked = new ArrayList<>();
		for (long id : new HashSet<>(ids)) {
			Optional<CardData> c = state.card(id);
			if (c.isEmpty()) {
				return Optional.of(CardRules.Refusal.NO_CARD);
			}
			if (!c.get().slotted()) {
				return Optional.of(CardRules.Refusal.NOT_SLOTTED);
			}
			picked.add(c.get());
		}
		if (picked.isEmpty()) {
			return Optional.of(CardRules.Refusal.NO_CARD);
		}
		ResourceKey<Skill> skill = picked.getFirst().place().deck().orElse(picked.getFirst().skill());
		if (picked.stream().anyMatch(c -> !c.place().deck().orElse(c.skill()).equals(skill))) {
			return Optional.of(CardRules.Refusal.MIXED_SKILLS);
		}
		if (!sitting.allows(skill)) {
			return Optional.of(CardRules.Refusal.WRONG_SKILL);
		}
		if (picked.stream().anyMatch(c -> c.needs() > sitting.cap())) {
			return Optional.of(CardRules.Refusal.OVER_CAP);
		}
		Preview preview = preview(player, sitting, picked);
		if (preview.free().isPresent()) {
			Cards.take(player, picked, preview.free().get());
			return Optional.empty();
		}
		ItemStack draught = preview.draught() ? findDraught(player, skill) : ItemStack.EMPTY;
		if (preview.draught() && draught.isEmpty() && !player.hasInfiniteMaterials()) {
			return Optional.of(CardRules.Refusal.NO_DRAUGHT);
		}
		if (!fees.canPay(player, preview.bits(), preview.emeralds(), sitting.payee())) {
			return Optional.of(CardRules.Refusal.CANT_PAY);
		}
		if (!fees.pay(player, preview.bits(), preview.emeralds(), sitting.payee())) {
			return Optional.of(CardRules.Refusal.CANT_PAY);
		}
		if (preview.draught()) {
			if (!draught.isEmpty() && !player.hasInfiniteMaterials()) {
				draught.shrink(1);
				ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
				if (!player.getInventory().add(bottle)) {
					player.drop(bottle, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
			sitting.spent().add(skill);
		}
		CardLedger ledger = Cards.ledger(player.level().getServer());
		List<Component> scars = new ArrayList<>();
		for (Line line : preview.lines()) {
			CardData after = line.dormant() ? line.card() : CardRules.scarred(line.card(), sitting.master());
			ledger.update(line.card().id(), x -> after.withPlace(CardData.Place.BINDER));
			line.scar().loses().ifPresent(t -> scars.add(CardText.trait(player.level().registryAccess(), t, Optional.empty())));
		}
		Cards.sync(player);
		MutableComponent lost = Component.empty();
		for (int i = 0; i < scars.size(); i++) {
			lost.append(i == 0 ? Component.empty() : Component.literal(", ")).append(scars.get(i));
		}
		SignaturePerks.feedback(player, Mettle.id("cards/" + skill.identifier().getPath()), Component.translatable(
			scars.isEmpty() ? "mettle.cards.rebuilt" : "mettle.cards.rebuilt.scars", picked.size(), Cards.skillName(player, skill),
			fees.describe(preview.bits(), preview.emeralds()), lost));
		Mettle.LOGGER.info("{} rebuilt {}: {} cards out, {} bits / {} emeralds, scars {}", player.getName().getString(), skill.identifier(), picked.size(),
			preview.bits(), preview.emeralds(), scars.size());
		return Optional.empty();
	}

	private static ItemStack findDraught(ServerPlayer player, ResourceKey<Skill> skill) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(Draughts.FORGETTING) && skill.identifier().equals(s.get(Draughts.FORGETS))) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	// ---------------------------------------------------------------- releasing (§7.5)

	/** At a Card Shrine: three binder cards of one skill leave the world for one reroll. Their ids are never reused. */
	public static Optional<CardRules.Refusal> release(ServerPlayer player, List<Long> ids) {
		if (!atShrine(player)) {
			return Optional.of(CardRules.Refusal.NOT_AT_SHRINE);
		}
		CardsState state = CardStore.get(player);
		List<CardData> picked = new ArrayList<>();
		for (long id : ids) {
			Optional<CardData> c = state.card(id);
			if (c.isEmpty()) {
				return Optional.of(CardRules.Refusal.NO_CARD);
			}
			picked.add(c.get());
		}
		int rerolls = CharacterStore.get(player).drafts().rerolls();
		Optional<CardRules.Refusal> no = CardRules.canRelease(picked, rerolls, now(player));
		if (no.isPresent()) {
			return no;
		}
		CardLedger ledger = Cards.ledger(player.level().getServer());
		picked.forEach(c -> ledger.update(c.id(), x -> x.withPlace(CardData.Place.RELEASED)));
		dev.voidmc.mettle.character.draft.Drafts.addRerolls(player, 1);
		Cards.sync(player);
		SignaturePerks.feedback(player, Mettle.id("cards/released"), Component.translatable("mettle.cards.released", picked.size(),
			CharacterStore.get(player).drafts().rerolls()));
		return Optional.empty();
	}

	// ---------------------------------------------------------------- the sitting in chat (until the Hub draws it)

	/** Lists every deck the sitting can rebuild, each slotted card with its fee and scar, clickable to take it out. */
	public static void list(ServerPlayer player) {
		Optional<Sitting> open = sitting(player);
		if (open.isEmpty()) {
			return;
		}
		Sitting s = open.get();
		CardsState state = CardStore.get(player);
		var registries = player.level().registryAccess();
		var skills = registries.lookupOrThrow(MettleRegistries.SKILL);
		skills.listElements().sorted(java.util.Comparator.comparingInt(r -> r.value().order())).forEach(ref -> {
			List<CardData> deck = state.deck(ref.key());
			if (deck.isEmpty() || !s.allows(ref.key())) {
				return;
			}
			player.sendSystemMessage(Component.translatable("mettle.cards.sitting.deck", ref.value().name(), deck.size()).withStyle(ChatFormatting.BOLD));
			for (CardData c : deck) {
				Preview p = preview(player, s, List.of(c));
				Line line = p.lines().getFirst();
				Optional<Perk> perk = CardText.perk(registries, c.perk());
				MutableComponent out = Component.literal("  ").append(CardCommands.line(player, c));
				Component cost = p.free().isPresent() ? Component.translatable("mettle.cards.cost.free." + p.free().get().name().toLowerCase(Locale.ROOT))
					: c.needs() > s.cap() ? Component.translatable("mettle.cards.cost.over_cap", s.cap())
					: Component.translatable("mettle.cards.cost", fees.describe(line.bits(), line.emeralds()),
						line.scar().spared() ? Component.translatable("mettle.cards.scar.spared")
							: line.scar().loses().map(t -> (Component) Component.translatable("mettle.cards.scar.loses", CardText.trait(registries, t, Optional.empty())))
								.orElse(Component.translatable("mettle.cards.scar.mark")));
				out.append(Component.literal(" ")).append(Component.literal("[").append(cost).append("]")
					.withStyle(st -> st.withColor(ChatFormatting.GOLD).withClickEvent(new ClickEvent.RunCommand("/mettle cards rebuild " + c.id()))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable("mettle.cards.click_rebuild",
							perk.map(Perk::name).orElse(Component.literal("?")))))));
				player.sendSystemMessage(out);
			}
		});
		if (s.kind() == Kind.SHRINE) {
			player.sendSystemMessage(Component.translatable("mettle.cards.sitting.release").withStyle(ChatFormatting.GRAY));
		}
	}
}
