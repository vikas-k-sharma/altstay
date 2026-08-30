import { describe, it, expect } from 'vitest';
import { formatMinor, parseMajor, percentToBps, bpsToPercent, minorToMajorInput } from './money';

describe('money', () => {
  it('formats and parses a two-decimal currency', () => {
    expect(formatMinor(1250000, 'INR')).toContain('12,500');
    expect(parseMajor('12500', 'INR')).toBe(1250000);
  });

  it('round-trips through parse then format', () => {
    const minor = parseMajor('99.50', 'USD');
    expect(minor).toBe(9950);
    expect(formatMinor(minor, 'USD')).toContain('99.50');
  });

  it('handles a zero-decimal currency without dividing by 100', () => {
    expect(parseMajor('500', 'JPY')).toBe(500);
    expect(formatMinor(500, 'JPY')).not.toContain('.');
  });

  it('throws on a non-numeric amount rather than silently coercing to 0', () => {
    expect(() => parseMajor('not a number', 'INR')).toThrow();
  });

  it('converts a tax percentage to basis points and back without a factor-of-100 slip', () => {
    expect(percentToBps('12')).toBe(1200);
    expect(bpsToPercent(1200)).toBe('12');
  });

  it('handles a fractional percentage', () => {
    expect(percentToBps('7.5')).toBe(750);
    expect(bpsToPercent(750)).toBe('7.5');
  });

  it('minorToMajorInput round-trips with parseMajor as a plain number, no currency symbol', () => {
    expect(minorToMajorInput(65000, 'INR')).toBe('650');
    expect(parseMajor(minorToMajorInput(65000, 'INR'), 'INR')).toBe(65000);
    expect(minorToMajorInput(500, 'JPY')).toBe('500');
  });
});
