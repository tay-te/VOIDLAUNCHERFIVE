package dev.voidmc.bestiary.studio;

import java.nio.file.Files;

/** Switches Iris's shader pack at runtime (studio and showcase runs with -Piris): a pack folder or zip in shaderpacks, or off. */
public final class IrisPacks {
	private IrisPacks() {
	}

	public static String select(String name) {
		try {
			var config = net.irisshaders.iris.Iris.getIrisConfig();
			if (name.equals("off") || name.equals("none")) {
				config.setShadersEnabled(false);
			} else {
				String found = null;
				try (var list = Files.list(net.irisshaders.iris.Iris.getShaderpacksDirectory())) {
					for (var p : list.toList()) {
						String f = p.getFileName().toString();
						if (f.equalsIgnoreCase(name) || f.toLowerCase(java.util.Locale.ROOT).startsWith(name.toLowerCase(java.util.Locale.ROOT))) {
							found = f;
							break;
						}
					}
				}
				if (found == null) {
					return "no shader pack like '" + name + "' in " + net.irisshaders.iris.Iris.getShaderpacksDirectory();
				}
				config.setShaderPackName(found);
				config.setShadersEnabled(true);
			}
			config.save();
			net.irisshaders.iris.Iris.reload();
			return "pack " + net.irisshaders.iris.Iris.getCurrentPackName() + (net.irisshaders.iris.Iris.getStoredError().isPresent() ? " (error: "
				+ net.irisshaders.iris.Iris.getStoredError().get() + ")" : "");
		} catch (Exception e) {
			return "pack switch failed: " + e;
		}
	}
}
