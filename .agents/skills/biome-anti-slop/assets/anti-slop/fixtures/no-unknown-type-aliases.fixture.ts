// Fixture for `no-unknown-type-aliases`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

type TeamFrame = { readonly type: string };
type RawFrame = unknown; // flag
