package dev.voidmc.hearth.quest.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * One objective of a quest instance, bound to concrete things (this item, that place, this resident) when the quest was
 * generated. Progress is counted on the server only; the client sees it through the synced player data.
 *
 * <p>Kind-specific details live in {@link #data} under the keys in {@link Keys}, so the shape stays stable as kinds are
 * added. A {@link #pos} whose y is {@link #SURFACE_Y} means "the ground there", resolved from the loaded chunk when the
 * player comes near (quests are generated without loading chunks).
 */
public final class Objective {
	/** A position's y to be read from the surface when the chunk is loaded. */
	public static final int SURFACE_Y = Integer.MIN_VALUE + 1;

	public static final Codec<Objective> CODEC = RecordCodecBuilder.create(i -> i.group(
		ObjectiveKind.CODEC.fieldOf("kind").forGetter(o -> o.kind),
		Codec.STRING.optionalFieldOf("label", "").forGetter(o -> o.label),
		Codec.STRING.optionalFieldOf("target", "").forGetter(o -> o.target),
		Codec.INT.optionalFieldOf("count", 1).forGetter(o -> o.count),
		Codec.INT.optionalFieldOf("progress", 0).forGetter(o -> o.progress),
		BlockPos.CODEC.optionalFieldOf("pos").forGetter(o -> Optional.ofNullable(o.pos)),
		Codec.INT.optionalFieldOf("radius", 0).forGetter(o -> o.radius),
		Codec.LONG.optionalFieldOf("settlement", -1L).forGetter(o -> o.settlement),
		UUIDUtil.CODEC.optionalFieldOf("entity").forGetter(o -> Optional.ofNullable(o.entity)),
		CompoundTag.CODEC.optionalFieldOf("data", new CompoundTag()).forGetter(o -> o.data)
	).apply(i, (kind, label, target, count, progress, pos, radius, settlement, entity, data) ->
		new Objective(kind, label, target, count, progress, pos.orElse(null), radius, settlement, entity.orElse(null), data)));

	public final ObjectiveKind kind;
	/** What the journal shows: "Bring 24 spruce logs", "Find the Karst Temple". */
	public String label;
	/** An item, entity, structure or biome id (or {@code #tag}), depending on the kind. */
	public String target;
	public int count;
	public int progress;
	public @Nullable BlockPos pos;
	/** How near counts as there, in blocks (0: the kind's default). */
	public int radius;
	/** A settlement this objective refers to (a courier's destination), or -1. */
	public long settlement;
	/** An entity or resident this objective refers to (the escorted mule, the beast, the friend), or null. */
	public @Nullable UUID entity;
	public CompoundTag data;

	public Objective(ObjectiveKind kind, String label, String target, int count, int progress, @Nullable BlockPos pos, int radius,
		long settlement, @Nullable UUID entity, CompoundTag data) {
		this.kind = kind;
		this.label = label;
		this.target = target;
		this.count = Math.max(1, count);
		this.progress = Math.max(0, Math.min(progress, this.count));
		this.pos = pos;
		this.radius = radius;
		this.settlement = settlement;
		this.entity = entity;
		this.data = data.copy();
	}

	public static Objective of(ObjectiveKind kind, String label, String target, int count) {
		return new Objective(kind, label, target, count, 0, null, 0, -1L, null, new CompoundTag());
	}

	public boolean done() {
		return this.progress >= this.count;
	}

	public int remaining() {
		return Math.max(0, this.count - this.progress);
	}

	/** Adds progress up to the count; returns how much was added. */
	public int advance(int amount) {
		int added = Math.max(0, Math.min(amount, remaining()));
		this.progress += added;
		return added;
	}

	public void complete() {
		this.progress = this.count;
	}

	public Objective copy() {
		return new Objective(this.kind, this.label, this.target, this.count, this.progress, this.pos, this.radius, this.settlement,
			this.entity, this.data);
	}

	/** Keys used in {@link #data}. */
	public static final class Keys {
		/** Supply: the {@code Goods} name the items count as. */
		public static final String GOODS = "goods";
		/** Supply: goods units each item is worth. */
		public static final String UNITS = "units";
		/** Supply: the stalled plot id. */
		public static final String PLOT = "plot";
		/** Kill: only kills at night count. */
		public static final String NIGHT = "night";
		/** Slay/escort: the creature's name. */
		public static final String NAME = "name";
		/** Slay: health multiplier for the beast. */
		public static final String HEALTH = "health";
		/** Slay/escort: the entity has been spawned (its UUID is in {@code entity}). */
		public static final String SPAWNED = "spawned";
		/** Reach/explore/biome: an item granted on arrival: its item id, name and lore. */
		public static final String GRANT_ITEM = "grant_item";
		public static final String GRANT_NAME = "grant_name";
		public static final String GRANT_LORE = "grant_lore";
		/** The granted item must still be carried at turn-in (and is taken then). */
		public static final String GRANT_REQUIRED = "grant_required";
		/** Explore: the structure's bounds, as min and max positions. */
		public static final String BOX_MIN = "box_min";
		public static final String BOX_MAX = "box_max";
		/** Scout: the distance band from the settlement and the clearance from any other settlement. */
		public static final String MIN_DISTANCE = "min_distance";
		public static final String MAX_DISTANCE = "max_distance";
		public static final String CLEARANCE = "clearance";
		/** Scout: where the site was found. */
		public static final String FOUND = "found";
		/** Courier/talk: the recipient's name. */
		public static final String RECIPIENT = "recipient";
		/** A display name for the target (a structure, a biome, a settlement). */
		public static final String TARGET_NAME = "target_name";
		// ---- phase 3
		/** Supply/contract: the workshop job a contract supplies (empty for a stalled build). */
		public static final String SOURCE = "source";
		/** The settlement happening ({@code SettlementQuests.Happening#id}) the objective answers. */
		public static final String HAPPENING = "happening";
		/** Ambush: the band is raised partway along the road from {@code pos} to {@code ROUTE_TO}, at this fraction. */
		public static final String ALONG = "along";
		public static final String ROUTE_TO = "route_to";
		public static final String ROUTE_FROM = "route_from";
		/** Ambush: where the band was raised. */
		public static final String SPRUNG_AT = "sprung_at";
		/** Ambush/apprehend: the creatures have been raised; their UUIDs. */
		public static final String SPRUNG = "sprung";
		public static final String MEMBERS = "members";
		/** Ambush: entity ids to raise, comma-separated (the target holds the same). */
		public static final String ENTITIES = "entities";
		/** Ambush/apprehend: only raised after dark. */
		public static final String AT_NIGHT = "at_night";
		/** Apprehend: the culprit was taken alive (true) or killed (false); absent while at large. */
		public static final String ALIVE = "alive";
		/** Apprehend: killing the culprit also counts. */
		public static final String LETHAL = "lethal";
		/** Ambush/escort: killing these creatures is this crime ({@code LawService.Offence} name), reported to the law. */
		public static final String CRIME = "crime";
		/** Ambush/escort: what they carry, in bits (sets the fine when it is a crime). */
		public static final String VALUE = "value";
		/** Escort: pack animals in the caravan (1 for a lone mule). */
		public static final String CARAVAN = "caravan";
		/** Escort/ambush: the pack animals carry cargo chests of this goods name. */
		public static final String CARGO = "cargo";
		/** Rescue: checks to stay at the place; checks stayed so far. */
		public static final String HOLD = "hold";
		public static final String HELD = "held";
		/** Extinguish: the most fires seen burning at once near the place. */
		public static final String FIRES = "fires";
		/** Prospect: the resource kind ({@code ResourceSite.Kind} name). */
		public static final String RESOURCE = "resource";
		/** Hunt: the hunted player's name. */
		public static final String QUARRY = "quarry";

		private Keys() {
		}
	}
}
