import { build } from "esbuild";
await build({entryPoints:["src/extension.jsx"],bundle:true,format:"iife",loader:{".js":"jsx"},outfile:"dist/extensions/index.js"});
