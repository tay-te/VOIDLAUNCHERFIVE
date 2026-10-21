package dev.voidmc.fray.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.fray.combat.math.LoneKey;
import org.junit.jupiter.api.Test;

/** The lone-key gate behind Alt's dodge and creative C (the user's calls on open questions 1 and 2, 2026-10-21). */
class LoneKeyTest {
	@Test
	void altAloneFires() {
		LoneKey k = new LoneKey(500);
		k.press(0, false);
		assertTrue(k.release(120), "Alt tapped by itself dodges");
	}

	@Test
	void altPlusMiddleMouseIsPickBlockNotADodge() {
		LoneKey k = new LoneKey(500);
		k.press(0, false);
		k.other(); // middle mouse down
		assertFalse(k.release(150), "Alt + middle mouse: no dodge");
		k.press(1000, false);
		assertTrue(k.release(1080), "the next lone tap dodges again");
	}

	@Test
	void anyOtherKeyBetweenSpoilsIt() {
		LoneKey k = new LoneKey(500);
		k.press(0, false);
		k.other(); // Tab (Alt + Tab), F4, a letter
		assertFalse(k.release(60));
	}

	@Test
	void keysPressedBeforeDontSpoil() {
		LoneKey k = new LoneKey(500);
		k.other(); // W held before Alt went down
		k.press(10, false);
		assertTrue(k.release(90), "a movement key held before Alt still dodges (the roll's direction)");
	}

	@Test
	void heldTooLongDoesNothing() {
		LoneKey k = new LoneKey(500);
		k.press(0, false);
		assertFalse(k.release(800), "Alt held for a menu or a window switch isn't a dodge");
	}

	@Test
	void releaseWithoutPressAndCancel() {
		LoneKey k = new LoneKey(500);
		assertFalse(k.release(10), "a release whose press went to a screen does nothing");
		k.press(0, false);
		k.cancel();
		assertFalse(k.release(50), "focus lost: forgotten");
	}

	@Test
	void creativeCPlusNumberIsTheToolbar() {
		LoneKey c = new LoneKey(1000);
		c.press(0, false);
		c.other(); // 1 pressed while C held: save toolbar 1
		assertFalse(c.release(200), "C + 1 doesn't fire the technique");
		c.press(500, true); // 3 already held when C went down
		assertFalse(c.release(600), "1 then C doesn't either");
		c.press(1000, false);
		assertTrue(c.release(1100), "C alone does");
	}
}
