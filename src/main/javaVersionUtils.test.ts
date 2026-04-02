import { describe, it, expect } from "vitest";
import { getRequiredJavaVersion } from "./javaVersionUtils";

describe("getRequiredJavaVersion", () => {
  // ─── Year-based versions (Java 25) ─────────────────────────────────
  describe("year-based versions (major >= 25)", () => {
    it("should return 25 for 26.1 (two-part year-based version)", () => {
      expect(getRequiredJavaVersion("26.1")).toBe(25);
    });

    it("should return 25 for 26.1.1 (three-part year-based version)", () => {
      expect(getRequiredJavaVersion("26.1.1")).toBe(25);
    });

    it("should return 25 for 27.2", () => {
      expect(getRequiredJavaVersion("27.2")).toBe(25);
    });

    it("should return 25 for 30.1 (far future year-based version)", () => {
      expect(getRequiredJavaVersion("30.1")).toBe(25);
    });

    it("should return 25 for 25.1 (boundary: major === 25)", () => {
      expect(getRequiredJavaVersion("25.1")).toBe(25);
    });
  });

  // ─── 1.20.5+ → Java 21 ─────────────────────────────────────────────
  describe("MC 1.20.5+ (Java 21)", () => {
    it("should return 21 for 1.21.4", () => {
      expect(getRequiredJavaVersion("1.21.4")).toBe(21);
    });

    it("should return 21 for 1.20.5 (exact boundary)", () => {
      expect(getRequiredJavaVersion("1.20.5")).toBe(21);
    });

    it("should return 21 for 1.21.1", () => {
      expect(getRequiredJavaVersion("1.21.1")).toBe(21);
    });

    it("should return 21 for 1.21 (minor > 20, no patch)", () => {
      expect(getRequiredJavaVersion("1.21")).toBe(21);
    });
  });

  // ─── 1.17 – 1.20.4 → Java 17 ──────────────────────────────────────
  describe("MC 1.17 to 1.20.4 (Java 17)", () => {
    it("should return 17 for 1.17 (lower boundary)", () => {
      expect(getRequiredJavaVersion("1.17")).toBe(17);
    });

    it("should return 17 for 1.20.4 (just below Java-21 boundary)", () => {
      expect(getRequiredJavaVersion("1.20.4")).toBe(17);
    });

    it("should return 17 for 1.19.2", () => {
      expect(getRequiredJavaVersion("1.19.2")).toBe(17);
    });

    it("should return 17 for 1.20 (no patch defaults to patch 0, below 1.20.5)", () => {
      expect(getRequiredJavaVersion("1.20")).toBe(17);
    });
  });

  // ─── Pre-1.17 → Java 8 ─────────────────────────────────────────────
  describe("MC pre-1.17 (Java 8)", () => {
    it("should return 8 for 1.16.5", () => {
      expect(getRequiredJavaVersion("1.16.5")).toBe(8);
    });

    it("should return 8 for 1.12.2", () => {
      expect(getRequiredJavaVersion("1.12.2")).toBe(8);
    });

    it("should return 8 for 1.8.9", () => {
      expect(getRequiredJavaVersion("1.8.9")).toBe(8);
    });

    it("should return 8 for 1.16 (no patch, still below 1.17)", () => {
      expect(getRequiredJavaVersion("1.16")).toBe(8);
    });
  });
});
