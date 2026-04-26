# Investigating `# conflict` and `# override` entries

After a successful run with the default `skipContentIdenticalDuplicates = true`,
Highlander leaves only entries that genuinely need attention in the baseline:

- `# override` — your app module is one of the sources. AAPT's "last wins"
  rule means your copy is what ships.
- `# conflict` — multiple external dependencies declare the same key with
  divergent bytes. AGP picks one by priority order; everything else loses.

This guide is the playbook for inspecting either kind of entry. The goal is
to decide, per entry, whether it is a vendor-intentional layering, a real
risk, or harmless dead code at runtime.

## Six-step workflow

### 1. Read the source coordinates

The first signal is in the entry itself.

- **Same group across all sources** (e.g. all `androidx.media3:*`, or all
  `com.facebook.android:*`) → almost always an intentional internal
  layering: a parent SDK overrides a child it bundles.
- **Different groups** (e.g. `androidx.media3:media3-ui` vs
  `com.google.android.exoplayer:exoplayer-ui`) → unrelated SDKs colliding by
  name. Higher chance of real risk; continue to step 4.
- **One source is a `:project:path` (your module)** → `# override`. The
  later steps still apply, but your copy is the answer; investigate whether
  that copy is intentional or stale.

### 2. Locate the AARs in the Gradle cache

Gradle resolves every artifact under a deterministic path:

```
~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/<sha1>/<artifact>-<version>.aar
```

The inner `<sha1>` directory is content-addressed and changes per resolution.
Use `find` to skip it:

```bash
find ~/.gradle/caches/modules-2/files-2.1 \
    -type f -name "<artifact>-<version>.aar"
```

For a JAR-only dependency (when investigating a `# conflict` from the
classes scan), substitute `.jar` for `.aar`.

### 3. Extract and diff the actual file

The path inside an AAR depends on which scan produced the entry. Map the
baseline key like this:

| Scan / baseline shape                                         | Path inside the AAR                                            |
|---------------------------------------------------------------|----------------------------------------------------------------|
| `resources` — `<typeDir>/<name>` with `(.ext)` suffix         | `res/<typeDir>/<name><ext>`                                    |
| `resources` — nine-patch (key has `.9` stripped, ext is `.png`) | `res/<typeDir>/<name>.9.png` (note the literal `.9`)         |
| `assets` — `<relative/path>`                                  | `assets/<relative/path>`                                       |
| `nativeLibs` — `<abi>/<lib>.so`                               | `jni/<abi>/<lib>.so`                                           |
| `classes` — fully qualified class name (dots)                 | inside `classes.jar` (a member of the AAR), `<dot/path>.class` |
| `valuesResources` — `<qualifier>/<type>/<name>`               | **No 1:1 file mapping** — see note below                       |

The `valuesResources` scan reports XML entries (`<string>`, `<color>`, `<style>`,
…) that AAR packagers merge into a small number of files under
`res/<qualifier>/`. To diff a specific entry, dump and compare the resource
table instead of trying to extract a single line:

```bash
$ANDROID_HOME/build-tools/<latest>/aapt2 dump resources <artifact>.aar
```

For everything else, a 10-line script extracts both files and prints SHA-256
side by side:

```bash
python3 - <<'PY'
import hashlib, zipfile

aars = [
    "<absolute path to first AAR>",
    "<absolute path to second AAR>",
]
inner = "res/layout/exo_player_view.xml"   # adjust per entry

for aar in aars:
    with zipfile.ZipFile(aar) as z, z.open(inner) as f:
        data = f.read()
        print(aar, len(data), hashlib.sha256(data).hexdigest()[:12])
PY
```

If the SHA-256 values are identical, the entry should not be appearing as
`# conflict` in the first place — Highlander already filters byte-identical
duplicates. Confirm `skipContentIdenticalDuplicates` is at its default
value, then file an issue with the diff if the mismatch is real.

If they differ, you have a real divergence — continue.

### 4. Trace why each artifact is on the classpath

The dependency graph tells you whether two sources are *meant* to coexist:

```bash
./gradlew :app:dependencyInsight \
    --configuration releaseRuntimeClasspath \
    --dependency <group>:<artifact>
```

Typical patterns:

- A direct dependency from `:app` and a transitive from another SDK →
  intentional choice, but worth confirming the transitive version isn't
  silently lagging.
- Two transitive chains under the same vendor (`A → B → C`, all under one
  group) → strong signal of an intentional vendor override; the entry is
  likely safe to keep in the baseline as-is.
- Two unrelated transitive chains → highest-risk pattern; both SDKs ship
  the resource, neither knows about the other.

### 5. Map the resource to runtime usage

AGP's pick only matters if both sides are actually exercised. Find each
SDK's entry-point and grep the app code for it:

```bash
git grep -nE "ExoPlayer\.Builder|androidx\.media3\.exoplayer\.ExoPlayer" app/ feature/
```

If only one SDK's API is reachable from your code, the loser of AGP's
priority is dead code at runtime — the conflict is harmless layout noise.
If both APIs are reachable, the `# conflict` is a real surface that warrants
testing against both code paths or excluding one.

### 6. Classify and act

| Symptom                                                                   | Classification                  | Recommended action                                                                                            |
|---------------------------------------------------------------------------|---------------------------------|---------------------------------------------------------------------------------------------------------------|
| Same-group transitive chain; only the parent SDK is exercised             | Vendor-intentional override     | Leave entry in baseline. Drift detection still catches new files added by upgrades.                           |
| Different vendors; both SDK APIs are exercised at runtime                 | Real risk                       | Investigate further. Consider aligning versions, isolating one SDK, or `exclude` directives in `dependencies` block. |
| Different vendors; only one SDK's API is exercised                        | Latent risk                     | Document the decision in a code comment. Re-evaluate on each SDK upgrade.                                     |
| Resource never referenced at runtime (e.g. `xml/ad_services_config`)      | Likely no-op                    | Leave in baseline; bytes will diff on the next upgrade if the SDK changes its declaration.                    |
| Same `<file>` from `:app` shadowing a library                             | Intentional override            | Leave; the entry exists in baseline so an unintentional removal of your override gets caught.                  |

## Limits

- This guide is procedural, not automated. Highlander does not classify
  intent — that requires app-specific knowledge (which SDK APIs are wired
  in, deployment context, etc.).
- Severity is a judgement call. The matrix above is a starting point, not a
  verdict.
- For `valuesResources`, byte-identical content cannot be detected today
  (the scan is entry-keyed, not file-hashed). Any `# conflict` there is
  surfaced regardless of value equivalence.

## See also

- [README.md](../README.md) — installation, configuration, and the
  classification matrix.
- [docs/setup-guide.md.txt](setup-guide.md.txt) — AI Agent setup guide.
- [CLAUDE.md](../CLAUDE.md) — repository-level architecture for code
  contributors.
