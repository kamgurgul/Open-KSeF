# Project

Open KSeF is a simple multiplatform app which will display information from KSeF (Krajowy Systeme
e-Faktur) - the Polish National System of e-Invoices. It is built using Kotlin Multiplatform and
Jetpack Compose, and it runs on Android, iOS and desktop. The app fetches data from the KSeF API.
Api documentation: https://api-test.ksef.mf.gov.pl/docs/v2/index.html

# Commands

- Run tests: `./gradlew allTests`

# Code style

- Use 4 spaces for indentation
- From VM expose data as StateFlow using single UiState
- For events use Channel<Event>(Channel.BUFFERED) and observe them in compose with ObserveAsEvents
- In VM use `on` prefix for callback functions like `onEvent`, `onClick` etc.
- For navigation use Navigation 3 library
- To VM layer pass data as NavKey and expose UiState as combined StateFlow created via
  `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())` and observed via
  `collectAsStateWithLifecycle()`
- Always add unit tests for VM layer nad rest of domain