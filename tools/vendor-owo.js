#!/usr/bin/env node
/*
 * Relocates the remapped owo-lib sources into civmodern's own packages.
 *
 * Pipeline (see tools/README-vendoring.md):
 *   1. extract owo-lib-<ver>-sources.jar
 *   2. keep only the paths in tools/owo-vendor-manifest.txt
 *   3. node tools/remap-intermediary.js <mappings.tiny> <keepDir>
 *   4. node tools/vendor-owo.js <keepDir>            <-- this script
 *
 *   io.wispforest.owo.ui.*        -> sh.okx.civmodern.common.ui.*
 *   io.wispforest.owo.util.*      -> sh.okx.civmodern.common.ui.observable.*
 *   io.wispforest.owo.mixin.ui.*  -> sh.okx.civmodern.common.mixins.*
 *   owo$<member>                  -> civmodern$<member>
 *
 * The owo$ -> civmodern$ rename is not cosmetic: these members are added to vanilla
 * classes by mixin under explicit (non-@Unique) names, so leaving them as owo$ would
 * collide with the real owo-lib whenever a player has both mods installed.
 *
 * Upstream class names are deliberately left alone so future owo releases stay diffable.
 */
const fs = require('fs');
const path = require('path');

const [, , keepDir] = process.argv;
if (!keepDir) {
  console.error('usage: node tools/vendor-owo.js <keepDir>');
  process.exit(2);
}

const REPO = path.resolve(__dirname, '..');
const JAVA = path.join(REPO, 'src', 'main', 'java');

const UI = 'sh.okx.civmodern.common.ui';
const OBS = 'sh.okx.civmodern.common.ui.observable';
const MIX = 'sh.okx.civmodern.common.mixins';

// Longest prefix first. Matched without a trailing dot so that both the `package x.y;`
// declaration and `x.y.Type` references are rewritten by the same rule.
const PKG_RULES = [
  ['io.wispforest.owo.mixin.ui.access', MIX],
  ['io.wispforest.owo.mixin.ui', MIX],
  ['io.wispforest.owo.ui', UI],
  ['io.wispforest.owo.util', OBS],
].map(([from, to]) => [
  new RegExp(from.replace(/\./g, '\\.') + '(?![A-Za-z0-9_])', 'g'),
  to,
]);

const HEADER = [
  '/*',
  ' * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).',
  ' * Copyright (c) glisco and owo-lib contributors. Licensed under the MIT License;',
  ' * see NOTICE.md at the repository root for the full text.',
  ' *',
  ' * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.',
  ' * Keep edits minimal so future owo-lib releases stay diffable.',
  ' */',
  '',
].join('\n');

function targetFor(pkg, name) {
  return path.join(JAVA, pkg.replace(/\./g, path.sep), name);
}

function rewrite(src) {
  let out = src;
  for (const [from, to] of PKG_RULES) out = out.replace(from, to);
  out = out.replace(/\bowo\$/g, 'civmodern$');
  return out;
}

let written = 0;
(function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) { walk(p); continue; }
    if (!e.name.endsWith('.java')) continue;

    const body = rewrite(fs.readFileSync(p, 'utf8'));
    const m = body.match(/^package\s+([\w.]+);/m);
    if (!m) { console.error('no package decl in ' + p); process.exit(1); }

    const dest = targetFor(m[1], e.name);
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.writeFileSync(dest, HEADER + body);
    written++;
  }
})(keepDir);

console.log('vendored ' + written + ' file(s) into ' + path.relative(REPO, JAVA));
