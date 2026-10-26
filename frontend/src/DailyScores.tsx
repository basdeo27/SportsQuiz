import { apiFetch } from "./api";
import { useEffect, useState } from "react";

export type DailyScore = {
  date: string;
  displayName: string;
  registered: boolean;
  score: number;
  elapsedSeconds: number;
  hintsRevealed: number;
  completedAtMillis: number;
  gaveUp: boolean;
};

type ScorePage = { scores: DailyScore[]; nextCursor: string | null };

export function DailyScores({ apiBase, date, signedIn = false, history = false }: {
  apiBase: string; date?: string; signedIn?: boolean; history?: boolean;
}) {
  const [rows, setRows] = useState<DailyScore[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refresh, setRefresh] = useState(0);
  const [ownScore, setOwnScore] = useState<DailyScore | null>(null);
  const endpoint = history ? `${apiBase}/daily/history` : `${apiBase}/daily/leaderboard`;

  useEffect(() => {
    setOwnScore(null);
    if (history || !signedIn || !date) return;
    const controller = new AbortController();
    apiFetch(`${apiBase}/daily/scores/me?${new URLSearchParams({ date })}`, {
      signal: controller.signal
    }).then(async (response) => {
      if (!response.ok) return;
      const text = await response.text();
      if (!controller.signal.aborted && text) setOwnScore(JSON.parse(text) as DailyScore | null);
    }).catch(() => {
      // The public standings remain usable if the optional personal score is unavailable.
    });
    return () => controller.abort();
  }, [apiBase, date, history, signedIn, refresh]);

  useEffect(() => {
    const controller = new AbortController();
    setRows([]);
    setCursor(null);
    setError(null);
    setLoading(true);
    const params = new URLSearchParams(date && !history ? { date } : {});
    apiFetch(`${endpoint}?${params}`, {
      signal: controller.signal
    }).then(async (response) => {
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "Could not load scores.");
      if (!controller.signal.aborted) {
        setRows((data as ScorePage).scores);
        setCursor((data as ScorePage).nextCursor);
      }
    }).catch((err) => {
      if (!controller.signal.aborted) setError(err instanceof Error ? err.message : "Could not load scores.");
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [endpoint, date, history, signedIn, refresh]);

  // Keep an old request from appending to a new date/account's results.
  const [pageRequest, setPageRequest] = useState<AbortController | null>(null);
  useEffect(() => () => pageRequest?.abort(), [pageRequest, endpoint, date, signedIn]);

  const loadMore = async () => {
    if (!cursor || loading) return;
    const controller = new AbortController();
    setPageRequest(controller);
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams({ cursor });
      if (date && !history) params.set("date", date);
      const response = await apiFetch(`${endpoint}?${params}`, {
        signal: controller.signal
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "Could not load scores.");
      if (!controller.signal.aborted) {
        setRows((previous) => [...previous, ...(data as ScorePage).scores]);
        setCursor((data as ScorePage).nextCursor);
      }
    } catch (err) {
      if (!controller.signal.aborted) setError(err instanceof Error ? err.message : "Could not load scores.");
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  };

  return <div className="daily-scores" aria-live="polite">
    {ownScore && <div className="daily-scores__own"><span>Your score for this challenge</span><strong>{ownScore.score.toLocaleString()} <span>pts</span></strong></div>}
    {!history && <p className="supporting">Highest score first. Ties go to the earliest finish. New scores may take a moment to appear.</p>}
    {rows.length > 0 && <div className="daily-scores__scroll"><table className="daily-scores__table">
      <caption className="visually-hidden">{history ? "Your daily challenge history" : `Daily challenge standings for ${date}`}</caption>
      <thead><tr>{!history && <th scope="col">#</th>}<th scope="col">{history ? "Challenge" : "Player"}</th>
        <th scope="col">Score</th><th scope="col" className="daily-scores__optional">Hints</th><th scope="col">Time</th></tr></thead>
      <tbody>{rows.map((row, index) => {
        const isOwn = ownScore?.date === row.date && ownScore?.completedAtMillis === row.completedAtMillis
          && ownScore?.displayName === row.displayName && ownScore?.score === row.score;
        return <tr key={`${row.date}-${row.completedAtMillis}-${index}`} className={isOwn ? "is-own-score" : undefined}>
        {!history && <td><span className={`rank-number${index < 3 ? ` rank-number--${index + 1}` : ""}`}>{index + 1}</span></td>}
        <td>{history ? row.date : <>{row.displayName}</>}
          {isOwn && <span className="daily-scores__badge">You</span>}
          {row.gaveUp && <span className="daily-scores__badge">Gave up</span>}</td>
        <td><strong>{row.score.toLocaleString()}</strong></td><td className="daily-scores__optional">{row.hintsRevealed}</td><td>{row.elapsedSeconds}s</td>
      </tr>; })}</tbody>
    </table></div>}
    {!loading && !error && rows.length === 0 && <p className="daily-scores__empty">{history ? "Your story starts with your first challenge. Play the daily to begin your history." : "No scores on the board for this day. A new challenge brings a new chance to set the pace."}</p>}
    {error && <p className="message error" role="alert">{error}</p>}
    <div className="quiz__actions">
      {cursor && <button className="ghost-button" disabled={loading} onClick={loadMore}>Load more</button>}
      <button className="ghost-button" disabled={loading} onClick={() => setRefresh((value) => value + 1)}>Refresh</button>
      {loading && <span className="supporting">Loading scores...</span>}
    </div>
  </div>;
}
