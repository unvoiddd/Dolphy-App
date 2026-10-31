// Fixture for `no-class-selector-queries`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

it("keeps the queue rows in order", () => {
  const { container } = render(QueuePanel);
  const handle = container.querySelector<HTMLFieldSetElement>(".agent-queue-item");
  vi.spyOn(handle, "getBoundingClientRect").mockReturnValue(rect);
  fireEvent.dragStart(handle);
  expect(container.querySelector("[data-agent-id='chief']")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Send" }).closest("section")).toBeInTheDocument();
  expect(container.querySelector(".agent-queue-item")).toBeInTheDocument(); // flag
  expect(container.querySelector<HTMLElement>(".agent-queue-item")).toBeInTheDocument(); // flag
  expect(Array.from(container.querySelectorAll(".agent-queue-item"))).toHaveLength(3); // flag
  expect(screen.getByRole("button", { name: "Send" }).closest(".agent-queue-item")).toBeVisible(); // flag
  within(container.querySelector<HTMLElement>(".agent-queue-item")).getByRole("button"); // flag
  expect(container.querySelector('.agent-queue-attachment img[src^="data:image/png"]')).toBeVisible(); // flag
  // Known not caught: the assertion is a statement away from the query, and
  // GritQL cannot follow the binding. Left here so the gap stays visible.
  const row = container.querySelector<HTMLElement>(".agent-queue-item");
  expect(row).toBeInTheDocument();
});
