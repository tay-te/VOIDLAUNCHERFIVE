import type { ForgeConfig } from "@electron-forge/shared-types";
import { MakerSquirrel } from "@electron-forge/maker-squirrel";
import { MakerZIP } from "@electron-forge/maker-zip";
import { MakerDMG } from "@electron-forge/maker-dmg";
import { AutoUnpackNativesPlugin } from "@electron-forge/plugin-auto-unpack-natives";
import { VitePlugin } from "@electron-forge/plugin-vite";
import { PublisherGithub } from "@electron-forge/publisher-github";
import fs from "fs";
import path from "path";

const config: ForgeConfig = {
  packagerConfig: {
    asar: true,
    name: "Void Launcher",
    executableName: "void-launcher",
    appBundleId: "com.voidlauncher.app",
    icon: "./public/icon",
    afterCopy: [
      (buildPath: string, _electronVersion: string, _platform: string, _arch: string, callback: (err?: Error) => void) => {
        const copied = new Set<string>();
        function copyWithDeps(mod: string) {
          if (copied.has(mod)) return;
          copied.add(mod);
          const src = path.resolve(__dirname, "node_modules", mod);
          if (!fs.existsSync(src)) return;
          const dest = path.join(buildPath, "node_modules", mod);
          fs.cpSync(src, dest, { recursive: true });
          try {
            const pkg = JSON.parse(fs.readFileSync(path.join(src, "package.json"), "utf-8"));
            for (const dep of Object.keys(pkg.dependencies || {})) {
              copyWithDeps(dep);
            }
          } catch {}
        }
        copyWithDeps("minecraft-java-core");
        callback();
      },
    ],
  },
  rebuildConfig: {},
  makers: [
    new MakerSquirrel({
      name: "void-launcher",
      setupIcon: "./public/icon.ico",
    }),
    new MakerZIP({}, ["darwin"]),
    new MakerDMG({
      format: "ULFO",
      icon: "./public/icon.icns",
    }),
  ],
  publishers: [
    new PublisherGithub({
      repository: {
        owner: "tay-te",
        name: "VOIDLAUNCHERFIVE",
      },
      prerelease: false,
      draft: false,
    }),
  ],
  plugins: [
    new AutoUnpackNativesPlugin({}),
    new VitePlugin({
      build: [
        {
          entry: "src/main/main.ts",
          config: "vite.main.config.ts",
          target: "main",
        },
        {
          entry: "src/main/preload.ts",
          config: "vite.preload.config.ts",
          target: "preload",
        },
      ],
      renderer: [
        {
          name: "main_window",
          config: "vite.renderer.config.ts",
        },
      ],
    }),
  ],
};

export default config;
