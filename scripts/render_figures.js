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
// Pictures added from open sources (scripts/figures-extra), drawn as images: nothing in them can run
const extraDir = join(here, "figures-extra");
const extras = JSON.parse(readFileSync(join(extraDir, "extras.json"), "utf8"));
const TYPES = ["image/svg+xml", "image/png", "image/gif", "image/jpeg"];
for (const x of extras) {
  if (!/^fig_[0-9a-f]{16}$/.test(x.name) || !TYPES.includes(x.type) || x.file.includes("/")) throw new Error(`bad extra ${x.name}`);
}
// Pictures no longer in the guide are removed
const keep = names.concat(extras.map((x) => x.name));
for (const f of readdirSync(out)) if (!keep.includes(f.replace(/\.png$/, ""))) rmSync(join(out, f));

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
for (const x of extras) {
  const data = readFileSync(join(extraDir, x.file)).toString("base64");
  const [cx, cy, cw, ch] = x.crop || [0, 0, 0, 0];
  // Shown at its width on white; an optional crop (in the picture's own pixels, with its size) leaves out a printed caption
  const img = x.crop
    ? `<div id="f" style="width:${x.width}px;height:${Math.round((x.width * ch) / cw)}px;overflow:hidden;position:relative"><img src="data:${x.type};base64,${data}" style="position:absolute;left:${(-cx * x.width) / cw}px;top:${(-cy * x.width) / cw}px;width:${(x.size[0] * x.width) / cw}px"></div>`
    : `<div id="f" style="width:${x.width}px"><img src="data:${x.type};base64,${data}" style="display:block;width:100%"></div>`;
  await page.setContent(`<!doctype html><meta charset="utf-8"><style>html,body{margin:0;background:#fff}</style>${img}`);
  await page.locator("#f").screenshot({ path: join(out, x.name + ".png"), omitBackground: false });
}
await browser.close();
console.log(`${names.length} figures and ${extras.length} added pictures rendered`);
})().catch((e) => { console.error(e); process.exit(1); });
