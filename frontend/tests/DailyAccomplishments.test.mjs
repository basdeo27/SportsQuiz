import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import ts from 'typescript';

const require = createRequire(import.meta.url);
const source = readFileSync(new URL('../src/DailyAccomplishments.tsx', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS }
}).outputText;
const componentModule = { exports: {} };
new Function('require', 'module', 'exports', compiled)(require, componentModule, componentModule.exports);
const { DailyAccomplishments } = componentModule.exports;
const render = (accomplishments) => renderToStaticMarkup(createElement(DailyAccomplishments, { accomplishments }));

test('renders the authored accomplishments with a distinct medal breakdown', () => {
  const entries = JSON.parse(readFileSync(new URL('../../src/main/resources/data/daily/2026-10.json', import.meta.url), 'utf8'));
  const hint = entries.find((entry) => entry.date === '2026-10-24').hints.find((hint) => hint.type === 'ACCOMPLISHMENTS');
  const html = render(hint.accomplishments);
  assert.match(html, /Ted Lindsay Award/);
  assert.match(html, /NHL All-Star/);
  assert.match(html, /Olympic selections/);
  assert.match(html, /aria-label="1 silver medal"/);
  assert.match(html, /aria-label="2 bronze medals"/);
  assert.doesNotMatch(html, /Markus|Näslund|Naslund/);
});

test('the same panel supports another sport without player-specific display logic', () => {
  const html = render({ label: 'International honours', entries: [
    { kind: 'TROPHY', name: 'World Cup', count: 2 },
    { kind: 'MEDALS', name: 'Olympic medals', count: 1, medals: [{ medal: 'GOLD', count: 1 }] }
  ] });
  assert.match(html, /International honours/);
  assert.match(html, /World Cup/);
  assert.match(html, /aria-label="1 gold medal"/);
});

test('author-supplied labels and details are escaped as text', () => {
  const html = render({ label: 'Honours', entries: [
    { kind: 'ALL_STAR', name: '<script>not markup</script>', count: 3, detail: '<img src=x onerror=alert(1)>' }
  ] });
  assert.match(html, /&lt;script&gt;/);
  assert.doesNotMatch(html, /<script>|<img src=x/);
});
