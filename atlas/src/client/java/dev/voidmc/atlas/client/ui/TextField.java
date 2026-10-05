package dev.voidmc.atlas.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;

/** A one-line text input in the design's well style: typing, backspace, delete, arrows, home/end, paste. */
public final class TextField {
	public String text = "";
	public int caret;
	public boolean focused;
	public final int maxLength;
	public String placeholder = "";

	public TextField(int maxLength) {
		this.maxLength = maxLength;
	}

	public void set(String value) {
		this.text = value == null ? "" : value;
		this.caret = this.text.length();
	}

	public boolean charTyped(CharacterEvent event) {
		if (!this.focused || !event.isAllowedChatCharacter()) {
			return false;
		}
		this.insert(event.codepointAsString());
		return true;
	}

	private void insert(String s) {
		String clean = s.replaceAll("[\\p{Cntrl}]", "");
		int room = this.maxLength - this.text.length();
		if (room <= 0 || clean.isEmpty()) {
			return;
		}
		if (clean.length() > room) {
			clean = clean.substring(0, room);
		}
		this.text = this.text.substring(0, this.caret) + clean + this.text.substring(this.caret);
		this.caret += clean.length();
	}

	/** Handles editing keys; returns true if the key was the field's. Enter and Escape are left to the owner. */
	public boolean keyPressed(KeyEvent event) {
		if (!this.focused) {
			return false;
		}
		int key = event.key();
		if (event.isPaste()) {
			this.insert(Minecraft.getInstance().keyboardHandler.getClipboard());
			return true;
		}
		if (event.isSelectAll()) {
			this.caret = this.text.length();
			return true;
		}
		switch (key) {
			case InputConstants.KEY_BACKSPACE -> {
				if (this.caret > 0) {
					this.text = this.text.substring(0, this.caret - 1) + this.text.substring(this.caret);
					this.caret--;
				}
				return true;
			}
			case InputConstants.KEY_DELETE -> {
				if (this.caret < this.text.length()) {
					this.text = this.text.substring(0, this.caret) + this.text.substring(this.caret + 1);
				}
				return true;
			}
			case InputConstants.KEY_LEFT -> {
				this.caret = Math.max(0, this.caret - 1);
				return true;
			}
			case InputConstants.KEY_RIGHT -> {
				this.caret = Math.min(this.text.length(), this.caret + 1);
				return true;
			}
			case InputConstants.KEY_HOME -> {
				this.caret = 0;
				return true;
			}
			case InputConstants.KEY_END -> {
				this.caret = this.text.length();
				return true;
			}
			default -> {
				// typing keys arrive as characters; swallow the rest so the map's keys don't fire while typing
				return key != InputConstants.KEY_RETURN && key != InputConstants.KEY_NUMPADENTER && key != InputConstants.KEY_ESCAPE && key != InputConstants.KEY_TAB;
			}
		}
	}

	/** Draws the field's text and caret inside a box (the caller draws the box). */
	public void draw(Ui ui, float x, float y, float w, Ui.Font font, int ink, int placeholderInk) {
		float lh = Ui.lineHeight(font);
		if (this.text.isEmpty() && !this.placeholder.isEmpty()) {
			ui.text(ui.fit(this.placeholder, font, w), x, y, font, placeholderInk);
		}
		String shown = this.text;
		float caretX = ui.width(this.text.substring(0, this.caret), font);
		float scroll = Math.max(0, caretX - w + 4);
		ui.text(shown, x - scroll, y, font, ink);
		if (this.focused && (System.currentTimeMillis() / 530) % 2 == 0) {
			ui.rect(ui.snap(x + caretX - scroll), y + 1, ui.hairline(), lh - 2, Theme.VIOLET);
		}
	}
}
