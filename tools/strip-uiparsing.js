#!/usr/bin/env node
/*
 * Removes owo's XML UI-model parsing from the vendored sources.
 *
 * civmodern builds every screen in Java and never loads a .xml UI model, so the whole
 * io.wispforest.owo.ui.parsing package is dropped from the vendor manifest. This script
 * deletes what referenced it: parseProperties() overrides and static parse(Element)
 * factories, together with their javadoc, annotations and now-dead imports.
 *
 *   node tools/strip-uiparsing.js <javaRoot>
 *
 * Run after tools/vendor-owo.js. Idempotent.
 */
const fs = require('fs');
const path = require('path');

const [, , root] = process.argv;
if (!root) {
  console.error('usage: node tools/strip-uiparsing.js <javaRoot>');
  process.exit(2);
}

const DEAD_IMPORT = /^import\s+(?:sh\.okx\.civmodern\.common\.ui\.parsing\.[\w.]+|org\.w3c\.dom\.[\w.]+)\s*;\s*$/;

// A method declaration whose parameter list mentions a parsing-only type.
const SIG = /^\s*(?:@\w+[^\n]*\s*)*(?:public|protected|private|default|static|final|abstract|\s)*[\w<>,.\[\]?\s]+\s+\w+\s*\([^)]*\b(?:UIModel|Element|Node)\b[^)]*\)\s*\{/;

function stripMethods(lines) {
  const out = [];
  for (let i = 0; i < lines.length; i++) {
    const rest = lines.slice(i).join('\n');
    if (!SIG.test(lines[i] + '\n' + (lines[i + 1] || ''))) { out.push(lines[i]); continue; }
    if (!/\b(?:UIModel|Element|Node)\b/.test(lines[i])) { out.push(lines[i]); continue; }

    // Walk back over @Annotations, javadoc and a single blank separator.
    while (out.length && /^\s*@\w/.test(out[out.length - 1])) out.pop();
    if (out.length && /\*\/\s*$/.test(out[out.length - 1])) {
      while (out.length && !/^\s*\/\*/.test(out[out.length - 1])) out.pop();
      if (out.length) out.pop();
    }
    while (out.length && out[out.length - 1].trim() === '') out.pop();

    // Brace-match forward from the signature.
    let depth = 0;
    let started = false;
    let j = i;
    for (; j < lines.length; j++) {
      for (const ch of lines[j].replace(/"(?:\\.|[^"\\])*"/g, '""').replace(/'(?:\\.|[^'\\])*'/g, "''")) {
        if (ch === '{') { depth++; started = true; }
        else if (ch === '}') depth--;
      }
      if (started && depth <= 0) break;
    }
    i = j;
    out.push('');
  }
  return out;
}

let touched = 0;
(function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) { walk(p); continue; }
    if (!e.name.endsWith('.java')) continue;

    const before = fs.readFileSync(p, 'utf8');
    if (!/UIParsing|UIModel|org\.w3c\.dom/.test(before)) continue;

    let lines = stripMethods(before.split(/\r?\n/));
    lines = lines.filter((l) => !DEAD_IMPORT.test(l));
    const after = lines.join('\n').replace(/\n{3,}/g, '\n\n');
    if (after !== before) { fs.writeFileSync(p, after); touched++; }
  }
})(root);

console.log('stripped UI-model parsing from ' + touched + ' file(s)');
