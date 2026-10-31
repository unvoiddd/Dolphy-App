// Fixture for `no-active-element-assertions`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

it("moves focus into the dialog", async () => {
  const dialog = screen.getByRole("dialog", { name: "Memories" });
  expect(dialog).toHaveFocus();
  expect(screen.getByRole("button", { name: "Close" })).toHaveFocus();
  expect(document.activeElement).toBe(dialog); // flag
  expect(document.activeElement?.tagName).toBe("BUTTON"); // flag
});
