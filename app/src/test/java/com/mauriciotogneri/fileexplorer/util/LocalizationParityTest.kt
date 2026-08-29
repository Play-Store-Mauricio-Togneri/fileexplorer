package com.mauriciotogneri.fileexplorer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the two localized resource sets `CLAUDE.md` mandates and nothing else enforces.
 *
 * Both failure modes here are invisible to every other test in the suite, because the whole suite
 * runs in English: a string missing from one `values-*` silently falls back to English for that
 * language, and a translation whose format placeholders drift from the default throws
 * `IllegalFormatException` at composition time — a hard crash for that locale's users only.
 *
 * This runs on the JVM against the resource files themselves rather than through Android, so it
 * costs milliseconds and blocks the drift at the point it is introduced.
 */
class LocalizationParityTest {

    /**
     * Quantities each language needs beyond `one`/`other`. A `<plurals>` carrying only those two
     * compiles and then reads wrong in these locales, which is why `CLAUDE.md` calls them out.
     */
    private val requiredQuantities = mapOf(
        "ar" to setOf("zero", "one", "two", "few", "many", "other"),
        "ru" to setOf("one", "few", "many", "other"),
        "ro" to setOf("one", "few", "other")
    )

    private val resDir: File by lazy {
        // Gradle runs unit tests with the module directory as the working directory, but walking up
        // keeps this working if that ever changes or the test is run from the repository root.
        generateSequence(File(System.getProperty("user.dir")!!).absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/res") }
            .firstOrNull { it.isDirectory }
            ?: File(System.getProperty("user.dir")!!, "src/main/res").also {
                assertTrue("Could not locate app/src/main/res from ${System.getProperty("user.dir")}", it.isDirectory)
            }
    }

    /**
     * Language directories only. `values-*` also covers qualifiers that carry no `strings.xml` —
     * `values-night`, `values-v33`, `values-sw600dp` — and parsing a file that is not there would
     * fail these tests for a reason that has nothing to do with localization.
     */
    private fun localeDirs(prefix: String): List<File> =
        resDir.listFiles { file -> file.isDirectory && file.name.startsWith("$prefix-") }
            ?.filter { dir -> prefix == "raw" || File(dir, "strings.xml").isFile }
            ?.sortedBy { it.name }
            .orEmpty()

    private fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement

    private fun childElements(parent: Element, tag: String): List<Element> {
        val nodes = parent.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun stringNames(file: File): Set<String> =
        childElements(parse(file), "string").mapNotNull { it.getAttribute("name").takeIf(String::isNotEmpty) }.toSet()

    private fun pluralsByName(file: File): Map<String, Set<String>> =
        childElements(parse(file), "plurals").associate { plural ->
            plural.getAttribute("name") to
                childElements(plural, "item").map { it.getAttribute("quantity") }.toSet()
        }

    /** `%d`, `%s` and their positional forms. Order-insensitive: a translation may reorder them. */
    private val placeholder = Regex("""%(?:\d+\$)?[a-zA-Z]""")

    private fun placeholdersByName(file: File): Map<String, List<String>> =
        childElements(parse(file), "string")
            .filter { it.getAttribute("name").isNotEmpty() }
            .associate { element ->
                element.getAttribute("name") to
                    placeholder.findAll(element.textContent.orEmpty()).map { it.value }.sorted().toList()
            }

    private fun baseStrings() = File(resDir, "values/strings.xml")

    @Test
    fun `every supported language ships every string the default declares`() {
        val expected = stringNames(baseStrings())
        assertTrue("The default values/strings.xml should not be empty", expected.isNotEmpty())

        localeDirs("values").forEach { dir ->
            val translated = stringNames(File(dir, "strings.xml"))
            val missing = (expected - translated).sorted()

            assertTrue(
                "${dir.name} is missing ${missing.size} string(s) and will fall back to English for " +
                    "them: ${missing.take(10)}",
                missing.isEmpty()
            )
        }
    }

    @Test
    fun `every supported language ships every plural the default declares`() {
        val expected = pluralsByName(baseStrings()).keys

        localeDirs("values").forEach { dir ->
            val translated = pluralsByName(File(dir, "strings.xml")).keys
            val missing = (expected - translated).sorted()

            assertTrue("${dir.name} is missing plural(s): $missing", missing.isEmpty())
        }
    }

    /**
     * The crash case. `getString(id, args)` formats against whatever the translation declares, so a
     * dropped or retyped placeholder throws for that language and only that language.
     */
    @Test
    fun `every translated string uses the same format placeholders as the default`() {
        val expected = placeholdersByName(baseStrings())

        localeDirs("values").forEach { dir ->
            placeholdersByName(File(dir, "strings.xml")).forEach { (name, actual) ->
                val base = expected[name] ?: return@forEach

                assertEquals(
                    "${dir.name}/strings.xml: '$name' declares $actual but the default declares " +
                        "$base — getString would throw for this language",
                    base,
                    actual
                )
            }
        }
    }

    @Test
    fun `plurals carry every quantity their language requires`() {
        localeDirs("values").forEach { dir ->
            val language = dir.name.removePrefix("values-")
            val required = requiredQuantities[language] ?: return@forEach

            pluralsByName(File(dir, "strings.xml")).forEach { (name, quantities) ->
                val missing = (required - quantities).sorted()

                assertTrue(
                    "${dir.name}: plural '$name' is missing $missing — required for $language",
                    missing.isEmpty()
                )
            }
        }
    }

    /**
     * The second localized set. These render through compose-markdown, so a stale translation is a
     * compliance problem rather than a cosmetic one.
     */
    @Test
    fun `every supported language ships both legal documents`() {
        val expected = File(resDir, "raw").listFiles()?.map { it.name }?.sorted().orEmpty()
        assertTrue("raw/ should hold the default legal documents", expected.isNotEmpty())

        localeDirs("raw").forEach { dir ->
            val present = dir.listFiles()?.map { it.name }?.sorted().orEmpty()

            assertEquals("${dir.name} does not carry the same documents as raw/", expected, present)
        }
    }

    /**
     * The language list is fixed by `CLAUDE.md`; both sets must cover it. A new `values-*` added
     * without its `raw-*` twin leaves that language reading the English privacy policy.
     */
    @Test
    fun `the string and legal-document language sets match`() {
        val stringLanguages = localeDirs("values").map { it.name.removePrefix("values-") }.toSet()
        val rawLanguages = localeDirs("raw").map { it.name.removePrefix("raw-") }.toSet()

        assertEquals(
            "values-* and raw-* cover different languages",
            stringLanguages.sorted(),
            rawLanguages.sorted()
        )
    }
}
