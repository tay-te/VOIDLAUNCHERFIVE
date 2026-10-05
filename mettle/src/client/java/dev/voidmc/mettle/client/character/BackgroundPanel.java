package dev.voidmc.mettle.client.character;

import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.client.ui.kit.Ui;
import dev.voidmc.mettle.client.ui.kit.UiCanvas;
import dev.voidmc.mettle.client.ui.kit.UiCanvas.Weight;
import dev.voidmc.mettle.core.EffectsApplier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

/**
 * The fixed side panel that describes one background (Figma 1b / 1c, page 264:2): its scene, attributes, favoured
 * skills, signature perk, kit and the Begin button. For a background that lets the player place points and pick skills
 * (the Wanderer) the attribute meters and skill chips are editable. Layout numbers are Figma pixels.
 */
final class BackgroundPanel {
	static final int W = 336;
	static final int H = 882;
	private static final int PAD = 24;
	private static final int INNER = 288;
	private static final int METER_X = 76;
	private static final int CELL = 8;
	private static final int CELL_STEP = 10;

	record Rect(int x, int y, int w, int h) {
		boolean contains(double px, double py) {
			return px >= this.x && py >= this.y && px < this.x + this.w && py < this.y + this.h;
		}
	}

	final ResourceKey<Background> key;
	final Background background;
	final List<Holder.Reference<Skill>> skills;
	/** Points placed by the player (Wanderer only). */
	final EnumMap<CharacterAttribute, Integer> placed = new EnumMap<>(CharacterAttribute.class);
	/** Skills picked to favour, in the order picked (Wanderer only). */
	final List<ResourceKey<Skill>> picked = new ArrayList<>();

	// Hit areas from the last frame, in screen pixels.
	@Nullable Rect button;
	final Map<CharacterAttribute, Rect> meters = new EnumMap<>(CharacterAttribute.class);
	final Map<ResourceKey<Skill>, Rect> chips = new LinkedHashMap<>();
	int cellStepPx = 1;

	BackgroundPanel(ResourceKey<Background> key, Background background, List<Holder.Reference<Skill>> skills) {
		this.key = key;
		this.background = background;
		this.skills = skills;
	}

	boolean editable() {
		return this.background.freePoints() > 0 || this.background.freeFavoured() > 0;
	}

	int pointsLeft() {
		return this.background.freePoints() - this.placed.values().stream().mapToInt(Integer::intValue).sum();
	}

	int picksLeft() {
		return this.background.freeFavoured() - this.picked.size();
	}

	boolean ready() {
		return pointsLeft() == 0 && picksLeft() == 0;
	}

	int value(CharacterAttribute a) {
		return CharacterAttribute.BASE + this.background.bonus(a) + this.placed.getOrDefault(a, 0);
	}

	/** Sets an attribute (Wanderer) to the meter cell under the mouse, within the points left. */
	void placeAt(CharacterAttribute a, double mouseX) {
		Rect m = this.meters.get(a);
		if (m == null || this.background.freePoints() == 0) {
			return;
		}
		int cell = (int) Math.floor((mouseX - m.x()) / this.cellStepPx) + 1;
		int base = CharacterAttribute.BASE + this.background.bonus(a);
		int current = this.placed.getOrDefault(a, 0);
		int max = base + current + pointsLeft();
		int target = Math.clamp(cell, base, Math.min(max, CharacterAttribute.MAX));
		if (target - base == 0) {
			this.placed.remove(a);
		} else {
			this.placed.put(a, target - base);
		}
	}

	/** Picks or unpicks a skill to favour (Wanderer); a third pick replaces the oldest. */
	void togglePick(ResourceKey<Skill> skill) {
		if (this.picked.remove(skill)) {
			return;
		}
		if (this.picked.size() >= this.background.freeFavoured()) {
			this.picked.removeFirst();
		}
		this.picked.add(skill);
	}

	// ---------------------------------------------------------------- drawing

	/** The picker's side panel. Returns nothing; hit areas are kept for the screen's mouse handling. */
	void drawPreview(UiCanvas c, int x, int y, double mx, double my, boolean waiting) {
		this.meters.clear();
		this.chips.clear();
		this.cellStepPx = c.px(CELL_STEP);
		c.card(x, y, c.px(W), c.px(H), c.px(24), Ui.BASE, Ui.white(0.08f), 1);
		int ix = x + c.px(PAD);
		header(c, ix, y);

		if (this.background.freePoints() > 0) {
			drawPlaceRows(c, ix, y, mx, my);
		} else {
			c.tracked(Component.translatable("mettle.picker.label.attributes").getString(), Weight.MEDIUM, 10, 0.12f, ix, y + c.px(268), Ui.TEXT_2);
			attributeRows(c, ix, y + c.px(292), a -> CharacterAttribute.BASE + this.background.bonus(a), a -> this.background.bonus(a), false);
		}

		int below;
		if (this.background.freeFavoured() > 0) {
			below = drawPickChips(c, ix, y, mx, my);
		} else {
			c.tracked(Component.translatable("mettle.picker.label.favoured").getString(), Weight.MEDIUM, 10, 0.12f, ix, y + c.px(428), Ui.TEXT_2);
			favouredChips(c, ix, y + c.px(452), this.background.favoured());
			below = y + c.px(516);
		}

		if (this.background.freeFavoured() > 0) {
			// Wanderer: one-line signature row, then a compact kit row.
			c.tracked(Component.translatable("mettle.picker.label.signature").getString(), Weight.MEDIUM, 10, 0.12f, ix, y + c.px(710), Ui.TEXT_2);
			c.textRight(Component.translatable("mettle.picker.no_signature"), Weight.LIGHT, 13, ix + c.px(INNER), y + c.px(707), Ui.TEXT_2);
			c.tracked(Component.translatable("mettle.picker.label.kit").getString(), Weight.MEDIUM, 10, 0.12f, ix, y + c.px(740), Ui.TEXT_2);
			int after = kit(c, ix, y + c.px(760));
			int room = ix + c.px(INNER) - after - c.px(10);
			c.text(this.background.kitSummary(), Weight.REGULAR, c.fit(this.background.kitSummary(), Weight.REGULAR, 12, room), after + c.px(10), y + c.px(779), Ui.TEXT_3);
		} else {
			signature(c, ix, below);
			c.tracked(Component.translatable("mettle.picker.label.kit").getString(), Weight.MEDIUM, 10, 0.12f, ix, y + c.px(610), Ui.TEXT_2);
			kit(c, ix, y + c.px(634));
			c.text(this.background.kitSummary(), Weight.REGULAR, 12, ix, y + c.px(696), Ui.TEXT_3);
			carry(c, ix, y + c.px(744), value(CharacterAttribute.STRENGTH));
		}

		// Begin
		int by = y + c.px(this.editable() ? 834 : 810);
		int bh = c.px(this.editable() ? 44 : 48);
		Rect b = new Rect(ix, by, c.px(INNER), bh);
		boolean enabled = ready() && !waiting;
		boolean hover = enabled && b.contains(mx, my);
		c.round(b.x(), b.y(), b.w(), b.h(), c.px(9), enabled ? (hover ? 0xFFFFFFFF : Ui.TEXT) : Ui.white(0.06f));
		Component label = waiting ? Component.translatable("mettle.picker.waiting") : buttonLabel();
		c.textCentred(label, Weight.MEDIUM, 15, b.x() + b.w() / 2, b.y() + (b.h() - c.px(19)) / 2, enabled ? Ui.SHELL : Ui.TEXT_3);
		this.button = enabled ? b : null;
	}

	/** The K screen's view of the player's own background: no kit, no button. */
	void drawOwned(UiCanvas c, int x, int y, int height, ToIntFunction<CharacterAttribute> values, List<ResourceKey<Skill>> favoured) {
		c.card(x, y, c.px(W), height, c.px(24), Ui.BASE, Ui.white(0.08f), 1);
		int ix = x + c.px(PAD);
		header(c, ix, y);
		c.tracked(Component.translatable("mettle.character.label.attributes").getString(), Weight.MEDIUM, 10, 0.12f, ix, y + c.px(268), Ui.TEXT_2);
		attributeRows(c, ix, y + c.px(292), values, a -> values.applyAsInt(a) - CharacterAttribute.BASE, true);
		c.tracked(Component.translatable("mettle.picker.label.favoured").getString(), Weight.MEDIUM, 10, 0.12f, ix, y + c.px(428), Ui.TEXT_2);
		favouredChips(c, ix, y + c.px(452), favoured);
		int below = y + c.px(516);
		if (this.background.signature().isPresent()) {
			signature(c, ix, below);
			below = y + c.px(610);
		}
		carry(c, ix, below, values.applyAsInt(CharacterAttribute.STRENGTH));
	}

	static int ownedHeight(Background background) {
		return background.signature().isPresent() ? 690 : 596;
	}

	private void header(UiCanvas c, int ix, int y) {
		int artW = c.px(INNER);
		int artH = c.px(144);
		int artY = y + c.px(PAD);
		c.sprite(Background.art(this.key), ix, artY, artW, artH, 48, 24, 0xFFFFFFFF);
		c.roundOff(ix, artY, artW, artH, c.px(10), Ui.BASE, true, true);
		c.tracked(this.background.name().getString(), Weight.REGULAR, 30, -0.02f, ix, y + c.px(186), Ui.TEXT);
		c.text(this.background.tagline(), Weight.LIGHT, 13, ix, y + c.px(226), Ui.TEXT_2);
	}

	/** Five rows: name, bonus, 20-cell meter, value. Values brighter where something was added. */
	private void attributeRows(UiCanvas c, int ix, int top, ToIntFunction<CharacterAttribute> values, ToIntFunction<CharacterAttribute> bonuses, boolean owned) {
		int row = 0;
		for (CharacterAttribute a : CharacterAttribute.values()) {
			int ry = top + c.px(24 * row++);
			int value = values.applyAsInt(a);
			int bonus = bonuses.applyAsInt(a);
			int nameW = c.text(a.displayName(), Weight.REGULAR, 13, ix, ry, Ui.TEXT_2);
			if (bonus != 0) {
				c.text((bonus > 0 ? "+" : "") + bonus, Weight.MEDIUM, 11, ix + nameW + c.px(6), ry + c.px(1), Ui.TEXT_3);
			}
			meter(c, ix + c.px(METER_X), ry + c.px(4), value, false);
			c.textRight(Component.literal(Integer.toString(value)), Weight.MEDIUM, 13, ix + c.px(INNER), ry, bonus != 0 || owned ? Ui.TEXT : Ui.TEXT_2);
		}
	}

	private void drawPlaceRows(UiCanvas c, int ix, int y, double mx, double my) {
		c.tracked(Component.translatable("mettle.picker.label.place", this.background.freePoints()).getString(), Weight.MEDIUM, 10, 0.12f, ix, y + c.px(268), Ui.TEXT_2);
		counter(c, ix + c.px(INNER), y + c.px(264), Component.translatable("mettle.picker.points_left", pointsLeft()));
		int row = 0;
		for (CharacterAttribute a : CharacterAttribute.values()) {
			int ry = y + c.px(296 + 24 * row++);
			int placedHere = this.placed.getOrDefault(a, 0);
			Rect meter = new Rect(ix + c.px(METER_X) - c.px(4), ry - c.px(2), c.px(20 * CELL_STEP) + c.px(8), c.px(20));
			boolean hot = meter.contains(mx, my);
			int nameW = c.text(a.displayName(), Weight.REGULAR, 13, ix, ry, hot || placedHere > 0 ? Ui.TEXT : Ui.TEXT_2);
			if (placedHere > 0) {
				c.text("+" + placedHere, Weight.MEDIUM, 11, ix + nameW + c.px(6), ry + c.px(1), Ui.TEXT_3);
			}
			int mxPx = ix + c.px(METER_X);
			meter(c, mxPx, ry + c.px(4), value(a), hot);
			// Where the base ends: points placed are to its right.
			int base = CharacterAttribute.BASE + this.background.bonus(a);
			c.fill(mxPx + base * c.px(CELL_STEP) - c.px(1), ry + c.px(2), Math.max(1, c.px(1)), c.px(12), Ui.white(0.10f));
			c.textRight(Component.literal(Integer.toString(value(a))), Weight.MEDIUM, 13, ix + c.px(INNER), ry, placedHere > 0 ? Ui.TEXT : Ui.TEXT_2);
			this.meters.put(a, new Rect(mxPx, meter.y(), c.px(20 * CELL_STEP), meter.h()));
		}
		Component note = Component.translatable("mettle.picker.place_note");
		c.text(note, Weight.REGULAR, c.fit(note, Weight.REGULAR, 12, c.px(INNER)), ix, y + c.px(418), Ui.TEXT_3);
	}

	private void meter(UiCanvas c, int x, int y, int value, boolean hot) {
		int r = Math.max(1, c.px(2.4));
		for (int i = 0; i < 20; i++) {
			int colour;
			if (i + 1 == value) {
				colour = Ui.alpha(Ui.ACCENT, 0.95f);
			} else if (i < value) {
				colour = Ui.alpha(Ui.TEXT, hot ? 0.48f : 0.38f);
			} else {
				colour = Ui.alpha(Ui.TEXT, hot ? 0.11f : 0.07f);
			}
			c.round(x + i * c.px(CELL_STEP), y, c.px(CELL), c.px(CELL), r, colour);
		}
	}

	private void counter(UiCanvas c, int right, int top, Component text) {
		int w = c.px(74);
		c.card(right - w, top, w, c.px(20), c.px(8), Ui.CARD, Ui.white(0.06f), 1);
		c.textCentred(text, Weight.MEDIUM, 11, right - w / 2, top + c.px(3), Ui.TEXT_2);
	}

	/** The favoured-skill chips: 2× pixel icon and name. */
	private void favouredChips(UiCanvas c, int ix, int top, List<ResourceKey<Skill>> favoured) {
		int cx = ix;
		int k = iconScale(c);
		for (ResourceKey<Skill> key : favoured) {
			Skill skill = skill(key);
			if (skill == null) {
				continue;
			}
			int textW = c.width(skill.name(), Weight.MEDIUM, 13);
			int w = c.px(42) + textW + c.px(14);
			int h = c.px(40);
			c.card(cx, top, w, h, c.px(8), Ui.CARD, Ui.white(0.06f), 1);
			int slot = c.px(32);
			c.sprite(Skill.icon(key), cx + c.px(4) + (slot - 16 * k) / 2, top + c.px(4) + (slot - 16 * k) / 2, 16 * k, 16 * k, 16, 16, 0xFFFFFFFF);
			c.text(skill.name(), Weight.MEDIUM, 13, cx + c.px(42), top + c.px(11), Ui.TEXT);
			cx += w + c.px(8);
		}
	}

	/** The Wanderer's pick-two grid of released skills, then the dimmed row of coming ones. Returns the y below. */
	private int drawPickChips(UiCanvas c, int ix, int y, double mx, double my) {
		c.tracked(Component.translatable("mettle.picker.label.pick").getString(), Weight.MEDIUM, 10, 0.12f, ix, y + c.px(446), Ui.TEXT_2);
		counter(c, ix + c.px(INNER), y + c.px(442), Component.translatable("mettle.picker.picked", this.picked.size(), this.background.freeFavoured()));
		int k = iconScale(c);
		int slot = c.px(32);
		int i = 0;
		List<Holder.Reference<Skill>> coming = new ArrayList<>();
		for (Holder.Reference<Skill> ref : this.skills) {
			if (!ref.value().released()) {
				coming.add(ref);
				continue;
			}
			int col = i % 2;
			int rowN = i / 2;
			i++;
			Rect r = new Rect(ix + c.px(147 * col), y + c.px(468 + 42 * rowN), c.px(141), c.px(36));
			boolean selected = this.picked.contains(ref.key());
			boolean hover = r.contains(mx, my);
			c.round(r.x(), r.y(), r.w(), r.h(), c.px(8), selected ? Ui.RAISED : hover ? Ui.CARD_HOVER : Ui.CARD);
			c.stroke(r.x(), r.y(), r.w(), r.h(), c.px(8), selected ? Math.max(1, c.px(1.5)) : 1, selected ? Ui.ACCENT : Ui.white(hover ? 0.10f : 0.06f));
			c.sprite(Skill.icon(ref.key()), r.x() + c.px(2) + (slot - 16 * k) / 2, r.y() + c.px(2) + (slot - 16 * k) / 2, 16 * k, 16 * k, 16, 16, 0xFFFFFFFF);
			int room = r.w() - c.px(42) - c.px(selected ? 22 : 8);
			float size = c.fit(ref.value().name(), Weight.MEDIUM, 13, room);
			c.text(ref.value().name(), Weight.MEDIUM, size, r.x() + c.px(42), r.y() + c.px(9) + c.px((13 - size) / 2), selected ? Ui.TEXT : Ui.TEXT_CHIP);
			if (selected) {
				int d = c.px(6);
				c.round(r.x() + c.px(125), r.y() + c.px(15), d, d, d / 2, Ui.alpha(Ui.ACCENT, 0.95f));
			}
			this.chips.put(ref.key(), r);
		}
		int ty = y + c.px(638);
		for (int t = 0; t < coming.size(); t++) {
			int tx = ix + c.px(42 * t);
			c.card(tx, ty, c.px(36), c.px(36), c.px(8), Ui.TILE, Ui.white(0.04f), 1);
			c.sprite(Skill.icon(coming.get(t).key()), tx + c.px(2) + (slot - 16 * k) / 2, ty + c.px(2) + (slot - 16 * k) / 2, 16 * k, 16 * k, 16, 16, Ui.white(0.30f));
		}
		Component note = Component.translatable("mettle.picker.coming_note");
		c.text(note, Weight.REGULAR, c.fit(note, Weight.REGULAR, 12, c.px(INNER)), ix, y + c.px(680), Ui.TEXT_3);
		return y + c.px(710);
	}

	private void signature(UiCanvas c, int ix, int top) {
		this.background.signature().ifPresent(sig -> {
			c.tracked(Component.translatable("mettle.picker.label.signature").getString(), Weight.MEDIUM, 10, 0.12f, ix, top, Ui.TEXT_2);
			c.text(sig.name(), Weight.MEDIUM, 15, ix, top + c.px(24), Ui.TEXT);
			c.wrapped(sig.description(), Weight.LIGHT, 13, ix, top + c.px(47), c.px(INNER), Ui.TEXT_2);
		});
	}

	/** The kit as item slots; returns the x right of the last slot. */
	private int kit(UiCanvas c, int ix, int top) {
		int s = c.px(54);
		int x = ix;
		for (ItemStackTemplate template : this.background.kit()) {
			if (x + s > ix + c.px(INNER)) {
				break;
			}
			c.card(x, top, s, s, c.px(9), Ui.WELL, Ui.white(0.05f), 1);
			ItemStack stack = template.create();
			c.item(stack, x + c.px(3), top + c.px(3), c.px(48), true);
			x += s + c.px(8);
		}
		return x - c.px(8);
	}

	private void carry(UiCanvas c, int ix, int top, int strength) {
		c.tracked(Component.translatable("mettle.picker.label.carry").getString(), Weight.MEDIUM, 10, 0.12f, ix, top, Ui.TEXT_2);
		int fromStrength = (int) (strength * EffectsApplier.CAPACITY_PER_STRENGTH);
		c.text(Component.translatable("mettle.picker.carry", 40 + fromStrength, fromStrength, strength), Weight.REGULAR, 13, ix, top + c.px(20), Ui.TEXT_2);
	}

	private Component buttonLabel() {
		if (pointsLeft() > 0) {
			return pointsLeft() == 1 ? Component.translatable("mettle.picker.place_one_more") : Component.translatable("mettle.picker.place_more", pointsLeft());
		}
		if (picksLeft() > 0) {
			return picksLeft() == 1 ? Component.translatable("mettle.picker.pick_one_more") : Component.translatable("mettle.picker.pick_more", picksLeft());
		}
		return Component.translatable("mettle.picker.begin", this.background.name());
	}

	private @Nullable Skill skill(ResourceKey<Skill> key) {
		for (Holder.Reference<Skill> ref : this.skills) {
			if (ref.key().equals(key)) {
				return ref.value();
			}
		}
		return null;
	}

	/** Skill icons are 16 px pixel art; draw them at the largest whole multiple that fits their 32-px Figma slot. */
	private static int iconScale(UiCanvas c) {
		return Math.max(1, (int) Math.floor(2 * c.u + 1e-3));
	}

	static Map<CharacterAttribute, Integer> copy(EnumMap<CharacterAttribute, Integer> placed) {
		return new EnumMap<>(placed);
	}
}
