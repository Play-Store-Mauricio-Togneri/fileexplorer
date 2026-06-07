package com.mauriciotogneri.fileexplorer.testutil

/**
 * Marks an instrumentation test method (or class) that is known to flake for timing reasons under
 * full-suite emulator load and should be retried by [RetryRunner] before being reported as failed.
 *
 * Apply sparingly and only to tests whose flakiness is a confirmed emulator-load/timeout artifact —
 * never to paper over a real intermittent bug. Tests without this annotation run exactly once, so
 * genuine regressions still surface immediately.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Retry(val attempts: Int = 3)
