// Fixture for `no-title-copy-assertions`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

it("explains why the control is disabled", () => {
  const button = screen.getByRole("button", { name: "Send" });
  expect(button).toBeDisabled();
  expect(button).toHaveAttribute("aria-describedby", "send-hint");
  expect(button).toHaveAttribute("title", "The agent is busy"); // flag
});
