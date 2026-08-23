#!/usr/bin/env node
/*
 * Remaps Fabric *intermediary*-named Java sources to Mojang ("named") mappings.
 *
 * Used to vendor owo-lib's sources into this project: owo publishes its sources jar
 * with intermediary names (net.minecraft.class_3532, method_1551, field_22758) while
 * civmodern builds against officialMojangMappings().
 *
 *   node tools/remap-intermediary.js <mappings.tiny> <srcDir>
 *
 * mappings.tiny comes from Loom's cache, e.g.
 *   ~/.gradle/caches/fabric-loom/1.21.11/loom.mappings.1_21_11.layered+hash.<n>-v2/mappings.tiny
 */
const fs = require('fs');
const path = require('path');

const [, , tinyPath, srcDir] = process.argv;
if (!tinyPath || !srcDir) {
  console.error('usage: node tools/remap-intermediary.js <mappings.tiny> <srcDir>');
  process.exit(2);
}

// ---------------------------------------------------------------- parse tiny v2
// header:  tiny <major> <minor> official intermediary named
// class:   c  <ns0>  <ns1>  <ns2>
// member:  \t (m|f)  <desc>  <ns0>  <ns1>  <ns2>
const lines = fs.readFileSync(tinyPath, 'utf8').split('\n');
const NS = lines[0].split('\t').slice(3);
const iIdx = NS.indexOf('intermediary');
const nIdx = NS.indexOf('named');
if (iIdx < 0 || nIdx < 0) {
  console.error('mappings.tiny lacks intermediary/named namespaces (got ' + NS.join(', ') + ')');
  process.exit(2);
}

const fqnMap = new Map();     // net.minecraft.class_11228 -> net.minecraft.client.gui.render.GuiRenderer
const simpleMap = new Map();  // class_11230 -> Draw
const memberMap = new Map();  // method_1551 -> getInstance, field_22758 -> width, comp_1317 -> sinHalf
const ambiguous = new Set();

function note(map, key, value) {
  const prev = map.get(key);
  if (prev !== undefined && prev !== value) ambiguous.add(key);
  else map.set(key, value);
}

for (const raw of lines) {
  if (!raw) continue;
  const f = raw.split('\t');
  if (f[0] === 'c') {
    const inter = f[1 + iIdx];
    const named = f[1 + nIdx];
    if (!inter || !named) continue;
    const ip = inter.split('$');
    const np = named.split('$');
    if (ip.length !== np.length) continue;
    for (let k = 0; k < ip.length; k++) {
      const iSeg = k === 0 ? ip[0].split('/').pop() : ip[k];
      const nSeg = k === 0 ? np[0].split('/').pop() : np[k];
      if (/^class_\d+$/.test(iSeg)) note(simpleMap, iSeg, nSeg);
    }
    if (ip.length === 1) note(fqnMap, inter.replace(/\//g, '.'), named.replace(/\//g, '.'));
  } else if (f[0] === '' && (f[1] === 'm' || f[1] === 'f')) {
    const inter = f[3 + iIdx];
    const named = f[3 + nIdx];
    if (!inter || !named) continue;
    if (!/^(?:method_|field_|comp_)\d+$/.test(inter)) continue;
    note(memberMap, inter, named);
  }
}

// A few intermediary ids are reused across unrelated classes. Never guess: drop them
// so they survive substitution untouched and get reported as leftovers below.
for (const id of ambiguous) {
  simpleMap.delete(id);
  memberMap.delete(id);
  fqnMap.delete('net.minecraft.' + id);
}
console.log('mappings: ' + fqnMap.size + ' classes, ' + simpleMap.size + ' segments, ' +
  memberMap.size + ' members, ' + ambiguous.size + ' ambiguous ids excluded');

// ------------------------------------------------------------------- substitute
// Every intermediary top-level class is flattened into net.minecraft, so a single shape
// matches them all. The 20 entries outside net.minecraft are com.mojang.blaze3d.* whose
// obfuscated parts are nested segments only, handled by the simpleMap pass below.
const fqnRe = /\bnet\.minecraft\.class_\d+\b/g;

function remap(src) {
  return src
    .replace(fqnRe, m => fqnMap.get(m) || m)
    .replace(/\bclass_\d+\b/g, m => simpleMap.get(m) || m)
    .replace(/\b(?:method_|field_|comp_)\d+\b/g, m => memberMap.get(m) || m);
}

// ------------------------------------------------------------------------ walk
let files = 0;
const leftover = new Map();
(function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) { walk(p); continue; }
    if (!e.name.endsWith('.java')) continue;
    const before = fs.readFileSync(p, 'utf8');
    const after = remap(before);
    if (after !== before) fs.writeFileSync(p, after);
    files++;
    const rest = after.match(/\b(?:class_|method_|field_|comp_)\d+\b/g) || [];
    for (const m of rest) leftover.set(m, (leftover.get(m) || 0) + 1);
  }
})(srcDir);

console.log('remapped ' + files + ' file(s)');
if (leftover.size) {
  console.log('WARNING: ' + leftover.size + ' unmapped intermediary token(s) remain:');
  Array.from(leftover.entries()).sort((a, b) => b[1] - a[1]).slice(0, 20)
    .forEach(([k, v]) => console.log('  ' + k + ' x' + v + (ambiguous.has(k) ? '  (AMBIGUOUS)' : '')));
  process.exitCode = 1;
} else {
  console.log('no intermediary tokens remain');
}
