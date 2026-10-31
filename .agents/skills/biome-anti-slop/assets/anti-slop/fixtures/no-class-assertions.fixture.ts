// Fixture for `no-class-assertions`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

it("disables the control while the turn runs", () => {
  const button = screen.getByRole("button", { name: "Send" });
  expect(button).toBeDisabled();
  expect(button).toHaveClass("ui-button-disabled"); // flag
});
