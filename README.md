# File Explorer

* String.format(Locale.US) -> Use device local?

* Create new folder becomes a secondary button

* New mime types for office files

* List what functionality requires more than Android 6
    * Functionality
    * API level

* Run linter to fix problems

* Check for problem in other devices:
    * What features don't work in some old Android versions?
    * Different Android version problems
    * Problems with screen size

* Search for bugs
    * Security vulnerabilities
    * Performance problems
    * Unused or dead code
    * Accessibility problems
    * Run project inspections (Problems tab)
    * Implement defensive programming everywhere to avoid crashes (send reports with Crashlytics)

* Localize app in all major languages and existing used languages in the app
    * Portuguese
    * Hindi
    * English
    * Spanish
    * Indonesian
    * German
    * Italian
    * French
    * Russian
    * Dutch
    * Mandarin Chinese
    * Japanese
    * Vietnamese
    * Standard Arabic
    * Bengali
    * Urdu
    * Turkish
    * Greek
    * Polish
    * Romanian
* Is there hardcoded text or it's all localized?

* Optimization:
    * Configure ProGuard/R8 (see rules below)
    * Performance optimization (profile with Layout Inspector)

* Other:
    * Update library versions in libs.versions.toml
    * Implement all events in Analytics

* Testing:
    * Test on multiple devices/APIs
    * Verify test coverage for ViewModels, repositories, and utilities
    * Unit tests required for all business logic. Use JUnit 4 + Mock for mocking, and Turbine for
      Flow
      testing.