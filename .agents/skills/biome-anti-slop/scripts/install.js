#!/usr/bin/env node
import { cpSync, existsSync, readdirSync, readFileSync, rmSync } from "node:fs";
import { dirname, isAbsolute, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
const skillRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const source = resolve(skillRoot, "assets/anti-slop");
const arguments_ = process.argv.slice(2);
const force = arguments_.includes("--force");
const unknownOption = arguments_.find((argument) => argument.startsWith("--") && argument !== "--force");
const destinations = arguments_.filter((argument) => !argument.startsWith("--"));
if (unknownOption) {
    throw new Error(`Unknown option: ${unknownOption}`);
}
if (destinations.length > 1) {
    throw new Error("Pass at most one destination.");
}
const destinationArgument = destinations[0] ?? "tools/biome/anti-slop";
if (isAbsolute(destinationArgument)) {
    throw new Error("Destination must be relative to the target repository.");
}
const repositoryRoot = resolve(process.cwd());
const destination = resolve(repositoryRoot, destinationArgument);
const relativeDestination = relative(repositoryRoot, destination);
if (relativeDestination === "" ||
    relativeDestination === "." ||
    relativeDestination.startsWith("..") ||
    isAbsolute(relativeDestination)) {
    throw new Error("Destination must stay inside the target repository.");
}
if (existsSync(destination) && !force) {
    console.error(`Refusing to overwrite ${destination}. Re-run with --force only after reviewing existing files.`);
    process.exit(1);
}
if (force) {
    rmSync(destination, { recursive: true, force: true });
}
cpSync(source, destination, { recursive: true });
console.log(`Copied Biome anti-slop rules to ${destination}`);
// Each rule declares the scope it belongs in, so the two plugin lists are read
// off the rules themselves rather than kept in a list here that can go stale.
const rulesDirectory = resolve(destination, "rules");
const scopes = { global: [], tests: [] };
for (const entry of readdirSync(rulesDirectory).sort()) {
    if (!entry.endsWith(".grit"))
        continue;
    const declared = /^\/\/ scope: (global|tests)$/m.exec(readFileSync(resolve(rulesDirectory, entry), "utf8"));
    if (!declared) {
        throw new Error(`${entry} declares no "// scope:" line.`);
    }
    scopes[declared[1]].push(`${destinationArgument}/rules/${entry}`);
}
const list = (paths) => paths.map((path) => `    "./${path}"`).join(",\n");
console.log(`\nRegister these in the top-level "plugins" array:\n${list(scopes.global)}`);
console.log(`\nRegister these in a "plugins" array on an overrides entry for "**/*.test.ts" and "**/*.test.tsx":\n${list(scopes.tests)}`);
console.log(`\nEach rule declares its own severity. Keep it: "error" is for a pattern with no honest`);
console.log(`counter-example, "warn" for a judgement a pattern cannot make. Every rule ships a fixture`);
console.log(`in ${destinationArgument}/fixtures marking the lines it must reject with "// flag".`);
