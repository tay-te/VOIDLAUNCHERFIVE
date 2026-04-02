/**
 * Pure utility: maps a Minecraft version string to the minimum required Java
 * major version. Kept separate from main.ts so it can be unit-tested without
 * importing Electron.
 */
export function getRequiredJavaVersion(mcVersion: string): number {
  const parts = mcVersion.split(".");
  const major = parseInt(parts[0]) || 1;
  const minor = parseInt(parts[1]) || 0;
  const patch = parseInt(parts[2]) || 0;

  // Year-based versions (26.x+) require Java 25
  if (major >= 25) {
    return 25;
  }
  // MC 1.20.5+ requires Java 21
  if ((minor > 20) || (minor === 20 && patch >= 5)) {
    return 21;
  }
  // MC 1.17+ requires Java 17
  if (minor >= 17) {
    return 17;
  }
  // Older versions use Java 8
  return 8;
}
