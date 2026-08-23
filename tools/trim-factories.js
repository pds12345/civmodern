#!/usr/bin/env node
/*
 * Trims the vendored UIComponents / UIContainers factories down to the components and
 * containers that are actually vendored (see tools/owo-vendor-manifest.txt). owo's
 * factory classes reference every component it ships, most of which civmodern prunes.
 *
 *   node tools/trim-factories.js <javaRoot>
 *
 * Run after tools/vendor-owo.js. Idempotent.
 */
const fs = require('fs');
const path = require('path');

const [, , root] = process.argv;
if (!root) { console.error('usage: node tools/trim-factories.js <javaRoot>'); process.exit(2); }

// Types that survive the prune; a factory mentioning anything else goes.
const KEPT = new Set([
  'ButtonComponent', 'TextBoxComponent', 'LabelComponent', 'BoxComponent',
  'SpacerComponent', 'VanillaWidgetComponent',
  'GridLayout', 'FlowLayout', 'WrappingParentUIComponent',
]);
const PRUNED = /\b(TextAreaComponent|EntityComponent|ItemComponent|BlockComponent|CheckboxComponent|SliderComponent|DiscreteSliderComponent|SpriteComponent|TextureComponent|DropdownComponent|SlimSliderComponent|SmallCheckboxComponent|StackLayout|ScrollContainer|CollapsibleContainer|OverlayContainer|DraggableContainer)\b/;

function trim(file) {
  const src = fs.readFileSync(file, 'utf8');
  const lines = src.split(/\r?\n/);
  const out = [];
  let removed = 0;

  for (let i = 0; i < lines.length; i++) {
    if (!/^\s*public static .*\(/.test(lines[i]) || !PRUNED.test(lines[i])) { out.push(lines[i]); continue; }

    // Drop the javadoc and annotations that belong to this factory.
    while (out.length && /^\s*@\w/.test(out[out.length - 1])) out.pop();
    if (out.length && /\*\/\s*$/.test(out[out.length - 1])) {
      while (out.length && !/^\s*\/\*/.test(out[out.length - 1])) out.pop();
      if (out.length) out.pop();
    }
    while (out.length && out[out.length - 1].trim() === '') out.pop();

    let depth = 0, started = false, j = i;
    for (; j < lines.length; j++) {
      for (const ch of lines[j]) {
        if (ch === '{') { depth++; started = true; }
        else if (ch === '}') depth--;
      }
      if (started && depth <= 0) break;
    }
    i = j;
    removed++;
    out.push('');
  }

  // Drop imports of the pruned types only. A general "is this still referenced?" test is
  // tempting but strips imports the surviving factories need if it is even slightly wrong.
  const kept = out.filter((l) => {
    const m = l.match(/^import\s+(?:static\s+)?[\w.]*\.(\w+);/);
    return !m || KEPT.has(m[1]) || !PRUNED.test(m[1]);
  });

  fs.writeFileSync(file, kept.join('\n').replace(/\n{3,}/g, '\n\n'));
  return removed;
}

let total = 0;
for (const name of ['component/UIComponents.java', 'container/UIContainers.java']) {
  const p = path.join(root, name);
  if (!fs.existsSync(p)) { console.error('missing ' + p); process.exit(1); }
  const n = trim(p);
  console.log(name + ': removed ' + n + ' factory method(s)');
  total += n;
}
console.log('trimmed ' + total + ' factories');
