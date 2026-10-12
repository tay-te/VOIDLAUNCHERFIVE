package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.core.CardData;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/**
 * A card written straight to NBT, the same tag {@link CardData#CODEC} writes (CardNbtTest checks they are equal), about
 * ten times faster: the ledger is saved often (every player save, every trade), and at tens of thousands of cards the
 * codec's encode was most of the time. Reading still goes through the codec.
 */
final class CardNbt {
	private CardNbt() {
	}

	static CompoundTag write(CardData c) {
		CompoundTag t = new CompoundTag();
		t.putLong("id", c.id());
		t.putString("skill", c.skill().identifier().toString());
		t.putString("perk", c.perk().identifier().toString());
		t.putString("rarity", c.rarity().getSerializedName());
		if (c.rank() != 1) {
			t.putInt("rank", c.rank());
		}
		if (!c.keywords().isEmpty()) {
			ListTag k = new ListTag();
			c.keywords().forEach(id -> k.add(StringTag.valueOf(id.toString())));
			t.put("keywords", k);
		}
		if (!c.traits().isEmpty()) {
			ListTag l = new ListTag();
			for (CardData.TraitRoll r : c.traits()) {
				CompoundTag o = new CompoundTag();
				o.putString("trait", r.trait().toString());
				o.putDouble("value", r.value());
				l.add(o);
			}
			t.put("traits", l);
		}
		if (c.needs() != 0) {
			t.putInt("needs", c.needs());
		}
		CompoundTag drawn = new CompoundTag();
		drawn.put("player", uuid(c.drawn().player()));
		drawn.putString("name", c.drawn().name());
		drawn.putInt("level", c.drawn().level());
		drawn.putLong("day", c.drawn().day());
		drawn.putLong("millis", c.drawn().millis());
		drawn.putString("how", c.drawn().how().getSerializedName());
		t.put("drawn", drawn);
		t.put("owner", uuid(c.owner()));
		CompoundTag history = new CompoundTag();
		history.putInt("owners", c.history().owners());
		if (!c.history().last().isEmpty()) {
			ListTag last = new ListTag();
			for (CardData.Holder h : c.history().last()) {
				last.add(holder(h));
			}
			history.put("last", last);
		}
		t.put("history", history);
		if (c.scars() != 0) {
			t.putInt("scars", c.scars());
		}
		if (c.scarSeed() != 0L) {
			t.putLong("scar_seed", c.scarSeed());
		}
		if (!c.place().equals(CardData.Place.BINDER)) {
			CompoundTag p = new CompoundTag();
			p.putString("kind", c.place().kind().getSerializedName());
			c.place().deck().ifPresent(d -> p.putString("deck", d.identifier().toString()));
			if (c.place().index() != 0) {
				p.putInt("index", c.place().index());
			}
			if (c.place().ref() != 0L) {
				p.putLong("ref", c.place().ref());
			}
			t.put("place", p);
		}
		if (c.attunedAt() != 0L) {
			t.putLong("attuned_at", c.attunedAt());
		}
		return t;
	}

	static CompoundTag holder(CardData.Holder h) {
		CompoundTag o = new CompoundTag();
		o.put("player", uuid(h.player()));
		o.putString("name", h.name());
		o.putLong("day", h.day());
		return o;
	}

	private static IntArrayTag uuid(UUID id) {
		return new IntArrayTag(UUIDUtil.uuidToIntArray(id));
	}
}
