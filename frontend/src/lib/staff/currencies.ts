// A short curated list, not free text (phase-6 §4.7) — currency is one of the two fields phase-5
// §2 gave no default for, specifically because a wrong one looks right. Covers the hostel
// markets this product targets, including a zero-decimal currency (VND) so the picker itself
// doesn't quietly assume two decimal places anywhere upstream of `money.ts`.
export const CURATED_CURRENCIES = [
  'INR',
  'USD',
  'EUR',
  'GBP',
  'AUD',
  'THB',
  'VND',
  'IDR',
  'PHP',
  'JPY',
] as const;
