// Fixture for `no-style-assertions`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

it("hides the panel while it collapses", () => {
  const panel = screen.getByRole("region", { name: "Queue" });
  expect(panel).not.toBeVisible();
  expect(panel).toHaveStyle({ opacity: "0" }); // flag
  expect(getComputedStyle(panel).opacity).toBe("0"); // flag
  expect(getComputedStyle(panel, "::before").content).toBe("none"); // flag
});
