import { useEffect, useState } from "react";

export type ShareResult = {
  mode: "daily" | "logos" | "faces";
  date: string;
  score: number;
  hintsUsed: number;
  elapsedSeconds: number;
  totalHints?: number;
  ranked?: boolean;
  gaveUp?: boolean;
  correctCount?: number;
  totalQuestions?: number;
};

const SITE = "https://funsportsquiz.com";
const whole = (value: number) => Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0;
export function formatShareTime(value: number): string {
  const seconds = whole(value);
  const minutes = Math.floor(seconds / 60);
  return minutes >= 60
    ? `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`
    : `${minutes}:${String(seconds % 60).padStart(2, "0")}`;
}

// A whitelist of result fields: answers, hints, account names and session IDs never enter a share.
export function resultSummary(result: ShareResult) {
  const daily = result.mode === "daily";
  const total = Math.max(1, Math.min(50, whole(result.totalHints ?? 5)));
  const used = daily ? Math.min(total, whole(result.hintsUsed)) : whole(result.hintsUsed);
  const title = daily ? `Daily challenge${result.ranked === false ? " · Unranked" : ""}`
    : `${result.mode === "logos" ? "Logo" : "Face"} quiz`;
  const status = daily ? (result.gaveUp ? "Not solved" : "Solved")
    : `${whole(result.correctCount ?? 0)}/${whole(result.totalQuestions ?? 0)} correct`;
  const cells = daily ? Array.from({ length: total }, (_, i) =>
    i >= used ? "unused" : !result.gaveUp && i === used - 1 ? "solved" : "revealed") : [];
  return {
    title, status, cells,
    date: /^\d{4}-\d{2}-\d{2}$/.test(result.date) ? result.date : "Unknown date",
    score: (result.gaveUp ? 0 : whole(result.score)).toLocaleString("en-GB"),
    hints: daily ? `${used}/${total}` : String(used),
    time: formatShareTime(result.elapsedSeconds),
  };
}

export function buildShareText(result: ShareResult): string {
  const summary = resultSummary(result);
  const strip = summary.cells.map(cell => cell === "solved" ? "🟩" : cell === "revealed" ? "🟨" : "⬜").join("");
  return [`Fun Sports Quiz · ${summary.title}`, summary.date, `${summary.status}${strip ? ` · ${strip}` : ""}`,
    `Score: ${summary.score}`, `Hints: ${summary.hints} · Time: ${summary.time}`, SITE].join("\n");
}

const xml = (value: string) => value.replace(/[&<>"']/g, char =>
  ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&apos;" }[char]!));

export function buildShareSvg(result: ShareResult): string {
  const s = resultSummary(result);
  const cellWidth = s.cells.length ? Math.min(64, (624 - (s.cells.length - 1) * 4) / s.cells.length) : 0;
  const blocks = s.cells.map((cell, i) => `<rect x="${48 + i * (cellWidth + 4)}" y="174" width="${cellWidth}" height="32" rx="5" fill="${cell === "solved" ? "#7ed6ad" : cell === "revealed" ? "#e9c66e" : "#303c4e"}"/>`).join("");
  return `<svg xmlns="http://www.w3.org/2000/svg" width="720" height="440" viewBox="0 0 720 440" role="img" aria-label="${xml(buildShareText(result))}">
  <rect width="720" height="440" rx="24" fill="#151c26"/>
  <path d="M24 0H696Q720 0 720 24V7H0V24Q0 0 24 0" fill="#ff7b6b"/>
  <g font-family="Arial, Helvetica, sans-serif" fill="#f3f5f7">
  <text x="48" y="53" font-size="22" font-weight="700" letter-spacing="2">FUN SPORTS QUIZ</text>
  <text x="672" y="53" font-size="22" text-anchor="end" fill="#a5b1c3">${xml(s.date)}</text>
  <text x="48" y="102" font-size="26" font-weight="700">${xml(result.mode === "daily" ? "Daily challenge" : s.title)}</text>
  <text x="48" y="143" font-size="22" fill="${result.gaveUp ? "#ff7b6b" : "#7ed6ad"}">${xml(s.status)}</text>
  ${blocks}
  <path d="M48 232H672" stroke="#303c4e"/>
  ${[ ["SCORE", s.score, 48], ["HINTS", s.hints, 286], ["TIME", s.time, 484] ].map(([label, value, x]) =>
    `<text x="${x}" y="275" font-size="22" fill="#a5b1c3" letter-spacing="2">${label}</text><text x="${x}" y="326" font-size="44" font-weight="700">${xml(String(value))}</text>`).join("")}
  <text x="48" y="398" font-size="18" fill="#a5b1c3">funsportsquiz.com</text>
  <text x="672" y="398" font-size="15" fill="#a5b1c3" text-anchor="end">Your turn?</text>
  </g></svg>`;
}

async function pngFile(svg: string, filename: string): Promise<File> {
  const url = URL.createObjectURL(new Blob([svg], { type: "image/svg+xml" }));
  try {
    const image = new Image();
    await new Promise<void>((resolve, reject) => { image.onload = () => resolve(); image.onerror = () => reject(new Error("Image unavailable")); image.src = url; });
    const canvas = document.createElement("canvas");
    canvas.width = 1440; canvas.height = 880;
    const context = canvas.getContext("2d");
    if (!context) throw new Error("Image unavailable");
    context.drawImage(image, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(value => value ? resolve(value) : reject(new Error("Image unavailable")), "image/png"));
    return new File([blob], filename, { type: "image/png" });
  } finally { URL.revokeObjectURL(url); }
}

export function ResultShare({ result }: { result: ShareResult }) {
  const text = buildShareText(result);
  const svg = buildShareSvg(result);
  const filename = `sports-quiz-${result.mode}-${resultSummary(result).date}.png`;
  const [prepared, setPrepared] = useState<{ svg: string; file: File } | null>(null);
  const [notice, setNotice] = useState("");
  const [manualCopy, setManualCopy] = useState(false);
  const [sharing, setSharing] = useState(false);
  const file = prepared?.svg === svg ? prepared.file : null;

  useEffect(() => {
    let cancelled = false;
    setNotice(""); setManualCopy(false);
    // Prepare before a click so file generation cannot consume mobile share activation.
    pngFile(svg, filename).then(file => { if (!cancelled) setPrepared({ svg, file }); })
      .catch(() => { if (!cancelled) setNotice("Image saving is unavailable. You can still copy your result."); });
    return () => { cancelled = true; };
  }, [svg, filename]);

  async function copy() {
    try {
      if (!navigator.clipboard?.writeText) throw new Error("Clipboard unavailable");
      await navigator.clipboard.writeText(text);
      setNotice("Result copied. Paste it into your chat or post.");
    } catch { setManualCopy(true); setNotice("Select and copy your result below."); }
  }

  async function share() {
    if (sharing) return;
    if (!navigator.share) { await copy(); return; }
    setSharing(true);
    try {
      if (file && navigator.canShare?.({ files: [file] }))
        await navigator.share({ files: [file], title: "Fun Sports Quiz", text });
      else await navigator.share({ title: "Fun Sports Quiz", text });
      setNotice("Share sheet closed.");
    } catch (error) {
      if (!(error instanceof Error && error.name === "AbortError")) {
        setManualCopy(true); setNotice("Sharing is unavailable here. Copy your result below or save the image.");
      }
    } finally { setSharing(false); }
  }

  function download() {
    if (!file) return;
    const url = URL.createObjectURL(file);
    const link = document.createElement("a"); link.href = url; link.download = filename; link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    setNotice("Image download started.");
  }

  return <div className="result-share" aria-label="Share your result">
    <img className="result-share__card" width="720" height="440"
      src={`data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`} alt={text} />
    <p className="result-share__caption">A result worth sharing. No answers, no spoilers.</p>
    <div className="result-share__actions">
      <button className="primary-button" onClick={share} disabled={sharing}>{sharing ? "Sharing…" : "Share result"}</button>
      <button className="ghost-button" onClick={copy}>Copy text</button>
      <button className="ghost-button" onClick={download} disabled={!file}>Save image</button>
    </div>
    <p className="result-share__notice" role="status" aria-live="polite">{notice}</p>
    {manualCopy && <label className="result-share__manual">Copy your result
      <textarea readOnly value={text} rows={7} onFocus={event => event.currentTarget.select()} />
    </label>}
  </div>;
}
