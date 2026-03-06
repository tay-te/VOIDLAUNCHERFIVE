import type { ForgeConfig } from "@electron-forge/shared-types";
import { MakerSquirrel } from "@electron-forge/maker-squirrel";
import { MakerZIP } from "@electron-forge/maker-zip";
import { MakerDMG } from "@electron-forge/maker-dmg";
import { AutoUnpackNativesPlugin } from "@electron-forge/plugin-auto-unpack-natives";
import { VitePlugin } from "@electron-forge/plugin-vite";
import { PublisherGithub } from "@electron-forge/publisher-github";

const config: ForgeConfig = {
  packagerConfig: {
    asar: true,
    name: "Void Launcher",
    executableName: "void-launcher",
    appBundleId: "com.voidlauncher.app",
    icon: "./public/icon",
  },
  rebuildConfig: {},
  makers: [
    new MakerSquirrel({
      name: "void-launcher",
      // setupIcon: "./public/icon.ico",  // uncomment when icon.ico is added
    }),
    new MakerZIP({}, ["darwin"]),
    new MakerDMG({
      format: "ULFO",
      // icon: "./public/icon.icns",  // uncomment when icon.icns is added
    }),
  ],
  publishers: [
    new PublisherGithub({
      repository: {
        owner: "tay-te",
        name: "VOIDLAUNCHERFIVE",
      },
      prerelease: false,
      draft: true,
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
