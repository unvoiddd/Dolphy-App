// Fixture for `no-inner-html-assertions`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

it("renders the message body", () => {
  const host = document.createElement("div");
  host.innerHTML = "<p>Ship it</p>";
  expect(screen.getByText("Ship it")).toBeInTheDocument();
  expect(host.innerHTML).toContain("Ship it"); // flag
  expect(host.outerHTML).toContain("Ship it"); // flag
});
