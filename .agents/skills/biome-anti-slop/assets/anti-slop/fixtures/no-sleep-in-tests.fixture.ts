// Fixture for `no-sleep-in-tests`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

const fake = {
  respond: async () => {
    await new Promise((resolve) => setTimeout(resolve, 25));
    return { ok: true };
  },
};

it("answers the request", async () => {
  await Promise.race([client.request("ping"), new Promise((_, reject) => setTimeout(reject, 5_000))]);
  await new Promise((resolve) => setTimeout(resolve, 0));
  await vi.waitFor(() => expect(client.responses).toHaveLength(1));
  await new Promise((resolve) => setTimeout(resolve, 150)); // flag
  await new Promise<void>((resolve) => setTimeout(resolve, 150)); // flag
  await new Promise((resolve) => window.setTimeout(resolve, 50)); // flag
});
