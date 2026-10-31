// Fixture for `no-module-mocking`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

vi.mock("electron");
vi.mock("electron-updater");
vi.mock("node:child_process");
vi.mock("node:fs/promises", () => ({ readFile: vi.fn() }));
const service = new AgentService({ clock: new FakeClock() });
vi.mocked(window.openbot.servers.list).mockResolvedValue([]);
vi.mock("./agent-service"); // flag
vi.doMock("../backend/team-api-server", () => ({ start: vi.fn() })); // flag
jest.mock("./agent-service"); // flag
