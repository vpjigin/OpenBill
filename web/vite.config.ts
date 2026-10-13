import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { APP_NAME, APP_NAME_ADMIN } from "./src/shared/brand";

const root = fileURLToPath(new URL(".", import.meta.url));

// Two apps, one build:
//   index.html -> billing website (served on the billing port: this computer, Wi-Fi, public)
//   admin.html -> desktop admin (served only on 127.0.0.1 admin port)
// In dev, run the Spring Boot server, then `npm run dev:billing` or `npm run dev:admin`.
const target =
  process.env.VITE_APP === "admin" ? "http://127.0.0.1:9090" : "http://127.0.0.1:8080";

function htmlTitles() {
  return {
    name: "html-titles",
    transformIndexHtml(html: string, ctx: { filename: string }) {
      if (ctx.filename.endsWith("admin.html")) {
        return html.replace(/<title>[^<]*<\/title>/, `<title>${APP_NAME_ADMIN}</title>`);
      }
      return html.replace(/<title>[^<]*<\/title>/, `<title>${APP_NAME}</title>`);
    },
  };
}

export default defineConfig({
  plugins: [react(), tailwindcss(), htmlTitles()],
  appType: "mpa",
  server: {
    proxy: { "/api": { target, changeOrigin: true } },
  },
  build: {
    outDir: resolve(root, "../server/src/main/resources/static"),
    emptyOutDir: true,
    rollupOptions: {
      input: {
        index: resolve(root, "index.html"),
        admin: resolve(root, "admin.html"),
      },
    },
  },
});
