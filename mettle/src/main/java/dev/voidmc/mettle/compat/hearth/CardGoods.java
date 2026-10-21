package dev.voidmc.mettle.compat.hearth;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.VirtualGoods;
import dev.voidmc.mettle.api.Mythic;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.character.cards.CardText;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.cards.Listings;
import dev.voidmc.mettle.character.cards.Mythics;
import dev.voidmc.mettle.core.CardData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Mettle's cards as VOID Hearth's virtual goods (request 7): players list binder cards at a Hearth card broker for coin.
 * Hearth owns the board, the price, fees, tax and the seller's pay; the card's moves are {@link Listings}' (escrow in the
 * ledger, one sale, attunement for the buyer, committed with the ledger like a trade). Only registered when the loaded
 * Hearth has {@code VirtualGoods} (HearthCompat checks first).
 */
final class CardGoods implements VirtualGoods {
	private static volatile @Nullable MinecraftServer server;

	static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
		HearthServices.addVirtualGoods(new CardGoods());
	}

	@Override
	public String kind() {
		return Listings.KIND;
	}

	@Override
	public List<Long> listable(ServerPlayer seller) {
		return Listings.listable(seller);
	}

	private static Optional<CardData> card(long id) {
		MinecraftServer s = server;
		return s == null ? Optional.empty() : Cards.ledger(s).get(id);
	}

	private static RegistryAccess registries() {
		MinecraftServer s = server;
		return s == null ? RegistryAccess.EMPTY : s.registryAccess();
	}

	/** "Tunneler III · Rare", "The Twin Edge · Mythic". */
	@Override
	public Component name(long id) {
		Optional<CardData> card = card(id);
		if (card.isEmpty()) {
			return Component.translatable("mettle.broker.gone");
		}
		CardData c = card.get();
		RegistryAccess r = registries();
		MutableComponent name = c.mythicId().flatMap(m -> Mythics.def(r, m)).map(m -> m.name().copy())
			.orElseGet(() -> CardText.perk(r, c.perk()).map(p -> CardText.name(p, c.rank())).orElse(Component.literal(c.perk().identifier().getPath())));
		return name.append(" · ").append(Component.translatable("mettle.draft.rarity." + c.rarity().getSerializedName()));
	}

	/** The printed id, the requirement, the traits, the keywords and the history. */
	@Override
	public List<Component> describe(long id) {
		Optional<CardData> card = card(id);
		List<Component> out = new ArrayList<>();
		if (card.isEmpty()) {
			return out;
		}
		CardData c = card.get();
		RegistryAccess r = registries();
		out.add(Component.literal(Cards.printId(r, c)).withStyle(ChatFormatting.DARK_GRAY));
		if (c.mythic()) {
			c.mythicId().flatMap(m -> Mythics.def(r, m)).map(Mythic::effect).ifPresent(out::add);
		} else {
			CardText.perk(r, c.perk()).map(Perk::description).ifPresent(d -> out.add(d.copy().withStyle(ChatFormatting.GRAY)));
			out.add(Component.translatable("mettle.broker.needs", c.needs(), registries().lookupOrThrow(dev.voidmc.mettle.api.MettleRegistries.SKILL)
				.getOptional(c.skill()).map(dev.voidmc.mettle.api.Skill::name).orElse(Component.literal(c.skill().identifier().getPath()))));
			c.traits().forEach(t -> out.add(CardText.trait(r, t, Optional.empty()).withStyle(ChatFormatting.AQUA)));
			if (!c.keywords().isEmpty()) {
				out.add(CardText.keywords(r, c.keywords()).withStyle(ChatFormatting.GOLD));
			}
		}
		out.add(Component.translatable("mettle.broker.drawn", c.drawn().name(), c.drawn().day()).withStyle(ChatFormatting.DARK_GRAY));
		out.add(Component.translatable("mettle.broker.owners", c.history().owners(), c.scars()).withStyle(ChatFormatting.DARK_GRAY));
		return out;
	}

	/** A display-only stack: a page named for the card (never given out; Hearth only draws it). */
	@Override
	public ItemStack display(long id) {
		ItemStack stack = new ItemStack(card(id).map(CardData::mythic).orElse(false) ? Items.ENCHANTED_BOOK : Items.PAPER);
		stack.set(DataComponents.ITEM_NAME, name(id));
		stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(describe(id)));
		return stack;
	}

	@Override
	public boolean hold(long id, ServerPlayer seller, long listing) {
		return Listings.hold(seller, id, listing);
	}

	@Override
	public void release(long id) {
		MinecraftServer s = server;
		if (s != null) {
			Listings.release(s, id);
		}
	}

	@Override
	public boolean transfer(long id, UUID buyer) {
		MinecraftServer s = server;
		return s != null && Listings.transfer(s, id, buyer);
	}

	@Override
	public String toString() {
		return "VOID Mettle cards";
	}
}
