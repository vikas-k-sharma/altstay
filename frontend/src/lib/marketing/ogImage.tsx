import { ImageResponse } from 'next/og';

export const OG_SIZE = { width: 1200, height: 630 };
export const OG_CONTENT_TYPE = 'image/png';

// phase-7 §8.2 — one generated OG image per page, same visual system as the site (dark surface,
// green accent), built at request time from plain text so there is nothing checked in to go
// stale. No external font fetch: the offline build invariant (CLAUDE.md) must hold with no
// network access.
export function renderOgImage(eyebrow: string, title: string) {
  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          padding: '80px',
          background: '#0a0a0a',
          color: '#ededed',
          fontFamily: 'sans-serif',
        }}
      >
        <div style={{ fontSize: 28, color: '#a1a1aa', letterSpacing: 2, textTransform: 'uppercase' }}>
          AltStay OS
        </div>
        <div style={{ fontSize: 22, color: '#71717a', marginTop: 24 }}>{eyebrow}</div>
        <div style={{ fontSize: 56, fontWeight: 600, marginTop: 16, maxWidth: 980, lineHeight: 1.15 }}>
          {title}
        </div>
      </div>
    ),
    OG_SIZE
  );
}
