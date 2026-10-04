package dev.voidmc.hub.gametest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Process memory, the way Task Manager reports it (Windows; -1 elsewhere or on failure). */
public final class Memory {

	private Memory() {
	}

	/** Java heap in use after a GC. */
	public static long heapUsed() {
		Runtime rt = Runtime.getRuntime();
		System.gc();
		return rt.totalMemory() - rt.freeMemory();
	}

	/** Private bytes: everything this process committed for itself, native allocations included. */
	public static long privateBytes() {
		return query("PrivateMemorySize64");
	}

	/** Working set (resident memory). */
	public static long workingSet() {
		return query("WorkingSet64");
	}

	/** Whole-machine CPU load right now (Windows), -1 if unknown: context for noisy numbers. */
	public static int cpuLoadPercent() {
		try {
			Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
				"(Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average").redirectErrorStream(true).start();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String line = r.readLine();
				p.waitFor();
				return line == null ? -1 : (int) Math.round(Double.parseDouble(line.trim()));
			}
		} catch (Exception e) {
			return -1;
		}
	}

	private static long query(String property) {
		long pid = ProcessHandle.current().pid();
		try {
			Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
				"(Get-Process -Id " + pid + ")." + property).redirectErrorStream(true).start();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String line = r.readLine();
				p.waitFor();
				return line == null ? -1 : Long.parseLong(line.trim());
			}
		} catch (Exception e) {
			return -1;
		}
	}
}
