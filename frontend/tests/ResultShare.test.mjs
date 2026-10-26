import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import ts from 'typescript';
const require = createRequire(import.meta.url);
const compiled = ts.transpileModule(readFileSync(new URL('../src/ResultShare.tsx', import.meta.url), 'utf8'), {
  compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS }
}).outputText;
const loaded = { exports: {} };
new Function('require', 'module', 'exports', compiled)(require, loaded, loaded.exports);
const { buildShareText, buildShareSvg, formatShareTime, ResultShare } = loaded.exports;
const daily = { mode: 'daily', date: '2026-10-25', score: 784, hintsUsed: 3, totalHints: 5, elapsedSeconds: 84, ranked: true };

test('daily text includes the challenge date and a spoiler-free hint strip', () => {
  const text = buildShareText({ ...daily, answer: 'Tom Brady', sessionId: 'private-session', displayName: 'Private Person' });
  assert.equal(text, 'Fun Sports Quiz · Daily challenge\n2026-10-25\nSolved · 🟨🟨🟩⬜⬜\nScore: 784\nHints: 3/5 · Time: 1:24\nhttps://funsportsquiz.com');
  assert.doesNotMatch(text, /Brady|private-session|Private Person/);
});

test('practice and give-up results cannot be mistaken for ranked wins', () => {
  const text = buildShareText({ ...daily, ranked: false, gaveUp: true });
  assert.match(text, /Unranked/);
  assert.match(text, /Not solved · 🟨🟨🟨⬜⬜/);
  assert.match(text, /Score: 0/);
  assert.doesNotMatch(text, /🟩/);
  assert.match(buildShareText({ ...daily, hintsUsed: 1 }), /🟩⬜⬜⬜⬜/);
  assert.match(buildShareText({ ...daily, hintsUsed: 5 }), /🟨🟨🟨🟨🟩/);
});

test('custom quizzes show their accuracy, hint count and final time without answers', () => {
  const text = buildShareText({ ...daily, mode: 'logos', correctCount: 8, totalQuestions: 10, hintsUsed: 2 });
  assert.match(text, /Logo quiz/);
  assert.match(text, /8\/10 correct/);
  assert.match(text, /Hints: 2 · Time: 1:24/);
  assert.doesNotMatch(text, /🟩|Practice/);
});

test('elapsed time stays unambiguous for zero, minutes and hours', () => {
  assert.equal(formatShareTime(0), '0:00');
  assert.equal(formatShareTime(65), '1:05');
  assert.equal(formatShareTime(3662), '1:01:02');
  assert.equal(formatShareTime(-5), '0:00');
  assert.equal(formatShareTime(NaN), '0:00');
});

test('visual and accessible preview contain the same final metrics and safe content', () => {
  const svg = buildShareSvg(daily);
  for (const value of ['784', '3/5', '1:24', '2026-10-25']) assert.ok(svg.includes(value));
  assert.doesNotMatch(buildShareSvg({ ...daily, date: '<script>alert(1)</script>' }), /<script>/);
  const html = renderToStaticMarkup(createElement(ResultShare, { result: daily }));
  assert.match(html, /Share result/);
  assert.match(html, /Copy text/);
  assert.match(html, /Save image/);
  assert.match(html, /role="status"/);
});

// Optional standalone preview for visual QA; no file is written during normal tests.
if (process.env.RESULT_SHARE_PREVIEW) writeFileSync(process.env.RESULT_SHARE_PREVIEW, buildShareSvg(daily));
