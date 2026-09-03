package dev.voidclient.ultralight;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Classpath reads, called from native code.
 *
 * <p>The {@code ULFileSystem} implementation in {@code platform.cpp} needs to pull the in-game
 * bundle out of the mod JAR. Doing stream I/O from C++ through JNI would be a lot of calls per
 * file; two static methods that hand back a byte[] is one call each.
 */
final class Resources {

  private Resources() {}

  private static ClassLoader loader() {
    ClassLoader cl = Resources.class.getClassLoader();
    if (cl == null) {
      cl = ClassLoader.getSystemClassLoader();
    }
    return cl;
  }

  /** Returns the resource's bytes, or null if it does not exist. Called from native code. */
  static byte[] read(String path) {
    if (path == null || path.isEmpty()) {
      return null;
    }
    InputStream in = loader().getResourceAsStream(path);
    if (in == null) {
      return null;
    }
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, in.available()));
      byte[] buf = new byte[16 * 1024];
      int n;
      while ((n = in.read(buf)) > 0) {
        out.write(buf, 0, n);
      }
      return out.toByteArray();
    } catch (IOException e) {
      return null;
    } finally {
      try {
        in.close();
      } catch (IOException ignored) {
        // nothing useful to do
      }
    }
  }

  /** Whether the resource exists. Called from native code. */
  static boolean exists(String path) {
    if (path == null || path.isEmpty()) {
      return false;
    }
    URL url = loader().getResource(path);
    return url != null;
  }
}
