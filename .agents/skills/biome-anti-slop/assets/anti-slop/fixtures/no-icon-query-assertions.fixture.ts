// Fixture for `no-icon-query-assertions`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

it("labels the attachment control", () => {
  const { container } = render(QueuePanel);
  expect(screen.getByRole("button", { name: "Attach a file" })).toBeInTheDocument();
  expect(screen.getByRole("img", { name: "Agent avatar" })).toBeInTheDocument();
  expect(container.querySelector("svg")).toBeInTheDocument(); // flag
  expect(container.querySelector("img")).toBeInTheDocument(); // flag
  expect(container.querySelectorAll("svg")).toHaveLength(2); // flag
  expect(container.querySelectorAll("img")).toHaveLength(2); // flag
});
