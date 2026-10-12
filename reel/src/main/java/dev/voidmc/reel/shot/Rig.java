package dev.voidmc.reel.shot;

/** A camera move: where the camera is at each moment of a shot, t in seconds from 0 to the shot's duration. */
public interface Rig {
	CameraState at(double t);

	/** One line for logs and reports: what the move is. */
	String describe();
}
