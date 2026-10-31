// Fixture for `no-unknown-returns`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

export function decodeFrame(raw: string): TeamFrame | null {
  return parse(raw);
}
export function leaky(raw: string): unknown { // flag
  return JSON.parse(raw);
}
export function leakyAsync(raw: string): Promise<unknown> { // flag
  return Promise.resolve(JSON.parse(raw));
}
export const leakyArrow = (raw: string): unknown => JSON.parse(raw); // flag
export const leakyArrowAsync = (raw: string): Promise<unknown> => Promise.resolve(raw); // flag
