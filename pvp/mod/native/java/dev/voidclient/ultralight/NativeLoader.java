package dev.voidclient.ultralight;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extracts {@code natives/<os>-<arch>/} from the classpath into a temporary directory and loads the
 * libraries in dependency order.
 *
 * <p>Layout on the classpath, produced by the CMake build (see mod/native/README.md):
 *
 * <pre>
 *   natives/windows-x64/  voidultralight.dll  UltralightCore.dll  WebCore.dll  Ultralight.dll
 *   natives/macos-x64/    voidultralight.dylib  libUltralightCore.dylib  libWebCore.dylib  libUltralight.dylib
 *   natives/macos-arm64/  (same as macos-x64)
 *                         resources/cacert.pem  resources/icudt67l.dat
 *                         resources/fonts/Inter-Variable.ttf  resources/fonts/OFL.txt
 *                         resources/NOTICES.md
 *                         files.txt
 * </pre>
 *
 * <p>{@code files.txt} lists every payload path, one per line: a JAR gives no way to enumerate a
 * "directory", so the manifest is generated at build time and read here.
 *
 * <p>Load order matters. Ultralight's libraries are loaded before ours so that our library's
 * imports resolve against modules already in the process — on Windows that is the only thing that
 * makes it work without touching {@code PATH}, and on macOS it is what lets dyld satisfy the
 * {@code @rpath/…} install names.
 */
final class NativeLoader {

  private NativeLoader() {}

  /** Property to point at a pre-staged natives directory (the CMake build tree, in development). */
  private static final String DIR_PROPERTY = "void.ultralight.nativeDir";

  private static String nativeDir;

  static synchronized String nativeDir() {
    return nativeDir;
  }

  static synchronized void load() {
    String key = platformKey();

    String override = System.getProperty(DIR_PROPERTY);
    File dir;
    if (override != null && !override.isEmpty()) {
      dir = new File(override);
      if (!dir.isDirectory()) {
        throw new UnsatisfiedLinkError(DIR_PROPERTY + " is not a directory: " + override);
      }
    } else {
      dir = extract(key);
    }

    List<String> order = libraryOrder(key);
    for (String name : order) {
      File lib = new File(dir, name);
      if (!lib.isFile()) {
        throw new UnsatisfiedLinkError("missing native library " + lib.getAbsolutePath()
            + " (natives/" + key + "/ not on the classpath?)");
      }
      try {
        System.load(lib.getAbsolutePath());
      } catch (UnsatisfiedLinkError e) {
        throw new UnsatisfiedLinkError("failed to load " + lib.getAbsolutePath() + ": "
            + e.getMessage());
      }
    }

    nativeDir = dir.getAbsolutePath() + File.separator;
  }

  /** {@code windows-x64}, {@code macos-x64}, {@code macos-arm64} or {@code linux-x64}. */
  static String platformKey() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    boolean arm64 = arch.contains("aarch64") || arch.contains("arm64");
    boolean x64 = arch.contains("64") && !arm64;

    if (os.contains("win")) {
      if (!x64) {
        throw new UnsatisfiedLinkError("only 64-bit Windows is supported (os.arch=" + arch + ")");
      }
      return "windows-x64";
    }
    if (os.contains("mac") || os.contains("darwin")) {
      // Minecraft 1.8.9 runs on an x64 JVM under Rosetta on Apple Silicon (LWJGL 2 has no arm64
      // natives), so os.arch is normally x86_64 even on an M-series Mac. macos-arm64 exists for
      // tooling and for the day that changes.
      return arm64 ? "macos-arm64" : "macos-x64";
    }
    if (os.contains("linux")) {
      // Not shipped with the mod (§13, Linux out of scope) but built here for the CPU tests.
      return arm64 ? "linux-arm64" : "linux-x64";
    }
    throw new UnsatisfiedLinkError("unsupported platform: " + os + "/" + arch);
  }

  private static List<String> libraryOrder(String key) {
    List<String> out = new ArrayList<String>(4);
    if (key.startsWith("windows")) {
      out.add("UltralightCore.dll");
      out.add("WebCore.dll");
      out.add("Ultralight.dll");
      out.add("voidultralight.dll");
    } else if (key.startsWith("macos")) {
      out.add("libUltralightCore.dylib");
      out.add("libWebCore.dylib");
      out.add("libUltralight.dylib");
      out.add("voidultralight.dylib");
    } else {
      out.add("libUltralightCore.so");
      out.add("libWebCore.so");
      out.add("libUltralight.so");
      out.add("voidultralight.so");
    }
    return out;
  }

  private static File extract(String key) {
    String base = "natives/" + key + "/";
    List<Entry> files = manifest(base);

    File root = new File(System.getProperty("java.io.tmpdir"),
        "voidultralight-" + key + "-" + stamp(base));
    if (!root.isDirectory() && !root.mkdirs()) {
      throw new UnsatisfiedLinkError("could not create " + root.getAbsolutePath());
    }

    for (Entry entry : files) {
      File target = new File(root, entry.path);
      // Already there and the right size? Leave it: libWebCore alone is ~96 MB and this runs on
      // every launch.
      if (target.isFile() && entry.size >= 0 && target.length() == entry.size) {
        continue;
      }
      File parent = target.getParentFile();
      if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
        throw new UnsatisfiedLinkError("could not create " + parent.getAbsolutePath());
      }
      copy(base + entry.path, target);
    }
    return root;
  }

  /** One manifest line: a payload path and the size it must have once extracted. */
  private static final class Entry {
    final String path;
    final long size;

    Entry(String path, long size) {
      this.path = path;
      this.size = size;
    }
  }

  private static List<Entry> manifest(String base) {
    byte[] listing = Resources.read(base + "files.txt");
    if (listing == null) {
      throw new UnsatisfiedLinkError("missing " + base + "files.txt on the classpath — the mod JAR "
          + "was built without natives for this platform");
    }
    List<Entry> out = new ArrayList<Entry>();
    String text;
    try {
      text = new String(listing, "UTF-8");
    } catch (IOException e) {
      throw new UnsatisfiedLinkError("unreadable " + base + "files.txt");
    }
    for (String line : text.split("\n")) {
      String entry = line.trim();
      if (entry.isEmpty() || entry.startsWith("#")) {
        continue;
      }
      int tab = entry.indexOf('\t');
      if (tab < 0) {
        out.add(new Entry(entry, -1L));
      } else {
        long size;
        try {
          size = Long.parseLong(entry.substring(tab + 1).trim());
        } catch (NumberFormatException e) {
          size = -1L;
        }
        out.add(new Entry(entry.substring(0, tab), size));
      }
    }
    return out;
  }

  /**
   * Cache key for the extracted directory: the sizes of everything in the manifest. Cheap, and it
   * changes whenever a library does, so a rebuilt mod never runs against stale natives.
   */
  private static String stamp(String base) {
    byte[] version = Resources.read(base + "version.txt");
    if (version != null) {
      try {
        return new String(version, "UTF-8").trim().replaceAll("[^A-Za-z0-9._-]", "");
      } catch (IOException ignored) {
        // fall through
      }
    }
    byte[] listing = Resources.read(base + "files.txt");
    int hash = listing == null ? 0 : java.util.Arrays.hashCode(listing);
    return Integer.toHexString(hash);
  }

  private static void copy(String resource, File target) {
    InputStream in = NativeLoader.class.getClassLoader().getResourceAsStream(resource);
    if (in == null) {
      throw new UnsatisfiedLinkError("missing classpath entry " + resource);
    }
    OutputStream out = null;
    try {
      out = new FileOutputStream(target);
      byte[] buf = new byte[64 * 1024];
      int n;
      while ((n = in.read(buf)) > 0) {
        out.write(buf, 0, n);
      }
    } catch (IOException e) {
      throw new UnsatisfiedLinkError("could not extract " + resource + ": " + e.getMessage());
    } finally {
      close(in);
      close(out);
    }
    if (target.getName().endsWith(".so") || target.getName().endsWith(".dylib")
        || target.getName().endsWith(".dll")) {
      target.setExecutable(true, false);
    }
  }

  private static void close(java.io.Closeable c) {
    if (c != null) {
      try {
        c.close();
      } catch (IOException ignored) {
        // nothing useful to do
      }
    }
  }
}
