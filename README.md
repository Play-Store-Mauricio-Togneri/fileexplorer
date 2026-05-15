# File Explorer

* Replace toast by snackbar?

* Review privacy and terms

* Adjust colors for light and dark themes

* Add fallback to icon for thumbnails if they fail

* Specific icons for different file types. Unify thumbnail icons and info screen icons

* Implement analytics

* Implement crashlytics

* Implement InApp Messaging

---

# Validation

* List all the toast that we show in the app
* What features don't work in some old Android versions?
* Different Android version problems
* Search for TODOs
* Bugs
* Security vulnerabilities
* Problems with screen size
* Problems in some devices
* Performance problems
* Unused or dead code
* Accessibility problems
* Localize app in all major languages and existing used languages in the app
* Is there hardcoded text or it's all localized?
* Run project inspections (Problems tab)
* Verify test coverage for ViewModels, repositories, and utilities
* Test on multiple devices/APIs
* Configure ProGuard/R8 (see rules below)
* Set up signing config
* Implement defensive programming everywhere to avoid crashes
* Send a crashlytics report for each try/catch
* Use all agents to compare the old app and the new app searching for differences
* Can we improve the proguard rules?
* Update library versions in libs.versions.toml
* Performance optimization (profile with Layout Inspector)
* Unit tests required for all business logic. Use JUnit 4 + Mockk for mocking, and Turbine for Flow
  testing.

# Future

* Grid View: Alternative to list view for visual browsing
* Bookmarks / Favorites: Quick access to favorite folders