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
  },
});
