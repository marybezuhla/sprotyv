// Turns the guide's figures (scripts/figures-svg/*.svg, written by build_guide.py) into PNG
// pictures for the app: content/src/main/assets/figures/<name>.png, twice the drawn size, on white.
// Uses a local Chromium through Playwright; nothing is loaded from the network.
//
//   node scripts/render_figures.js
const { chromium } = require("playwright");
const { readdirSync, readFileSync, mkdirSync, rmSync } = require("node:fs");
const { join } = require("node:path");

const here = __dirname;
const src = join(here, "figures-svg");
const out = join(here, "..", "content", "src", "main", "assets", "figures");
mkdirSync(out, { recursive: true });

const names = readdirSync(src).filter((f) => f.endsWith(".svg")).map((f) => f.slice(0, -4));
// Pictures no longer in the guide are removed
for (const f of readdirSync(out)) if (!names.includes(f.replace(/\.png$/, ""))) rmSync(join(out, f));

(async () => {
const browser = await chromium.launch();
const page = await browser.newPage({ deviceScaleFactor: 2, javaScriptEnabled: false });
// Nothing outside the page itself may be fetched
await page.route("**/*", (route) => (route.request().url().startsWith("data:") || route.request().url() === "about:blank" ? route.continue() : route.abort()));
for (const name of names) {
  const svg = readFileSync(join(src, name + ".svg"), "utf8");
  if (/<script|href\s*=\s*["']?(?!#)|<foreignObject/i.test(svg)) throw new Error(`${name}: unexpected content in the figure`);
  await page.setContent(
    `<!doctype html><meta charset="utf-8"><style>html,body{margin:0;background:#fff}svg{display:block;font-family:"DejaVu Sans",sans-serif}</style>${svg}`,
  );
  await page.locator("svg").first().screenshot({ path: join(out, name + ".png"), omitBackground: false });
}
await browser.close();
console.log(`${names.length} figures rendered`);
})().catch((e) => { console.error(e); process.exit(1); });
