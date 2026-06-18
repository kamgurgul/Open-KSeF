<div align="center">

<img src="docs/icon.png" alt="Open KSeF logo" width="128" height="128" />

# Open KSeF

**A clean, multiplatform client for the Polish National System of e-Invoices ([KSeF](https://www.podatki.gov.pl/ksef/)).**

Browse, search and export your e-invoices from one app — on Android, iOS and desktop.

![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Platforms](https://img.shields.io/badge/Android%20%C2%B7%20iOS%20%C2%B7%20Desktop-2348DB)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)

</div>

## About

Open KSeF connects to the official KSeF API and presents your invoice data in a fast, native
interface built from a single shared Kotlin Multiplatform + Jetpack Compose codebase. One design
system, three platforms.

> API documentation: https://api-test.ksef.mf.gov.pl/docs/v2/index.html

## Features

- 📄 **Browse invoices** — fetch issued and received invoices with paging
- 🔍 **Search & filter** — find invoices by number or contractor and narrow by date range
- 🧾 **Invoice details** — full breakdown with NIP, dates and net / VAT / gross amounts
- ⬇️ **PDF export** — save invoices as PDF
- ✉️ **Send invoices** — submit invoices to KSeF

## Tech stack

- **Kotlin Multiplatform** — shared business, domain and UI logic
- **Jetpack Compose Multiplatform** — declarative UI on every target
- **Material 3** — theming with custom brand colors, type scale and shapes
- **Navigation 3** — multiplatform navigation
- **Koin** — dependency injection

## Building & running

```bash
# Run all tests
./gradlew allTests

# Desktop app
./gradlew :desktopApp:run

# Android app — open the project in Android Studio and run the androidApp configuration,
# or install a debug build:
./gradlew :androidApp:installDebug
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run the app.

## Status

Work in progress — the app already fetches and displays a list of invoices.

- [ ] Test and update sending invoices to KSeF
- [ ] Unify PDF exporting

## License

    Copyright KG Soft

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
