# todo

Living task list for the Anvil+Dagger → Metro migration. See `changelog.md` for shipped
history and `PLAN_METRO_MIGRATION.md` for the full design.

## In progress
- [ ] Stage 1: version catalog — add Metro 1.2.0 + KSP 2.3.9, bump Kotlin 2.2.0→2.3.20, drop dagger/anvil/kapt — started 2026-06-12

## Open
- [ ] Stage 2: runtime annotations + scopes (SingleIn/ForScope typealias to Metro; keep meta annotations)
- [ ] Stage 3: whetstone-compiler → KSP SymbolProcessor + processor unit tests
- [ ] Stage 4: whetstone runtime — graphs/extensions/modules/multibinding factories on Metro
- [ ] Stage 5: whetstone-compose on Metro
- [ ] Stage 6: whetstone-worker on Metro
- [ ] Stage 7: gradle plugin rewrite (apply metro+ksp, drop anvil/kapt/proguard task)
- [ ] Stage 8: samples build green
- [ ] Stage 9: emulator end-to-end parity run (App/Activity/Service/View/ViewModel/Fragment/Worker)
- [ ] Stage 10: port test suites (codegen, configuration-cache, worker)
- [ ] Stage 11: docs + binary-compat baseline

## Done
- [x] Stage 0: baseline build green + Metro pattern spike (4/4 patterns validated: member-injector multibinding, graph extension + contributed factory, instance multibinding via `() -> Base`, **KSP-generated `@ContributesTo` merged by Metro**) — done 2026-06-12. Version trio locked: Kotlin 2.3.20 + Metro 1.2.0 + KSP 2.3.9.
