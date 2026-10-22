package dev.voidmc.fray.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.fray.combat.action.ActionClips;
import dev.voidmc.fray.combat.action.ActionKind;
import dev.voidmc.fray.combat.action.ActionTiming;
import dev.voidmc.fray.combat.action.Movesets;
import dev.voidmc.fray.combat.action.WeaponFamily;
import dev.voidmc.fray.combat.math.Phases;
import dev.voidmc.fray.motion.DefenderTiming;
import dev.voidmc.fray.motion.DefenderTiming.Window;
import dev.voidmc.fray.motion.Timeline;
import dev.voidmc.fray.motion.bake.MotionBake;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Combat on the animation contract (ANIM_EVENTS.md): the defender-favoured numbers combat uses are DefenderTiming's,
 * the windows its dodges and parries are judged by are the clips' (with Mettle's and the parry's overrides), and the
 * actions' phases and windows come from the baked timelines.
 */
class DefenderGraceTest {
	private static Map<Identifier, MotionBake.Baked> clips;

	@BeforeAll
	static void bake() throws Exception {
		clips = MotionBake.bakeAll(Path.of("../src/main/motion"), Path.of("../src/main/blockbench/rig"), null).clips();
	}

	private static Timeline t(String path) {
		MotionBake.Baked b = clips.get(Identifier.fromNamespaceAndPath("bestiary", path));
		assertNotNull(b, path);
		return b.timeline();
	}

	@Test
	void graceIsTheRoundTripPlusATickCapped() {
		assertEquals(2, DefenderTiming.grace(50, 5), "50 ms: 1 tick + 1");
		assertEquals(4, DefenderTiming.grace(150, 5), "150 ms: 3 ticks + 1");
		assertEquals(5, DefenderTiming.grace(250, 5), "250 ms: capped at 5 ticks");
		assertEquals(2, DefenderTiming.grace(250, DefenderTiming.PVP_CAP_TICKS), "against players: 100 ms");
		assertEquals(0, DefenderTiming.grace(250, 0), "0 turns it off");
	}

	@Test
	void stampsAreClamped() {
		long receipt = 1000;
		assertEquals(997, DefenderTiming.clamp(997, receipt, 150, 5, Long.MIN_VALUE), "in time: the client's tick");
		assertEquals(996, DefenderTiming.clamp(900, receipt, 150, 5, Long.MIN_VALUE), "older than the grace: clamped");
		assertEquals(1000, DefenderTiming.clamp(1010, receipt, 150, 5, Long.MIN_VALUE), "future stamps: the receipt");
		assertEquals(998, DefenderTiming.clamp(997, receipt, 150, 5, 998), "never before the previous stamp");
	}

	@Test
	void dodgeWindowsAreTheClipsAndMettleWidensThem() {
		Timeline roll = t("player/dodge_forward");
		assertEquals(Window.NONE, DefenderTiming.windowAt(roll, 100, 100));
		assertEquals(Window.PERFECT, DefenderTiming.windowAt(roll, 100, 101), "perfect 1-3");
		assertEquals(Window.PERFECT, DefenderTiming.windowAt(roll, 100, 103));
		assertEquals(Window.IFRAMES, DefenderTiming.windowAt(roll, 100, 107), "i-frames 1-7");
		assertEquals(Window.NONE, DefenderTiming.windowAt(roll, 100, 108));
		Map<String, int[]> wider = Map.of("iframes", new int[] {1, 9});
		assertEquals(Window.IFRAMES, DefenderTiming.windowAt(roll, 100, 109, wider), "Mettle's +2");
		assertEquals(Window.PERFECT, DefenderTiming.windowAt(roll, 100, 102, wider), "the perfect ticks stay the clip's");
		Timeline side = t("player/dodge_left");
		assertEquals(Window.IFRAMES, DefenderTiming.windowAt(side, 100, 106));
		Timeline back = t("player/backstep");
		assertEquals(Window.IFRAMES, DefenderTiming.windowAt(back, 100, 104));
		assertEquals(Window.NONE, DefenderTiming.windowAt(back, 100, 105), "backstep i-frames 1-4");
	}

	@Test
	void parryWindowIsThePressesOwn() {
		Timeline parry = t("player/parry");
		assertEquals(Window.PARRY, DefenderTiming.windowAt(parry, 50, 54), "parry 0-4 as authored");
		assertEquals(Window.NONE, DefenderTiming.windowAt(parry, 50, 55));
		assertEquals(Window.NONE, DefenderTiming.windowAt(parry, 50, 53, Map.of("parry", new int[] {0, 1})), "spammed: halved");
		assertEquals(Window.NONE, DefenderTiming.windowAt(parry, 50, 50, Map.of("parry", new int[] {0, -1})), "Winded: none");
		assertEquals(Window.PARRY, DefenderTiming.windowAt(parry, 50, 56, Map.of("parry", new int[] {0, 6})), "Mettle's bonus");
	}

	private static ActionTiming timing(ActionKind kind, WeaponFamily family, Phases def, int variant) {
		Identifier clip = ActionClips.pick(kind, variant, id -> clips.containsKey(id));
		return ActionTiming.of(kind, family, def, clip, clip == null ? null : clips.get(clip).timeline(), 1);
	}

	@Test
	void swordActionsTakeTheirTimingFromTheirClips() {
		var sword = Movesets.of(WeaponFamily.SWORD);
		ActionTiming l1 = timing(ActionKind.LIGHT, WeaponFamily.SWORD, sword.light(0).phases(), 0);
		assertTrue(l1.fromClip());
		assertEquals(Identifier.fromNamespaceAndPath("bestiary", "player/sword/light_1"), l1.clip());
		assertEquals(new Phases(4, 2, 6), l1.phases(), "light_1 4/2/6");
		assertFalse(l1.chainOpen(5));
		assertTrue(l1.chainOpen(6), "chain 6-11");
		assertTrue(l1.dodgeCancel(8) && !l1.dodgeCancel(7), "dodge_cancel 8-11");
		ActionTiming l3 = timing(ActionKind.LIGHT, WeaponFamily.SWORD, sword.light(2).phases(), 2);
		assertEquals(new Phases(6, 3, 9), l3.phases(), "light_3 6/3/9");
		ActionTiming heavy = timing(ActionKind.HEAVY, WeaponFamily.SWORD, sword.heavy().phases(), 0);
		assertEquals(new Phases(12, 3, 12), heavy.phases(), "heavy 12/3/12");
		assertTrue(heavy.feint(9) && !heavy.feint(10), "feint 0-9");
		assertTrue(heavy.hyperArmour(14) && !heavy.hyperArmour(15), "hyper_armour 0-14");
		assertTrue(heavy.chainOpen(15) && heavy.dodgeCancel(18) && !heavy.dodgeCancel(17));
		ActionTiming charged = timing(ActionKind.CHARGED, WeaponFamily.SWORD, sword.heavy().phases(), 0);
		assertEquals(new Phases(6, 3, 15), charged.phases(), "charge_release 6/3/15, not the heavy's");
		assertTrue(charged.hyperArmour(8) && !charged.hyperArmour(9));
		ActionTiming plunge = timing(ActionKind.PLUNGE, WeaponFamily.SWORD, sword.light(0).phases(), 0);
		assertEquals(new Phases(4, 3, 9), plunge.phases(), "plunge 4/3/9");
		ActionTiming roll = timing(ActionKind.DODGE, WeaponFamily.SWORD, Movesets.ROLL.phases(), 1);
		assertEquals(12, roll.total());
		assertTrue(roll.chainOpen(9) && !roll.chainOpen(8), "roll_attack 9-11");
	}

	@Test
	void borrowedClipsAreRetimedToTheMoveset() {
		var dagger = Movesets.of(WeaponFamily.DAGGER);
		ActionTiming t = timing(ActionKind.LIGHT, WeaponFamily.DAGGER, dagger.light(0).phases(), 0);
		assertFalse(t.fromClip());
		assertEquals(dagger.light(0).phases(), t.phases(), "the dagger keeps its own 2/2/5");
		assertEquals(2.0F, t.clipSpeed(), 1e-4, "light_1's strike at tick 4 lands on the dagger's tick 2");
		ActionTiming exec = timing(ActionKind.EXECUTE, WeaponFamily.SWORD, Movesets.EXECUTE.phases(), 0);
		assertEquals(Movesets.EXECUTE.phases(), exec.phases());
		assertEquals(2.0F, exec.clipSpeed(), 1e-4, "the heavy's tick 12 on the Execute's tick 6");
	}

	@Test
	void mettleActionSpeedScalesTheClip() {
		Phases def = Movesets.of(WeaponFamily.SWORD).heavy().phases();
		Identifier id = Identifier.fromNamespaceAndPath("bestiary", "player/sword/heavy");
		ActionTiming fast = ActionTiming.of(ActionKind.HEAVY, WeaponFamily.SWORD, def, id, clips.get(id).timeline(), 1.2);
		assertEquals(1.2F, fast.clipSpeed(), 1e-4);
		assertEquals(10, fast.phases().windup(), "12 / 1.2");
	}
}
