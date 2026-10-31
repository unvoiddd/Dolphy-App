// Fixture for `no-testid-queries`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

it("finds the send control", async () => {
  expect(screen.getByRole("button", { name: "Send" })).toBeInTheDocument();
  expect(screen.getByLabelText("shell controller state")).toBeInTheDocument();
  expect(screen.getByTestId("send")).toBeInTheDocument(); // flag
  expect(screen.queryByTestId("send")).toBeNull(); // flag
  expect(await screen.findByTestId("send")).toBeInTheDocument(); // flag
  expect(screen.getAllByTestId("send")).toHaveLength(1); // flag
  expect(screen.queryAllByTestId("send")).toHaveLength(1); // flag
  expect(await screen.findAllByTestId("send")).toHaveLength(1); // flag
});
