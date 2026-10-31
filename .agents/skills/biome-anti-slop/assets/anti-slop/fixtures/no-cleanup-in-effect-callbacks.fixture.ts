// Fixture for `no-cleanup-in-effect-callbacks`. Every line the rule must reject carries a trailing
// `// flag`; every other line is correct code the rule must leave alone.
// Checked by `scripts/anti-slop-rules.test.ts`; not compiled and not linted.

function Component() {
  onCleanup(() => stopAnimation());

  createEffect(
    () => source(),
    (port) => {
      if (!port) return;
      return port.subscribe(load);
    },
  );

  createEffect(
    () => {
      onCleanup(() => abort());
      return source();
    },
    (value) => setValue(value),
  );

  createRenderEffect(
    () => element(),
    (element) => {
      const dispose = createRoot((dispose) => {
        onCleanup(() => element.remove());
        return dispose;
      });
      runWithOwner(owner, () => onCleanup(() => release()));
      return dispose;
    },
  );

  createRoot(() => {
    createEffect(
      () => source(),
      (port) => {
        onCleanup(port.subscribe(load)); // flag
      },
    );
  });

  createEffect(
    () => resetsAt(),
    (resetsAt) => {
      const timer = window.setTimeout(refresh, resetsAt);
      onCleanup(() => window.clearTimeout(timer)); // flag
    },
  );

  createEffect(
    () => tabId(),
    (tabId) => onCleanup(() => stop(tabId)), // flag
    { defer: true },
  );

  createEffect<string>(
    () => tabId(),
    (tabId) => {
      const stop = () => {
        onCleanup(() => release(tabId)); // flag
      };
      stop();
    },
  );

  createRenderEffect(
    () => element(),
    (element) => {
      element.addEventListener("click", suppress, true);
      onCleanup(() => element.removeEventListener("click", suppress, true)); // flag
    },
  );
}
