'use client';

import { useState } from 'react';

// The concierge composer autofocuses on mount, which is correct for a chat UI on its own page.
// Nested eagerly in an iframe on a long marketing page, that same autofocus makes the browser
// scroll the *outer* page to the iframe the instant it loads — an unsolicited jump nobody asked
// for. Click-to-load sidesteps it: focus only moves after the visitor has already clicked inside
// that exact spot, and it also keeps a second Next.js app instance off the page until asked for.
export function LiveDemoFrame() {
  const [loaded, setLoaded] = useState(false);

  if (!loaded) {
    return (
      <div className="flex h-[600px] flex-col items-center justify-center gap-4 bg-surface text-center">
        <p className="max-w-sm text-sm text-text-muted">
          The concierge demo loads here — the real thing, on the real route, not an embedded copy.
        </p>
        <button
          type="button"
          onClick={() => setLoaded(true)}
          className="rounded-full bg-accent px-5 py-2.5 text-sm font-medium text-accent-foreground hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        >
          Load the live demo
        </button>
      </div>
    );
  }

  return (
    <iframe
      src="/concierge"
      title="The AltStay concierge, live"
      className="h-[600px] w-full"
    />
  );
}
