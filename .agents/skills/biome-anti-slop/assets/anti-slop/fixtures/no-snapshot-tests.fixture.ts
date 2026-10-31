// Fixture for `no-snapshot-tests`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

it("encodes the frame", () => {
  expect(encode(frame)).toEqual({ type: "turn/start", id: "turn-1" });
  expect(encode(frame)).toMatchSnapshot(); // flag
  expect(encode(frame)).toMatchInlineSnapshot(); // flag
  expect(encode(frame)).toMatchFileSnapshot("./frame.json"); // flag
  expect(() => encode(frame)).toThrowErrorMatchingSnapshot(); // flag
  expect(() => encode(frame)).toThrowErrorMatchingInlineSnapshot(); // flag
});
