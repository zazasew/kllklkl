# Build fixes for previous CI errors

Fixed the Kotlin compiler errors reported by GitHub Actions:

- `SettingsRepository.kt`: convert `Set` to `List` before `takeLast(200)`.
- `PoliceIncidentCheckWorker.kt`: convert the ID collections to `List` before `takeLast(200)`.
- `LiveCurrencyFetcher.kt`: changed the ECB local function from expression body to block body so `return` is legal.
- `PoliceIncidentFetcher.kt`: removed `continue` from `ifBlank` inline-lambda and used an ordinary blank check.
- `YoutubeChannelFetcher.kt`: changed the `isShort` regex to a Kotlin raw string so `\s` is a valid regex escape.
- `DashboardScreen.kt`: removed currency-only fields accidentally referenced from the Bitcoin card (`c.updatedAtMillis`, `c.consensusSources`, etc.).
- `SettingsScreen.kt`: marked `SettingsGroupHeader` as `@Composable`.

The project could not be executed through Gradle in this environment because the Gradle distribution is not locally installed and outbound network/DNS access is unavailable. The fixes above directly address every compiler error in the supplied CI log.
