// Fixture for `no-unsafe-dictionary-type`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

type Queue = Record<string, QueueDelivery>;
type ByIndex = Record<number, QueueDelivery>;
type Loose = Record<string, unknown>; // flag
type LooseNumber = Record<number, unknown>; // flag
type LooseSymbol = Record<symbol, unknown>; // flag
type LooseKey = Record<PropertyKey, unknown>; // flag
type Objected = Record<string, object>; // flag
type ObjectedKey = Record<PropertyKey, object>; // flag

// A key type this rule cannot enumerate. `no-known-value-widening` used to own
// these through a `Record<$key, …>` arm; the metavariable key is what lets that
// arm go without opening a hole.
type Aliased = Record<AgentId, unknown>; // flag
const inAnnotation: Record<AgentId, unknown> = readDeliveries(); // flag

// Wrong, but `noExplicitAny` already reports it as an error.
type Anyed = Record<string, any>;
