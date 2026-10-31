// Fixture for `no-collections-in-stores`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

const readOperations = new Map<string, Promise<void>>();

const [collapsed, setCollapsed] = createStore({ sections: [] as string[], pending: false });
const [duplicating, setDuplicating] = createSignal<Set<string>>(new Set());
setDuplicating((current) => new Set(current).add(botId));

function reconcileRows(rows: readonly Row[]) {
  const byId = new Map(rows.map((row) => [row.id, row]));
  return byId;
}

const [seen, setSeen] = createStore({ ids: new Set<string>() }); // flag
const [index, setIndex] = createStore({ rows: new Map<string, Row>() }); // flag
const [nested, setNested] = createStore({ view: { ids: new Set<string>() } }); // flag
const [bare, setBare] = createStore({ ids: new Set() }); // flag
const [seeded, setSeeded] = createStore({ rows: new Map(rows.map((row) => [row.id, row])) }); // flag
const [typed, setTyped] = createStore<Seen>({ ids: new Set<string>() }); // flag
const [typedRows, setTypedRows] = createStore<Index>({ rows: new Map<string, Row>() }); // flag
const [shallow, setShallow] = createStore<Index>({ rows: new Map<string, Row>() }, { shallow: true }); // flag
