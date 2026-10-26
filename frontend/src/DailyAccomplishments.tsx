import type { ReactNode } from "react";

export type AccomplishmentKind = "TROPHY" | "ALL_STAR" | "APPEARANCE" | "MEDALS";
export type MedalType = "GOLD" | "SILVER" | "BRONZE";
export type PlayerAccomplishments = {
  label: string;
  entries: {
    kind: AccomplishmentKind;
    name: string;
    count: number;
    detail?: string | null;
    medals?: { medal: MedalType; count: number }[];
  }[];
};

function AccomplishmentIcon({ kind }: { kind: AccomplishmentKind }) {
  const paths: Record<AccomplishmentKind, ReactNode> = {
    TROPHY: <><path d="M8 3h8v5a4 4 0 0 1-8 0V3Z" /><path d="M8 5H5v2a4 4 0 0 0 4 4m7-6h3v2a4 4 0 0 1-4 4m-3 1v6m-4 3h8m-6-3h4v3" /></>,
    ALL_STAR: <path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9L12 3Z" />,
    APPEARANCE: <><circle cx="12" cy="12" r="9" /><ellipse cx="12" cy="12" rx="4" ry="9" /><path d="M3 12h18M5 6.5h14M5 17.5h14" /></>,
    MEDALS: <><path d="m7 3 5 8 5-8M4 3h6m4 0h6" /><circle cx="12" cy="16" r="5" /><path d="m12 13 1 2 2 .3-1.5 1.4.4 2-1.9-1-1.9 1 .4-2L9 15.3l2-.3 1-2Z" /></>
  };
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[kind]}</svg>;
}

/** Presentation is driven entirely by the hint data, so other players/sports reuse the same panel. */
export function DailyAccomplishments({ accomplishments }: { accomplishments: PlayerAccomplishments }) {
  return <section className="accomplishments" aria-label={accomplishments.label}>
    <header className="accomplishments__header">
      <p className="eyebrow">The honours board</p>
      <h2>{accomplishments.label}</h2>
    </header>
    <ul className="accomplishments__grid">
      {accomplishments.entries.map((entry, index) => <li className="accomplishments__item" key={`${entry.kind}-${entry.name}-${index}`}>
        <div className="accomplishments__top">
          <span className="accomplishments__icon"><AccomplishmentIcon kind={entry.kind} /></span>
          <span className="accomplishments__count" aria-label={`${entry.count} ${entry.name}`}>{entry.count.toLocaleString()}<span aria-hidden="true">×</span></span>
        </div>
        <h3 className="accomplishments__name">{entry.name}</h3>
        {entry.detail && <p className="accomplishments__detail">{entry.detail}</p>}
        {!!entry.medals?.length && <ul className="accomplishments__medals" aria-label="Medal breakdown">
          {entry.medals.map(({ medal, count }) => <li className={`accomplishments__medal accomplishments__medal--${medal.toLowerCase()}`} key={medal}
            aria-label={`${count} ${medal.toLowerCase()} medal${count === 1 ? "" : "s"}`}>
            <span className="accomplishments__medal-dot" aria-hidden="true" />
            <strong>{count}</strong> {medal.charAt(0) + medal.slice(1).toLowerCase()}
          </li>)}
        </ul>}
      </li>)}
    </ul>
  </section>;
}
