package dev.voidmc.hearth.social.events;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.UUIDUtil;

/**
 * How an event came out (docs/LIVING_WORLD_2.md §3.2): decided by {@link Outcomes} from the event's facts and seed, and
 * the players' interventions if any. The same record whether the town was abstract or embodied; the books keep the last
 * sixteen ({@link EventBook#recent}).
 *
 * @param result     a word for what happened: {@code guilty}, {@code acquitted}, {@code repelled}, {@code sacked}, {@code burnt},
 *                   {@code paid}, {@code won}, {@code held}...
 * @param coin       bits moved out of the town's treasury (positive) or into it (negative)
 * @param goods      goods lost from the stores (raids, the tax pig), by {@link dev.voidmc.hearth.api.Goods#ordinal()}
 * @param deaths     residents who die of it (an execution, a raid's rare death)
 * @param plot       the plot burnt, or -1
 * @param burned     share of that plot's blocks burnt, 0–1
 * @param winner     the winner's name (a contest), or ""
 * @param sentence   a trial's sentence ({@code fine}, {@code pillory}, {@code cell}, {@code gallows}), or ""
 * @param intervened whether players changed it
 */
public record Outcome(int event, EventKind kind, long day, String result, long coin, int[] goods, List<UUID> deaths, int plot, double burned,
	String winner, String sentence, boolean intervened) {

	public static final Codec<Outcome> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("event").forGetter(Outcome::event),
		EventKind.CODEC.fieldOf("kind").forGetter(Outcome::kind),
		Codec.LONG.fieldOf("day").forGetter(Outcome::day),
		Codec.STRING.optionalFieldOf("result", "").forGetter(Outcome::result),
		Codec.LONG.optionalFieldOf("coin", 0L).forGetter(Outcome::coin),
		Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of).optionalFieldOf("goods", new int[0]).forGetter(Outcome::goods),
		UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("deaths", List.of()).forGetter(Outcome::deaths),
		Codec.INT.optionalFieldOf("plot", -1).forGetter(Outcome::plot),
		Codec.DOUBLE.optionalFieldOf("burned", 0.0).forGetter(Outcome::burned),
		Codec.STRING.optionalFieldOf("winner", "").forGetter(Outcome::winner),
		Codec.STRING.optionalFieldOf("sentence", "").forGetter(Outcome::sentence),
		Codec.BOOL.optionalFieldOf("intervened", false).forGetter(Outcome::intervened)
	).apply(i, Outcome::new));

	/** Nothing to it beyond the gathering (a christening, a preacher). */
	public static Outcome held(Scheduled e) {
		return new Outcome(e.id(), e.kind(), e.day(), "held", 0, new int[0], List.of(), -1, 0, "", "", false);
	}

	public int goodsLost() {
		int n = 0;
		for (int g : this.goods) {
			n += g;
		}
		return n;
	}

	/** Same outcome, field by field (records compare arrays by identity). */
	public boolean sameAs(Outcome o) {
		return this.event == o.event && this.kind == o.kind && this.day == o.day && this.result.equals(o.result) && this.coin == o.coin
			&& java.util.Arrays.equals(this.goods, o.goods) && this.deaths.equals(o.deaths) && this.plot == o.plot && this.burned == o.burned
			&& this.winner.equals(o.winner) && this.sentence.equals(o.sentence) && this.intervened == o.intervened;
	}
}
