package dev.voidmc.mettle.audit;

import com.mojang.authlib.GameProfile;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Backgrounds;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.cards.Mythics;
import dev.voidmc.mettle.character.cards.Trades;
import dev.voidmc.mettle.character.draft.Drafts;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.DraftState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.Deflater;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.impl.attachment.sync.AttachmentSync;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Audit only (branch audit-server). Runs in Fabric's headless game-test server, i.e. the dedicated-server environment
 * (KnotServer, the common Minecraft jar only, server mixin configs only): every Mettle mixin target is force-loaded, then
 * a background, XP, a draft kept and slotted, a 300-card binder (with its sync size), a trade between two players, and a
 * world first claimed twice, all with Fabric fake players. Anything that touches a client-only class fails here.
 */
public class ServerEnvAudit {
	private static final ResourceKey<Skill> MINING = Progress.skill("mining");

	@GameTest(maxTicks = 1200)
	public void serverEnvironment(GameTestHelper helper) {
		log("environment " + FabricLoader.getInstance().getEnvironmentType() + ", client classes present: " + present("net.minecraft.client.Minecraft"));
		helper.assertTrue(!present("net.minecraft.client.Minecraft"), "client classes are on the server class path");

		// Every mixin target of every selected config, loaded now (a missing target or a failed injection throws).
		long t0 = System.nanoTime();
		MixinEnvironment.getCurrentEnvironment().audit();
		log("mixin audit done in " + (System.nanoTime() - t0) / 1_000_000 + " ms");

		ServerLevel level = helper.getLevel();
		FakePlayer ann = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Ann"));
		FakePlayer ben = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Ben"));
		ann.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
		ben.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 1)));
		Trades.standIn(ann);
		Trades.standIn(ben);
		var ranger = ResourceKey.create(MettleRegistries.BACKGROUND, Mettle.id("ranger"));

		for (FakePlayer p : List.of(ann, ben)) {
			Backgrounds.force(p, ranger);
			Progress.award(p, MINING, Curves.skillXpFor(6) * 1.2);
			CharacterState s = CharacterStore.get(p);
			List<DraftState.Card> hand = s.drafts().hand(MINING);
			helper.assertTrue(!hand.isEmpty(), "no hand dealt for " + p.getName().getString());
			Optional<Drafts.Refusal> kept = Drafts.keep(p, MINING, 0, Cards.Keep.SLOT);
			helper.assertTrue(kept.isEmpty(), "keep & slot refused: " + kept);
			Optional<Drafts.Refusal> kept2 = Drafts.keep(p, MINING, 0, Cards.Keep.BINDER);
			helper.assertTrue(kept2.isEmpty(), "keep refused: " + kept2);
			CardsState cs = CardStore.get(p);
			log(p.getName().getString() + ": mining level " + s.level(MINING) + ", cards " + cs.cards().size() + ", slotted " + cs.slotted().size()
				+ ", active perks " + cs.active());
			helper.assertTrue(cs.slotted().size() >= 1, "nothing slotted");
		}

		// A trade between the two (stand-ins: Trades finishes the countdown on demand).
		long annCard = CardStore.get(ann).cards().stream().filter(c -> !c.slotted()).findFirst().orElseThrow().id();
		long benCard = CardStore.get(ben).cards().stream().filter(c -> !c.slotted()).findFirst().orElseThrow().id();
		log("trade: calm " + Trades.calm(ann) + "/" + Trades.calm(ben) + ", request " + Trades.request(ann, ben) + ", accept " + Trades.accept(ben)
			+ ", offers " + Trades.offerCard(ann, annCard) + " " + Trades.offerCard(ben, benCard) + ", locks " + Trades.lock(ann) + " " + Trades.lock(ben)
			+ ", confirm " + Trades.confirm(ann));

		// A world first claimed by both: only one gets it.
		boolean first = Mythics.first(ann, "far_horizon");
		boolean second = Mythics.first(ben, "far_horizon");
		log("world first far_horizon: Ann " + first + ", Ben " + second + "; Ann public " + ann.getAttached(Mythics.PUBLIC));
		helper.assertTrue(first && !second, "world first given " + first + "/" + second);

		// 300 cards in one binder: the size of the attachment Fabric syncs to the owner on every change.
		String[] perks = {"quarryman", "prospector", "tunneler", "deep_delver", "stonesense", "ore_sense"};
		t0 = System.nanoTime();
		while (CardStore.get(ann).cards().size() < 300) {
			Cards.give(ann, ResourceKey.create(MettleRegistries.PERK, Mettle.id("mining/" + perks[CardStore.get(ann).cards().size() % perks.length])),
				DraftState.Rarity.EPIC, false);
		}
		long giveMs = (System.nanoTime() - t0) / 1_000_000;
		CardsState cs = CardStore.get(ann);
		byte[] raw = encode(cs);
		log("300-card binder: CardsState " + raw.length + " B raw, " + deflated(raw) + " B deflated, " + raw.length / cs.cards().size()
			+ " B per card; Fabric's attachment sync limit " + AttachmentSync.DEFAULT_MAX_DATA_SIZE + " B; 300 gives took " + giveMs + " ms ("
			+ giveMs / 300.0 + " ms each)");
		long binder = cs.cards().stream().filter(c -> !c.slotted() && c.mythicId().isEmpty()).mapToLong(CardData::id).max().orElseThrow();
		t0 = System.nanoTime();
		var slotted = Cards.slot(ann, binder, -1);
		log("one slot with 300 cards: " + slotted + " in " + (System.nanoTime() - t0) / 1_000 + " us");
		t0 = System.nanoTime();
		for (int i = 0; i < 100; i++) {
			Progress.award(ann, MINING, 0.5);
		}
		log("100 XP awards with 300 cards: " + (System.nanoTime() - t0) / 1_000 + " us; CharacterState "
			+ ByteBufCodecs.fromCodecTrusted(CharacterState.CODEC).getClass().getSimpleName() + " " + encodeChar(CharacterStore.get(ann)).length + " B raw");
		Identifier far = Mettle.id("far_horizon");
		log("done; Ann holds far_horizon card: " + CardStore.get(ann).cards().stream().anyMatch(c -> c.mythicId().map(far::equals).orElse(false)));
		helper.succeed();
	}

	private static boolean present(String name) {
		try {
			Class.forName(name, false, ServerEnvAudit.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	private static byte[] encode(CardsState s) {
		ByteBuf b = Unpooled.buffer();
		ByteBufCodecs.fromCodecTrusted(CardsState.CODEC).encode(b, s);
		byte[] out = new byte[b.readableBytes()];
		b.readBytes(out);
		return out;
	}

	private static byte[] encodeChar(CharacterState s) {
		ByteBuf b = Unpooled.buffer();
		ByteBufCodecs.fromCodecTrusted(CharacterState.CODEC).encode(b, s);
		byte[] out = new byte[b.readableBytes()];
		b.readBytes(out);
		return out;
	}

	private static int deflated(byte[] in) {
		Deflater d = new Deflater();
		d.setInput(in);
		d.finish();
		byte[] buf = new byte[in.length + 1024];
		int n = d.deflate(buf);
		d.end();
		return n;
	}

	private static void log(String s) {
		Mettle.LOGGER.info("AUDIT-SERVER " + s);
	}
}
