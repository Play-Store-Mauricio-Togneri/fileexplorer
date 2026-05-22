# File Explorer

run ./gradlew lint and ./gradlew compileDebugKotlin, then make a list of the findings sorted by
priority (critical, high, medium and low). for each, explain what's the problem and make at least
one proposal on how to fix it

* Check for problem in other devices:
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
        * The last line has a warning: "Overly broad keep rule affecting more than 100 classes.
          Scope rules using annotations, specific classes, or using specific field/method selectors"
    * Performance optimization (profile with Layout Inspector)

* Other:
    * Implement all events in Analytics

* Testing:
    * Test on multiple devices/APIs
    * Verify test coverage for ViewModels, repositories, and utilities
    * Unit tests required for all business logic. Use JUnit 4 + Mock for mocking, and Turbine for
      Flow
      testing.