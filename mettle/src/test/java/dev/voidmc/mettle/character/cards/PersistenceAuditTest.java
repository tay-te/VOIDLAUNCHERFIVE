package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Audit (persistence), flipped to the fixed behaviour: each of these failed on the audited code (8b4e1d14), where the
 * test asserted the fault, and passes now.
 */
class PersistenceAuditTest {
	static {
		TestCards.DECK.toString();
	}

	@TempDir
	Path dir;

	private static List<CardData> binder(int n) {
		List<CardData> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(TestCards.plain("mining/prospector", Rarity.COMMON, -1));
		}
		return out;
	}

	private static CardLedger ledgerOf(List<CardData> cards) {
		CardLedger l = new CardLedger();
		cards.forEach(l::add);
		return l;
	}

	/** P1: a ledger that came back empty (lost, or started over) no longer wipes the cache: every card is adopted back. */
	@Test
	void emptyLedgerAdoptsTheCache() {
		List<CardData> cache = binder(40);
		CardLedger empty = new CardLedger();
		CardRules.Reconciled r = CardRules.reconcile(TestCards.PLAYER, cache, List.of(), empty::known);
		assertEquals(40, r.cards().size(), "every card the player held is still there");
		assertEquals(40, r.adopted().size(), "and goes back into the ledger");
		assertTrue(r.dropped().isEmpty());
		r.adopted().forEach(empty::adopt);
		assertEquals(40, empty.owned(TestCards.PLAYER).size());
		assertTrue(empty.nextId() > cache.getLast().id(), "new ids come after the adopted ones");
	}

	/** P1: a card someone else's copy names, or one the ledger released, is still dropped (the ledger wins for ids it knows). */
	@Test
	void knownIdsStillFollowTheLedger() {
		List<CardData> cards = binder(3);
		CardLedger l = ledgerOf(cards);
		l.update(cards.get(0).id(), c -> c.withPlace(CardData.Place.RELEASED));
		CardData stranger = new CardData(l.allocate(), cards.get(1).skill(), cards.get(1).perk(), cards.get(1).rarity(), 1, cards.get(1).keywords(),
			List.of(), 0, cards.get(1).drawn(), new UUID(5, 5), cards.get(1).history(), 0, 1, CardData.Place.BINDER, 0);
		CardRules.Reconciled r = CardRules.reconcile(TestCards.PLAYER, List.of(cards.get(0), stranger), l.owned(TestCards.PLAYER), l::known);
		assertEquals(List.of(cards.get(0).id(), stranger.id()), r.dropped(), "released, and another owner's card, are not adopted");
		assertTrue(r.adopted().isEmpty());
	}

	/** P2: a card the ledger never saved (player file newer than the ledger) is adopted, not dropped. */
	@Test
	void cardNewerThanTheLedgerIsAdopted() {
		List<CardData> cache = binder(3);
		CardLedger ledger = ledgerOf(cache.subList(0, 2));
		CardRules.Reconciled r = CardRules.reconcile(TestCards.PLAYER, cache, ledger.owned(TestCards.PLAYER), ledger::known);
		assertTrue(r.dropped().isEmpty(), "nothing dropped");
		assertEquals(List.of(cache.get(2).id()), r.adopted().stream().map(CardData::id).toList());
		assertEquals(3, r.cards().size());
	}

	/** P5: one card the codec can't read is kept as it was read and written back unchanged; the others load. */
	@Test
	void oneBadCardInTheLedgerIsKept() {
		CardLedger l = ledgerOf(binder(5));
		CompoundTag root = (CompoundTag) CardLedger.CODEC.encodeStart(NbtOps.INSTANCE, l).getOrThrow();
		ListTag cards = root.getListOrEmpty("cards");
		CompoundTag bad = (CompoundTag) cards.get(2);
		bad.putString("rarity", "legendary");
		long badId = bad.getLongOr("id", -1);
		DataResult<CardLedger> parsed = CardLedger.CODEC.parse(NbtOps.INSTANCE, root);
		assertTrue(parsed.isSuccess(), "the ledger loads");
		CardLedger back = parsed.getOrThrow();
		assertEquals(4, back.size(), "the four readable cards");
		assertEquals(1, back.unreadableCount(), "the bad one kept raw");
		assertTrue(back.known(badId), "its id is never handed out again");
		assertTrue(back.nextId() > badId);
		CompoundTag again = (CompoundTag) CardLedger.CODEC.encodeStart(NbtOps.INSTANCE, back).getOrThrow();
		ListTag out = again.getListOrEmpty("cards");
		assertEquals(5, out.size(), "nothing is dropped on save");
		assertTrue(out.contains(bad), "the unreadable card is written back as it was read");
	}

	/** P4: more than 1000 queued drafts is clamped, never an encode error that would wipe every mod's attachments. */
	@Test
	void queuedOver1000IsClampedNotAnError() {
		ResourceKey<dev.voidmc.mettle.api.Skill> mining = ResourceKey.create(dev.voidmc.mettle.api.MettleRegistries.SKILL,
			Identifier.fromNamespaceAndPath("mettle", "mining"));
		DraftState d = DraftState.fresh(1, 2).withQueued(mining, 1001).addQueued(mining, 50);
		assertEquals(DraftState.MAX_QUEUED, d.queued(mining), "clamped at every change");
		CharacterState s = new CharacterState(Optional.empty(), Map.of(), List.of(), Map.of(), Map.of(), Optional.of(d));
		DataResult<Tag> enc = CharacterState.CODEC.encodeStart(NbtOps.INSTANCE, s);
		assertTrue(enc.isSuccess(), "the record encodes");
		// Fabric's attachment map is Codec.dispatchedMap(...): with every value encodable, the other mods' entries survive.
		@SuppressWarnings({"unchecked", "rawtypes"})
		Codec<Map<String, Object>> map = Codec.dispatchedMap(Codec.STRING, k -> k.equals("character") ? (Codec) CharacterState.CODEC : (Codec) Codec.INT);
		assertTrue(map.encodeStart(NbtOps.INSTANCE, Map.of("character", s, "other", 7)).isSuccess(), "the whole attachment map encodes");

		// An old file holding 5000 (or a rank 9 perk, or an unknown attribute) still loads, clamped.
		CompoundTag tag = (CompoundTag) enc.getOrThrow();
		tag.getCompoundOrEmpty("draft").getCompoundOrEmpty("queued").put("mettle:mining", IntTag.valueOf(5000));
		CompoundTag perks = new CompoundTag();
		perks.putInt("mettle:mining/quarryman", 9);
		tag.put("perks", perks);
		CompoundTag spent = new CompoundTag();
		spent.putInt("luck", 4);
		spent.putInt("wits", 3);
		tag.put("spent", spent);
		CharacterState back = CharacterState.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		assertEquals(DraftState.MAX_QUEUED, back.drafts().queued(mining));
		assertEquals(5, back.perks().values().iterator().next(), "rank clamped to 5");
		assertEquals(1, back.spent().size(), "the unknown attribute is skipped, Wits kept");
	}

	/** P1: the ledger file is written atomically with a backup; a corrupt file loads the backup; both corrupt refuses. */
	@Test
	void ledgerFileSurvivesCorruption() throws Exception {
		Path file = this.dir.resolve("cards.dat");
		Path backup = this.dir.resolve("cards.dat_old");
		CardLedger first = ledgerOf(binder(3));
		LedgerFile.write(first, file);
		CardLedger second = ledgerOf(binder(4));
		LedgerFile.write(second, file);
		assertTrue(Files.exists(backup), "the previous save stays as the backup");
		assertEquals(4, LedgerFile.load(file).size());
		assertEquals(3, LedgerFile.load(backup).size());

		// A truncated cards.dat (a crash mid-write before this fix, a full disk): the backup loads, nothing starts empty.
		byte[] whole = Files.readAllBytes(file);
		Files.write(file, java.util.Arrays.copyOf(whole, whole.length / 2));
		assertEquals(3, LedgerFile.load(file).size(), "the backup, not an empty ledger");
		try (var aside = Files.list(this.dir)) {
			assertTrue(aside.anyMatch(p -> p.getFileName().toString().startsWith("cards.dat.unreadable-")), "the bad file is kept aside");
		}

		// The save was cut between the two renames: only the backup is there.
		Files.delete(file);
		assertEquals(3, LedgerFile.load(file).size());

		// Both unreadable: refuse to start rather than hand out an empty ledger.
		Files.write(file, new byte[] {1, 2, 3});
		Files.write(backup, new byte[] {4, 5, 6});
		IllegalStateException no = assertThrows(IllegalStateException.class, () -> LedgerFile.load(file));
		assertTrue(no.getMessage().contains("won't start"), no.getMessage());

		// Neither file: a new world.
		Path fresh = this.dir.resolve("none").resolve("cards.dat");
		assertEquals(0, LedgerFile.load(fresh).size());
	}

	/** P6: released and fused cards are archived to their ids: smaller files, and the ids are still never reused. */
	@Test
	void releasedCardsAreArchived() {
		CardLedger l = ledgerOf(binder(10));
		long gone = l.owned(TestCards.PLAYER).getFirst().id();
		l.update(gone, c -> c.withPlace(CardData.Place.RELEASED));
		CompoundTag tag = (CompoundTag) CardLedger.CODEC.encodeStart(NbtOps.INSTANCE, l).getOrThrow();
		assertEquals(9, tag.getListOrEmpty("cards").size(), "only the live cards are written in full");
		CardLedger back = CardLedger.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		assertTrue(back.known(gone) && back.released(gone), "the released id is remembered");
		assertEquals(1, back.archivedCount());
		CardRules.Reconciled r = CardRules.reconcile(TestCards.PLAYER, List.of(l.get(gone).orElseThrow().withPlace(CardData.Place.BINDER)),
			back.owned(TestCards.PLAYER), back::known);
		assertEquals(List.of(gone), r.dropped(), "an old player file can't bring a released card back");
	}

	/** P6: save size and time at 30,000 cards (300 a player × 100), before (all kept) and after archiving released cards. */
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
				l.add(new CardData(l.allocate(), c.skill(), c.perk(), c.rarity(), c.rank(), c.keywords(), List.of(t, t, t), c.needs(), c.drawn(),
					owner, h, 0, c.scarSeed(), c.place(), 0L));
			}
		}
		// Best of five each (the machine may be busy). Before: what the audited code did on the server thread (the codec's
		// encode, then gzip at the default level). After: the atomic write (fast encode and gzip, temp file, rename, backup).
		Path file = this.dir.resolve("cards.dat");
		long before = Long.MAX_VALUE;
		long encode = Long.MAX_VALUE;
		long after = Long.MAX_VALUE;
		int beforeKb = 0;
		for (int w = 0; w < 5; w++) {
			long t0 = System.nanoTime();
			CompoundTag root = new CompoundTag();
			root.put("data", CardData.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(l.all())).getOrThrow());
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(root, out);
			long t1 = System.nanoTime();
			LedgerFile.encode(l);
			long t2 = System.nanoTime();
			LedgerFile.write(l, file);
			long t3 = System.nanoTime();
			before = Math.min(before, t1 - t0);
			encode = Math.min(encode, t2 - t1);
			after = Math.min(after, t3 - t2);
			beforeKb = out.size() / 1024;
		}
		System.out.printf("[audit] 30000 cards: before %d ms (%d KB); after: encode %d ms, whole atomic write %d ms (%d KB)%n", before / 1_000_000, beforeKb,
			encode / 1_000_000, after / 1_000_000, Files.size(file) / 1024);
		// A world where two thirds of them were released or fused away over time.
		int n = 0;
		for (CardData c : List.copyOf(l.all())) {
			if (n++ % 3 != 0) {
				l.update(c.id(), x -> x.withPlace(CardData.Place.RELEASED));
			}
		}
		long t3 = System.nanoTime();
		LedgerFile.write(l, file);
		long t4 = System.nanoTime();
		CardLedger back = LedgerFile.load(file);
		long t5 = System.nanoTime();
		System.out.printf("[audit] 30000 cards, 20000 released: write %d ms (%d KB), load %d ms; %d live, %d archived%n", (t4 - t3) / 1_000_000,
			Files.size(file) / 1024, (t5 - t4) / 1_000_000, back.size(), back.archivedCount());
		assertEquals(10_000, back.size());
		assertEquals(20_000, back.archivedCount());

		CardsState cache = CardsState.EMPTY.withCards(back.owned(new UUID(0, 0)));
		assertEquals(100, cache.cards().size());
	}
}
