import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  publicDir: false,
  base: "./",
  resolve: {
    alias: {
      "@": "/src/renderer",
    },
  },
  build: {
    outDir: "dist/renderer/main_window",
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) return;

          if (
            id.includes("/@react-three/")
          ) {
            return "react-three-vendor";
          }

          if (
            id.includes("/three/") ||
            id.includes("/skinview3d/")
          ) {
            return "three-vendor";
          }

          if (id.includes("/marked/") || id.includes("/dompurify/")) {
            return "content-vendor";
          }

          if (
            id.includes("/react/") ||
            id.includes("/react-dom/") ||
            id.includes("/scheduler/")
          ) {
            return "react-vendor";
          }

          if (id.includes("/mobx/") || id.includes("/mobx-react-lite/")) {
            return "state-vendor";
          }

          if (id.includes("/@supabase/")) {
            return "supabase-vendor";
          }

          return "vendor";
        },
      },
    },
  },
});
