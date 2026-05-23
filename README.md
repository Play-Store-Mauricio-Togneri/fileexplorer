# File Explorer

* Is there hardcoded text or it's all localized?

* Optimization:
    * Configure ProGuard/R8 (see rules below)
        * The last line has a warning: "Overly broad keep rule affecting more than 100 classes.
          Scope rules using annotations, specific classes, or using specific field/method selectors"

* Other:
    * Implement all events in Analytics

* Testing:
    * Test on multiple devices/APIs
    * Verify test coverage for ViewModels, repositories, and utilities
    * Unit tests required for all business logic. Use JUnit 4 + Mock for mocking, and Turbine for
      Flow
      testing.