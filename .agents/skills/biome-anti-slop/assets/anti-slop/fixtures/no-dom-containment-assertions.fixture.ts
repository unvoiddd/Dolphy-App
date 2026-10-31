// Fixture for `no-dom-containment-assertions`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

it("shows the memory inside the modal", () => {
  const modal = screen.getByRole("dialog", { name: "Memories" });
  expect(modal).toBeInTheDocument();
  expect(within(modal).getByText("Uses metric units.")).toBeInTheDocument();
  expect(modal).toContainElement(screen.getByText("Uses metric units.")); // flag
});
