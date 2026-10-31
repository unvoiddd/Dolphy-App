// Fixture for `no-dom-traversal-assertions`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

it("renders the queue rows", () => {
  const row = screen.getByRole("group", { name: "Queued message 1: Ship it" });
  expect(row).toBeVisible();
  expect(row.parentElement).toBeVisible(); // flag
  expect(row.firstElementChild).toBeVisible(); // flag
  expect(row.nextElementSibling).toBeVisible(); // flag
  expect(row.previousElementSibling).toBeVisible(); // flag
});
