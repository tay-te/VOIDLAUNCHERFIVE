package dev.voidclient.ultralight;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extracts {@code natives/<os>-<arch>/} from the classpath into a content-addressed cache
 * directory and loads the libraries in dependency order.
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
 * "directory", so the manifest is generated at build time and read here. Each line is
 * {@code <path>\t<size>[\t<sha256>]}; the hash column is what makes the cache trustworthy.
 *
 * <p><b>Where it extracts.</b> The launcher passes {@code -Dvoid.nativesCache=<data dir>/natives-cache}
 * and the libraries go to {@code <that>/voidultralight-<key>-<hash>/}, where {@code <hash>} is a
 * SHA-256 over {@code files.txt} (which itself carries every file's SHA-256) and {@code version.txt}.
 * So the directory name <i>is</i> the content: a rebuilt binding can never run against a previously
 * extracted copy of itself, and nothing has to be compared but sizes on a warm start. Extraction goes
 * to a staging directory, every file is hashed as it is written and checked against the manifest, and
 * only a complete, verified tree is renamed into place (with a {@code .complete} marker) — so a crash
 * or a full disk mid-extraction leaves nothing that a later launch would trust. Without the property
 * (a plain {@code runClient}, the CPU tests) or when that directory is not writable, the same scheme
 * runs under {@code java.io.tmpdir}.
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

  /** Property the launcher sets: the parent directory for content-addressed extraction. */
  private static final String CACHE_PROPERTY = "void.nativesCache";

  /** Written last into a fully extracted and verified directory. */
  private static final String COMPLETE_MARKER = ".complete";

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
    String name = "voidultralight-" + key + "-" + stamp(base);

    UnsatisfiedLinkError last = null;
    for (File parent : cacheRoots()) {
      try {
        File dir = extractInto(new File(parent, name), base, files);
        if (parent != tmpRoot()) {
          pruneSiblings(parent, "voidultralight-" + key + "-", dir);
        }
        return dir;
      } catch (UnsatisfiedLinkError e) {
        last = e;
      }
    }
    throw last != null ? last : new UnsatisfiedLinkError("no directory to extract natives into");
  }

  /** The launcher's cache directory first (when set), then the JVM temp directory. */
  private static List<File> cacheRoots() {
    List<File> roots = new ArrayList<File>(2);
    String cache = System.getProperty(CACHE_PROPERTY);
    if (cache != null && !cache.trim().isEmpty()) {
      roots.add(new File(cache.trim()));
    }
    roots.add(tmpRoot());
    return roots;
  }

  private static File tmpRootFile;

  private static synchronized File tmpRoot() {
    if (tmpRootFile == null) {
      tmpRootFile = new File(System.getProperty("java.io.tmpdir"));
    }
    return tmpRootFile;
  }

  /**
   * Returns {@code root} once it holds a complete, verified copy of {@code files}, extracting into a
   * staging directory and renaming it into place when it does not.
   */
  private static File extractInto(File root, String base, List<Entry> files) {
    if (isComplete(root, files)) {
      return root;
    }
    File parent = root.getParentFile();
    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
      throw new UnsatisfiedLinkError("could not create " + parent.getAbsolutePath());
    }
    File staging = new File(parent, root.getName() + ".tmp-" + Long.toHexString(System.nanoTime()));
    try {
      for (Entry entry : files) {
        File target = new File(staging, entry.path);
        File dir = target.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
          throw new UnsatisfiedLinkError("could not create " + dir.getAbsolutePath());
        }
        String actual = copy(base + entry.path, target);
        if (entry.size >= 0 && target.length() != entry.size) {
          throw new UnsatisfiedLinkError("extracted " + entry.path + " is " + target.length()
              + " bytes, the manifest says " + entry.size);
        }
        if (entry.sha256 != null && !entry.sha256.equalsIgnoreCase(actual)) {
          throw new UnsatisfiedLinkError("extracted " + entry.path + " does not match its sha256");
        }
      }
      writeMarker(new File(staging, COMPLETE_MARKER));

      // An incomplete tree from an interrupted run is in the way; a complete one means another
      // process won the race, and its copy is as good as ours.
      if (root.exists() && !isComplete(root, files)) {
        deleteTree(root);
      }
      if (!root.exists() && staging.renameTo(root)) {
        return root;
      }
      if (isComplete(root, files)) {
        return root;
      }
      throw new UnsatisfiedLinkError("could not move extracted natives into " + root.getAbsolutePath());
    } finally {
      if (staging.exists()) {
        deleteTree(staging);
      }
    }
  }

  /** A directory carries the marker and every file at its manifest size. */
  private static boolean isComplete(File root, List<Entry> files) {
    if (!new File(root, COMPLETE_MARKER).isFile()) {
      return false;
    }
    for (Entry entry : files) {
      File f = new File(root, entry.path);
      if (!f.isFile() || (entry.size >= 0 && f.length() != entry.size)) {
        return false;
      }
    }
    return true;
  }

  /** Removes older extractions for the same platform; locked ones (in use) are left alone. */
  private static void pruneSiblings(File parent, String prefix, File keep) {
    File[] entries = parent.listFiles();
    if (entries == null) {
      return;
    }
    for (File f : entries) {
      if (f.isDirectory() && f.getName().startsWith(prefix) && !f.equals(keep)) {
        deleteTree(f);
      }
    }
  }

  private static void deleteTree(File f) {
    File[] children = f.listFiles();
    if (children != null) {
      for (File c : children) {
        deleteTree(c);
      }
    }
    if (!f.delete()) {
      f.deleteOnExit();
    }
  }

  private static void writeMarker(File marker) {
    OutputStream out = null;
    try {
      out = new FileOutputStream(marker);
      out.write("ok\n".getBytes("UTF-8"));
    } catch (IOException e) {
      throw new UnsatisfiedLinkError("could not write " + marker.getAbsolutePath() + ": " + e.getMessage());
    } finally {
      close(out);
    }
  }

  /** One manifest line: a payload path, the size it must have, and its SHA-256 when known. */
  private static final class Entry {
    final String path;
    final long size;
    final String sha256;

    Entry(String path, long size, String sha256) {
      this.path = path;
      this.size = size;
      this.sha256 = sha256;
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
      String[] cols = entry.split("\t");
      long size = -1L;
      if (cols.length > 1) {
        try {
          size = Long.parseLong(cols[1].trim());
        } catch (NumberFormatException e) {
          size = -1L;
        }
      }
      String sha = cols.length > 2 && cols[2].trim().length() == 64 ? cols[2].trim() : null;
      if (cols[0].contains("..")) {
        throw new UnsatisfiedLinkError("refusing manifest path " + cols[0]);
      }
      out.add(new Entry(cols[0], size, sha));
    }
    return out;
  }

  /**
   * Cache key for the extracted directory: SHA-256 over {@code files.txt} (every file's size and
   * hash) and {@code version.txt}, first 16 hex digits. It changes whenever any library does, so a
   * rebuilt mod never runs against stale natives.
   */
  static String stamp(String base) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] listing = Resources.read(base + "files.txt");
      byte[] version = Resources.read(base + "version.txt");
      if (listing != null) {
        md.update(listing);
      }
      md.update((byte) 0);
      if (version != null) {
        md.update(version);
      }
      return hex(md.digest()).substring(0, 16);
    } catch (NoSuchAlgorithmException e) {
      throw new UnsatisfiedLinkError("no SHA-256 in this JVM");
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
    }
    return sb.toString();
  }

  /** Copies a classpath resource to {@code target}, returning the SHA-256 of what was written. */
  private static String copy(String resource, File target) {
    InputStream in = NativeLoader.class.getClassLoader().getResourceAsStream(resource);
    if (in == null) {
      throw new UnsatisfiedLinkError("missing classpath entry " + resource);
    }
    OutputStream out = null;
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      close(in);
      throw new UnsatisfiedLinkError("no SHA-256 in this JVM");
    }
    try {
      out = new FileOutputStream(target);
      byte[] buf = new byte[64 * 1024];
      int n;
      while ((n = in.read(buf)) > 0) {
        md.update(buf, 0, n);
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
    return hex(md.digest());
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
