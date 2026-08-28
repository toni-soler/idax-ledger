import { readFile } from "node:fs/promises";
const locales = ["es","en","ca","eu","gl","de","fr","it","pt","pt-BR","jp","zh"];
for (const locale of locales) {
  const parsed = JSON.parse(await readFile(new URL(`../src/locales/${locale}.json`, import.meta.url), "utf8"));
  if (!parsed.ledger?.title) throw new Error(`Missing ledger.title in ${locale}`);
}
console.log(`Validated ${locales.length} IDAX locales`);
