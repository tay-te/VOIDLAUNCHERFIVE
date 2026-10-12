package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/** Audit (persistence): reproductions of save-data findings. Each test passes by asserting the current, faulty behaviour. */
class PersistenceAuditTest {
	static {
		TestCards.DECK.toString();
	}

	private static List<CardData> binder(int n) {
		List<CardData> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(TestCards.plain("mining/prospector", Rarity.COMMON, -1));
		}
		return out;
	}

	/** P1: a ledger that came back empty (corrupt or truncated cards.dat → SavedDataStorage returns a fresh one) wipes every cached card on join. */
	@Test
	void emptyLedgerWipesTheCache() {
		List<CardData> cache = binder(40);
		CardRules.Reconciled r = CardRules.reconcile(cache, List.of());
		assertTrue(r.cards().isEmpty(), "every card the player held is gone");
		assertEquals(40, r.dropped().size());
	}

	/** P2: a card the ledger never saved (player file saved on logout, ledger not yet) is dropped, not put back. */
	@Test
	void cardNewerThanTheLedgerIsDropped() {
		List<CardData> cache = binder(3);
		List<CardData> ledger = cache.subList(0, 2);
		CardRules.Reconciled r = CardRules.reconcile(cache, ledger);
		assertEquals(List.of(cache.get(2).id()), r.dropped());
	}

	/** P3: one card the codec cannot read (an unknown enum after a downgrade, a hand edit) — what does the ledger keep? */
	@Test
	void oneBadCardInTheLedger() {
		CardLedger l = new CardLedger();
		for (CardData c : binder(5)) {
			l.add(c);
		}
		Tag tag = CardLedger.CODEC.encodeStart(NbtOps.INSTANCE, l).getOrThrow();
		CompoundTag root = (CompoundTag) tag;
		ListTag cards = root.getListOrEmpty("cards");
		((CompoundTag) cards.get(2)).putString("rarity", "legendary");
		DataResult<CardLedger> parsed = CardLedger.CODEC.parse(NbtOps.INSTANCE, root);
		// SavedDataStorage uses resultOrPartial(...).orElse(null): null means a fresh, empty ledger.
		Optional<CardLedger> kept = parsed.resultOrPartial(e -> System.out.println("[audit] ledger parse error: " + e));
		System.out.println("[audit] one bad card: success=" + parsed.isSuccess() + " partial=" + kept.isPresent()
			+ " cardsKept=" + kept.map(CardLedger::size).orElse(-1));
		assertTrue(kept.isEmpty() || kept.get().size() < 5, "at least the bad card is lost");
	}

	/** P4: the character record refuses more than 1000 queued drafts in a skill, both ways (an op's /mettle draft give x11). */
	@Test
	void queuedOver1000BreaksTheCharacterCodec() {
		DraftState d = DraftState.fresh(1, 2).withQueued(ResourceKey.create(dev.voidmc.mettle.api.MettleRegistries.SKILL,
			Identifier.fromNamespaceAndPath("mettle", "mining")), 1001);
		CharacterState s = new CharacterState(Optional.empty(), Map.of(), List.of(), Map.of(), Map.of(), Optional.of(d));
		DataResult<Tag> enc = CharacterState.CODEC.encodeStart(NbtOps.INSTANCE, s);
		System.out.println("[audit] encode queued=1001: success=" + enc.isSuccess() + " partial=" + enc.resultOrPartial(e -> { }).isPresent());
		assertTrue(enc.isError());
		// Fabric's attachment map is Codec.dispatchedMap(...).promotePartial: does one bad entry take the others with it?
		Codec<Map<String, Object>> map = Codec.dispatchedMap(Codec.STRING, k -> k.equals("character") ? (Codec) CharacterState.CODEC : (Codec) Codec.INT);
		DataResult<Tag> all = map.encodeStart(NbtOps.INSTANCE, Map.of("character", s, "other", 7));
		System.out.println("[audit] attachments map encode: success=" + all.isSuccess() + " partial=" + all.resultOrPartial(e -> { }).orElse(null));
	}

	/** P5: size and time of a big ledger (300 cards x 100 players) and of one full binder in a player file. */
	@Test
	void bigLedger() throws Exception {
		CardLedger l = new CardLedger();
		CardData.TraitRoll t = new CardData.TraitRoll(Identifier.fromNamespaceAndPath("mettle", "mining/deepborn"), 12.5);
		for (int p = 0; p < 100; p++) {
			UUID owner = new UUID(p, p);
			for (CardData c : binder(300)) {
				CardData.History h = c.history();
				for (int k = 0; k < 4; k++) {
					h = h.passedTo(new CardData.Holder(new UUID(k, p), "SomePlayerName" + k, 100 + k));
				}
				CardData x = new CardData(l.allocate(), c.skill(), c.perk(), c.rarity(), c.rank(), c.keywords(), List.of(t, t, t), c.needs(), c.drawn(),
					owner, h, 0, c.scarSeed(), c.place(), 0L);
				l.add(x);
			}
		}
		for (int w = 0; w < 5; w++) {
			long a = System.nanoTime();
			CardLedger.CODEC.encodeStart(NbtOps.INSTANCE, l).getOrThrow();
			System.out.printf("[audit] warm encode %d: %d ms%n", w, (System.nanoTime() - a) / 1_000_000);
		}
		long t0 = System.nanoTime();
		CompoundTag root = new CompoundTag();
		root.put("data", CardLedger.CODEC.encodeStart(NbtOps.INSTANCE, l).getOrThrow());
		long t1 = System.nanoTime();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, out);
		long t2 = System.nanoTime();
		CompoundTag back = NbtIo.readCompressed(new ByteArrayInputStream(out.toByteArray()), NbtAccounter.unlimitedHeap());
		CardLedger parsed = CardLedger.CODEC.parse(NbtOps.INSTANCE, back.get("data")).getOrThrow();
		long t3 = System.nanoTime();
		System.out.printf("[audit] ledger %d cards: %d KB gz, encode %d ms (server thread), gzip %d ms, load %d ms%n", parsed.size(), out.size() / 1024,
			(t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000);
		assertEquals(30_000, parsed.size());

		CardsState cache = CardsState.EMPTY.withCards(l.owned(new UUID(0, 0)));
		ByteArrayOutputStream one = new ByteArrayOutputStream();
		CompoundTag p = new CompoundTag();
		p.put("c", CardsState.CODEC.encodeStart(NbtOps.INSTANCE, cache).getOrThrow());
		NbtIo.write(p, new java.io.DataOutputStream(one));
		System.out.printf("[audit] one 300-card cache: %d KB raw NBT (player file and every sync)%n", one.size() / 1024);
	}
}
