package dev.voidmc.reel.shot;

/** A shot file that cannot be filmed, with the field at fault ("camera.keys[2].pos"). */
public final class ShotException extends RuntimeException {
	private final String field;

	public ShotException(String field, String message) {
		super(field.isEmpty() ? message : field + ": " + message);
		this.field = field;
	}

	public String field() {
		return this.field;
	}
}
