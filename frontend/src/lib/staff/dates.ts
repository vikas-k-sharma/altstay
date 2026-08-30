// The browser's timezone is never used for a business date (phase-6 §7.2).
// `new Date().toISOString().slice(0, 10)` is UTC and is the exact bug `property.timezone` was
// added to prevent — it must never be copied into anything that decides a business date.

/** Today's date in the *property's* timezone, as `YYYY-MM-DD` — the only definition of "today" a
 *  front desk recognises (phase-5 front-desk endpoint javadoc). */
export function propertyToday(timezone: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: timezone }).format(new Date());
}

function parseDateParts(value: string): [number, number, number] {
  const [year, month, day] = value.split('-').map(Number);
  return [year, month, day];
}

/** Half-open [checkIn, checkOut), matching the `daterange '[)'` the database uses. Dates stay
 *  strings end to end; this parses their digits directly rather than trusting how a `Date` object
 *  would interpret the string, which is exactly the kind of implicit timezone conversion §7.2
 *  warns about. */
export function nightsBetween(checkIn: string, checkOut: string): number {
  const [inYear, inMonth, inDay] = parseDateParts(checkIn);
  const [outYear, outMonth, outDay] = parseDateParts(checkOut);
  const inUtc = Date.UTC(inYear, inMonth - 1, inDay);
  const outUtc = Date.UTC(outYear, outMonth - 1, outDay);
  return Math.round((outUtc - inUtc) / 86_400_000);
}

/** `date` shifted by `days` (negative to go back), as `YYYY-MM-DD`. Pure UTC-normalized digit
 *  arithmetic, like `nightsBetween` — never touches the local timezone, so it can't shift the
 *  calendar day the way constructing a local `Date` from the string could. */
export function addDays(date: string, days: number): string {
  const [year, month, day] = parseDateParts(date);
  const shifted = new Date(Date.UTC(year, month - 1, day) + days * 86_400_000);
  const yyyy = shifted.getUTCFullYear();
  const mm = String(shifted.getUTCMonth() + 1).padStart(2, '0');
  const dd = String(shifted.getUTCDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

/** The first day of `date`'s month, as `YYYY-MM-DD`. */
export function startOfMonth(date: string): string {
  const [year, month] = parseDateParts(date);
  return `${year}-${String(month).padStart(2, '0')}-01`;
}

/** The last day of `date`'s month, as `YYYY-MM-DD` — day 0 of the *next* month, computed with the
 *  same UTC-normalized arithmetic as `addDays` so a short/long month or a leap February is never
 *  guessed at. */
export function endOfMonth(date: string): string {
  const [year, month] = parseDateParts(date);
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`;
}

/**
 * A display string for a stay range, e.g. "Aug 30 – Sep 2, 2026". Deliberately takes no timezone:
 * these are pure calendar dates with no time component, so the only way to render them safely is
 * to build a `Date` whose *local* year/month/day match the input digits exactly, then format with
 * the runtime's own local zone — the local-to-local round trip is guaranteed lossless regardless
 * of what that zone is. Reinterpreting the string as a UTC instant and re-projecting it into a
 * given timezone is the same bug `propertyToday` exists to prevent, just moved into a formatter.
 */
export function formatStayRange(checkIn: string, checkOut: string): string {
  const [inYear, inMonth, inDay] = parseDateParts(checkIn);
  const [outYear, outMonth, outDay] = parseDateParts(checkOut);
  const inDate = new Date(inYear, inMonth - 1, inDay);
  const outDate = new Date(outYear, outMonth - 1, outDay);

  const sameYear = inYear === outYear;
  const inFormatter = new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: sameYear ? undefined : 'numeric',
  });
  const outFormatter = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' });

  return `${inFormatter.format(inDate)} – ${outFormatter.format(outDate)}`;
}
