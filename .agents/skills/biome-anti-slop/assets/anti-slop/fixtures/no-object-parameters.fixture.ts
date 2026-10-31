// Fixture for `no-object-parameters`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

export function encode(frame: TeamFrame): string {
  return JSON.stringify(frame);
}
export function widened(frame: object): string { // flag
  return JSON.stringify(frame);
}
export function spread(...frames: object[]): string {
  return frames.map(String).join("");
}
