import { defineConfig } from "vite";

export default defineConfig({
  publicDir: false,
  resolve: {
    conditions: ["node"],
    mainFields: ["module", "jsnext:main", "jsnext"],
  },
  build: {
    outDir: "dist/build",
    emptyOutDir: false,
    minify: true,
    lib: {
      entry: "src/main/main.ts",
      formats: ["cjs"],
      fileName: () => "main.js",
    },
    rollupOptions: {
      external: (id) => {
        if (
          id === "electron" ||
          id === "electron-updater" ||
          id === "electron-squirrel-startup" ||
          id === "minecraft-java-core"
        )
          return true;
        if (id.startsWith("node:")) return true;
        const builtins = [
          "path", "fs", "os", "http", "https", "child_process",
          "crypto", "events", "stream", "util", "url", "net",
          "tls", "zlib", "assert", "buffer", "querystring",
        ];
        return builtins.includes(id);
      },
    },
  },
  define: {
    MAIN_WINDOW_VITE_DEV_SERVER_URL: JSON.stringify(""),
    MAIN_WINDOW_VITE_NAME: JSON.stringify("main_window"),
  },
});
