package dev.voidmc.hearth.build.draft;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/**
 * Block-entity data for blueprint steps, in 26.3's saved format (what {@code BlockEntity.loadWithComponents} reads, the
 * same keys vanilla structure templates store). The applier loads the tag into the block entity after placing the
 * block.
 */
public final class BlockEntities {
	private BlockEntities() {
	}

	/** Sign text (SignBlockEntity: front_text/back_text as SignText, waxed so folk and players do not edit it). */
	public static CompoundTag sign(List<String> front, String color, boolean glowing) {
		CompoundTag t = new CompoundTag();
		t.put("front_text", signText(front, color, glowing));
		t.put("back_text", signText(List.of(), color, false));
		t.putBoolean("is_waxed", true);
		t.putBoolean("allow_op_features", false);
		return t;
	}

	private static CompoundTag signText(List<String> lines, String color, boolean glowing) {
		ListTag messages = new ListTag();
		for (int i = 0; i < 4; i++) {
			CompoundTag line = new CompoundTag();
			line.putString("text", i < lines.size() ? lines.get(i) : "");
			messages.add(line);
		}
		CompoundTag t = new CompoundTag();
		t.put("messages", messages);
		t.putString("color", color);
		t.putBoolean("has_glowing_text", glowing);
		return t;
	}

	/** A lectern holding a written book (LecternBlockEntity: Book as ItemStack, Page). */
	public static CompoundTag lectern(String title, String author, List<String> pages) {
		ListTag ps = new ListTag();
		for (String p : pages) {
			CompoundTag page = new CompoundTag();
			page.putString("raw", p);
			ps.add(page);
		}
		CompoundTag content = new CompoundTag();
		CompoundTag titleTag = new CompoundTag();
		titleTag.putString("raw", title.length() > 32 ? title.substring(0, 32) : title);
		content.put("title", titleTag);
		content.putString("author", author);
		content.put("pages", ps);
		content.putBoolean("resolved", true);
		CompoundTag components = new CompoundTag();
		components.put("minecraft:written_book_content", content);
		CompoundTag book = new CompoundTag();
		book.putString("id", "minecraft:written_book");
		book.putInt("count", 1);
		book.put("components", components);
		CompoundTag t = new CompoundTag();
		t.put("Book", book);
		t.putInt("Page", 0);
		return t;
	}

	/** Banner patterns (BannerBlockEntity: patterns as BannerPatternLayers), each {pattern id, dye colour}. */
	public static CompoundTag banner(List<String[]> patterns) {
		ListTag layers = new ListTag();
		for (String[] p : patterns) {
			CompoundTag layer = new CompoundTag();
			layer.putString("pattern", p[0].contains(":") ? p[0] : "minecraft:" + p[0]);
			layer.putString("color", p[1]);
			layers.add(layer);
		}
		CompoundTag t = new CompoundTag();
		t.put("patterns", layers);
		return t;
	}

	/** A decorated pot's sherds (DecoratedPotBlockEntity: sherds as PotDecorations of item templates). */
	public static CompoundTag pot(String back, String left, String right, String front) {
		CompoundTag sherds = new CompoundTag();
		sherds.put("back", itemTemplate(back));
		sherds.put("left", itemTemplate(left));
		sherds.put("right", itemTemplate(right));
		sherds.put("front", itemTemplate(front));
		CompoundTag t = new CompoundTag();
		t.put("sherds", sherds);
		return t;
	}

	private static CompoundTag itemTemplate(String id) {
		CompoundTag t = new CompoundTag();
		t.putString("id", id.contains(":") ? id : "minecraft:" + id);
		return t;
	}

	/** A container's items (chests, barrels, shelves: Items as slot-tagged ItemStacks). Each entry is {id, count}. */
	public static CompoundTag container(List<Object[]> items) {
		ListTag list = new ListTag();
		for (int i = 0; i < items.size(); i++) {
			Object[] it = items.get(i);
			CompoundTag stack = new CompoundTag();
			String id = (String) it[0];
			stack.putString("id", id.contains(":") ? id : "minecraft:" + id);
			stack.putInt("count", (Integer) it[1]);
			stack.putByte("Slot", (byte) (it.length > 2 ? (Integer) it[2] : i));
			list.add(stack);
		}
		CompoundTag t = new CompoundTag();
		t.put("Items", list);
		return t;
	}

	/** A shelf's display (ShelfBlockEntity: up to three items, slot 0 left). */
	public static CompoundTag shelf(List<Object[]> items) {
		CompoundTag t = container(items);
		t.putBoolean("align_items_to_bottom", false);
		return t;
	}

	/** A single text component as a string tag (for custom names). */
	public static StringTag text(String s) {
		return StringTag.valueOf(s);
	}
}
