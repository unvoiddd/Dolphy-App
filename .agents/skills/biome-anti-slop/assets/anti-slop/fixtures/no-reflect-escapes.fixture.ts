// Fixture for `no-reflect-escapes`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

const name = delivery.recipientBotId;
const result = handler.call(owner, delivery);
const escaped = Reflect.get(delivery, "recipientBotId"); // flag
const applied = Reflect.apply(handler, owner, [delivery]); // flag
