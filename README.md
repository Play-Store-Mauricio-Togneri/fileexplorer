# File Explorer

* Bugs in Android 6
    * Cannot scroll privacy

* Add fallback to icon for thumbnails if they fail

* Specific icons for different file types. Unify thumbnail icons and info screen icons

* Implement InApp Messaging

* Implement analytics

---

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
    * Configure ProGuard/R8 (see rules below)
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
* Set up script to sign and build the AAB
* Send a crashlytics report for each try/catch
* Use all agents to compare the old app and the new app searching for differences
* Can we improve the proguard rules?
* Update library versions in libs.versions.toml
* Performance optimization (profile with Layout Inspector)
* Any new feature? Small quality of life improvement?
* Testing:
    * Test on multiple devices/APIs
    * Verify test coverage for ViewModels, repositories, and utilities
    * Unit tests required for all business logic. Use JUnit 4 + Mockk for mocking, and Turbine for
      Flow
      testing.

# Future

* Grid View: Alternative to list view for visual browsing
* Bookmarks / Favorites: Quick access to favorite folders