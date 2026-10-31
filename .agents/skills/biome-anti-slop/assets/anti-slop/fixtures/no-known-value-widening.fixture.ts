// Fixture for `no-known-value-widening`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

const summary = readDelivery();
const typed: QueueDelivery = readDelivery();
const checked = readDelivery() satisfies QueueDelivery;
const widened: unknown = readDelivery(); // flag
const objected: object = readDelivery(); // flag

// Wrong, but owned elsewhere, so this rule must stay silent on them: `any` is an
// error under Biome's own `noExplicitAny`, and the dictionary types under
// `no-unsafe-dictionary-type`. Two rules reporting one line teaches the reader
// to skim the second.
const anyed: any = readDelivery();
const dictionary: Record<string, unknown> = readDelivery();
const looseDictionary: Record<string, any> = readDelivery();
