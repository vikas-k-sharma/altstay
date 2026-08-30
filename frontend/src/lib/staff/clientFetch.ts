/**
 * `fetch` for console Client Components, with one behaviour layered on top: any 401 means the
 * session died (an expired session and `missing-tenant` are both 401s the BFF treats alike —
 * phase-6 §2.4), and a dead session must never render as an empty list or a silent failure. A
 * hard navigation to login is deliberate here — there is no shared client-side router context to
 * thread through every call site, and a full reset is the right response to a dead session anyway.
 */
export async function consoleFetch(input: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(input, init);

  if (response.status === 401) {
    const next = window.location.pathname + window.location.search;
    // Not useRouter().push(): this is a plain utility, not a component, so the hook isn't
    // available — and a hard reset is the right response to a dead session anyway.
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.assign(`/console/login?next=${encodeURIComponent(next)}`);
  }

  return response;
}
