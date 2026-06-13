# todo

Living task list for the Anvil+Dagger → Metro migration. See `changelog.md` for shipped
history and `PLAN_METRO_MIGRATION.md` for the full design.

## In progress
_(none)_

## Open
- [ ] Overhaul B remaining tier (DeliveryHero purge, publishing) — NOT covered by the private-publish merge: rewrite `.github/workflows/publish-release.yml` (Sonatype→GHP, DH bot identity→yours, drop DH signing secrets, fix verify URLs); `settings.gradle.kts` `rootProject.name pd-whetstone→whetstone` + add the consumer-side GHP repo to `dependencyResolutionManagement`; `build-logic` convention plugin id `com.deliveryhero.whetstone.build`→helios66; `RELEASING.md` + `scripts/prepare-release.sh` + README badge
- [ ] Overhaul A (package rename `com.deliveryhero.whetstone.*`→helios66) — DEFERRED by owner; revisit only on public release or classpath-coexistence need (touches ~71 files + KSP hardcoded literals + .api baselines + manifest)

## Done
- [x] Mundus 0.7.0 → 0.8.0 bump + codified scenario harness (scripts/mundus-trace-scenarios.sh) — drives debug+release flows on the emulator, extracts the Perfetto trace, validates 16 invariants (debug heavy 5859 compose events; release lean 0 under R8 full mode; getMessage/scoreFor/statsBatch + @TraceArg debug.title/debug.todo + beginTokenWith debug.todoCount-int/debug.filter-string survive R8). All pass on 0.8.0 — done 2026-06-13
- [x] Merge local/private-publish into main (4aec94e) — wires private GitHub Packages publishing: library modules + gradle plugin + plugin-marker publish to helios66/whetstone-private; POM/flags repointed off DeliveryHero (mavenCentral/sign=false, POM_URL/SCM/DEVELOPER→helios66, all under whetstone-private). GHP publish tasks verified to materialize. Pushed to fork/main — done 2026-06-13
- [x] R8 full mode made explicit (`android.enableR8.fullMode=true`; was already AGP-8 default) + release re-verified on emulator — Whetstone/Metro DI survives (App/ViewModel(168 slices)/Worker injection all resolve, no lateinit/ClassNotFound/Metro errors); Mundus survives (491 slices, 0 compose with flag off; `@TraceArg` debug.title×19/debug.todo×6 across the suspend hop + `beginTokenWith` debug.todoCount=3 int/debug.filter=all string land in correct Perfetto columns) — done 2026-06-13
- [x] Mundus 0.6.0 → 0.7.0 bump + emulator trace test — 0.7.0 ships the requested opt-out: `mundus { composeTracing }` build flag (default true; `-Pmundus.composeTracing=false` drops the compose-tracing dep) + runtime kill switch `-Dmundus.composeTracing=false`. Replaced the `configurations…exclude` hack with the official flag. Verified debug 7630 compose events vs release (flag off) 0; `@TraceArg`/`beginTokenWith` metadata survive R8 in both — done 2026-06-13
- [x] Whetstone dependency-graph (Mermaid) feature — Phase 1 (per-module) + Phase 2 (whole-app aggregation) + renderer unit tests; verified on :sample (7 scopes/8 contributions) and :sample-library — done 2026-06-13 (see docs/dep-graph-spec.md)
- [x] Stage 9: emulator parity run on Pixel 6a — Application/ViewModel(Compose + delegate)/View/Service/Worker injection all verified working; nested App→Activity→View graph extensions resolve; no crashes — done 2026-06-12
- [x] Stage 10: configuration-cache-test updated — drops removed-proguard test; adds 2 codegen tests asserting the graph + injector + instance contribution shapes; worker LoadClassTest retained; all pass — done 2026-06-12
- [x] Stage 11: README updated to Metro; binary-compat baseline regenerated (metro.hints excluded); dropped unused anvil/dagger/kapt/autoService catalog entries; full `./gradlew build` green — done 2026-06-12
- [x] Stage 1: version catalog — Kotlin 2.2.0→2.3.20, add Metro 1.2.0 + KSP 2.3.9 + kotlinpoet 2.3.0 — done 2026-06-12
- [x] Stage 2: runtime annotations + scopes (SingleIn/ForScope typealias to Metro; meta annotations kept) — done 2026-06-12
- [x] Stage 4: whetstone runtime — graphs/extensions/multibinding factories on Metro; :whetstone green — done 2026-06-12
- [x] Stage 5: whetstone-compose on Metro (source unchanged) — done 2026-06-12
- [x] Stage 6: whetstone-worker on Metro — done 2026-06-12
- [x] Stage 7: gradle plugin rewrite (metro+ksp, javax interop, eager ksp dep, drop anvil/proguard) — done 2026-06-12
- [x] Stage 3: whetstone-compiler → KSP SymbolProcessor — done 2026-06-12
- [x] Stage 8: samples build green — sample app DI graph assembles end-to-end on Metro; APK built — done 2026-06-12
- [x] Stage 0: baseline build green + Metro pattern spike (4/4 patterns validated: member-injector multibinding, graph extension + contributed factory, instance multibinding via `() -> Base`, **KSP-generated `@ContributesTo` merged by Metro**) — done 2026-06-12. Version trio locked: Kotlin 2.3.20 + Metro 1.2.0 + KSP 2.3.9.
