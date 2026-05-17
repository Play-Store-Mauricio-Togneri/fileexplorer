# File Explorer

# Validation

* Check for problem in other devices:
    * What features don't work in some old Android versions?
    * Different Android version problems
    * Problems with screen size

* Search for TODOs

* Search for bugs
    * Security vulnerabilities
    * Performance problems
    * Unused or dead code
    * Accessibility problems
    * Run project inspections (Problems tab)
    * Implement defensive programming everywhere to avoid crashes (send reports with crashlytics)

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
    * Set up script to sign and build the AAB
    * Use all agents to compare the old app and the new app searching for differences
    * Update library versions in libs.versions.toml
    * Any new feature? Small quality of life improvement?
    * Implement all events in Analytics

* Testing:
    * Test on multiple devices/APIs
    * Verify test coverage for ViewModels, repositories, and utilities
    * Unit tests required for all business logic. Use JUnit 4 + Mockk for mocking, and Turbine for
      Flow
      testing.

# Future

* Grid View: Alternative to list view for visual browsing
* Bookmarks / Favorites: Quick access to favorite folders