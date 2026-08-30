// Every amount crossing the wire is a `long` minor unit plus an ISO 4217 code (phase-6 §7.1).
// Nothing else in the console divides or multiplies by 100 inline — most currencies have 2 minor
// digits, but not all (JPY has 0), so the exponent comes from Intl, never a hardcoded constant.

function minorDigits(currencyCode: string): number {
  const { maximumFractionDigits } = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: currencyCode,
  }).resolvedOptions();
  return maximumFractionDigits ?? 2;
}

export function formatMinor(amountMinor: number, currencyCode: string): string {
  const major = amountMinor / 10 ** minorDigits(currencyCode);
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currencyCode }).format(major);
}

export function parseMajor(input: string, currencyCode: string): number {
  const value = Number(input);
  if (!Number.isFinite(value)) {
    throw new Error(`Not a number: ${input}`);
  }
  return Math.round(value * 10 ** minorDigits(currencyCode));
}

/** The plain-number inverse of `parseMajor`, for pre-filling an editable amount input — no
 *  currency symbol or grouping, unlike `formatMinor`, which is for display only. */
export function minorToMajorInput(amountMinor: number, currencyCode: string): string {
  return String(amountMinor / 10 ** minorDigits(currencyCode));
}

// taxRateBps is entered as a percentage and stored as basis points (phase-6 §4.7) — the input
// shows 12, the request carries 1200. A factor-of-100 error here is a plausible and expensive
// bug, so the conversion lives in exactly these two functions and nowhere else.
export function percentToBps(percent: string): number {
  const value = Number(percent);
  if (!Number.isFinite(value)) {
    throw new Error(`Not a number: ${percent}`);
  }
  return Math.round(value * 100);
}

export function bpsToPercent(bps: number): string {
  return String(bps / 100);
}
