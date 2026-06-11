# todo

Living task list for the Anvil+Dagger → Metro migration. See `changelog.md` for shipped
history and `PLAN_METRO_MIGRATION.md` for the full design.

## In progress
- [ ] Stage 9: emulator end-to-end parity run on Pixel 6a (App/Activity/Service/View/ViewModel/Fragment/Worker) — started 2026-06-12

## Open
- [ ] Stage 10: port test suites (codegen→KSP, configuration-cache, worker)
- [ ] Stage 11: docs (README/RELEASING) + binary-compat baseline + drop unused anvil/dagger catalog entries

## Done
- [x] Stage 1: version catalog — Kotlin 2.2.0→2.3.20, add Metro 1.2.0 + KSP 2.3.9 + kotlinpoet 2.3.0 — done 2026-06-12
- [x] Stage 2: runtime annotations + scopes (SingleIn/ForScope typealias to Metro; meta annotations kept) — done 2026-06-12
- [x] Stage 4: whetstone runtime — graphs/extensions/multibinding factories on Metro; :whetstone green — done 2026-06-12
- [x] Stage 5: whetstone-compose on Metro (source unchanged) — done 2026-06-12
- [x] Stage 6: whetstone-worker on Metro — done 2026-06-12
- [x] Stage 7: gradle plugin rewrite (metro+ksp, javax interop, eager ksp dep, drop anvil/proguard) — done 2026-06-12
- [x] Stage 3: whetstone-compiler → KSP SymbolProcessor — done 2026-06-12
- [x] Stage 8: samples build green — sample app DI graph assembles end-to-end on Metro; APK built — done 2026-06-12
- [x] Stage 0: baseline build green + Metro pattern spike (4/4 patterns validated: member-injector multibinding, graph extension + contributed factory, instance multibinding via `() -> Base`, **KSP-generated `@ContributesTo` merged by Metro**) — done 2026-06-12. Version trio locked: Kotlin 2.3.20 + Metro 1.2.0 + KSP 2.3.9.
