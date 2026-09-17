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
     *
     * Catalan and Italian select `many` for exact millions too, but none of their plurals declares
     * it, so listing them here would fail the whole file instead of guarding it. That gap is
     * deliberate and stays out until those translations carry the quantity.
     */
    private val requiredQuantities = mapOf(
        "ar" to setOf("zero", "one", "two", "few", "many", "other"),
        "es" to setOf("one", "many", "other"),
        "fr" to setOf("one", "many", "other"),
        "pt" to setOf("one", "many", "other"),
        "ru" to setOf("one", "few", "many", "other"),
        "ro" to setOf("one", "few", "other")
    )

    /**
     * Every quantity `PluralRules.select()` can return per language, from CLDR's cardinal rules.
     * The mirror of [requiredQuantities]: that map catches a quantity a locale needs and lacks,
     * this one catches a quantity it declares and can never reach.
     */
    private val selectableQuantities = mapOf(
        "ar" to setOf("zero", "one", "two", "few", "many", "other"),
        "bn" to setOf("one", "other"),
        "ca" to setOf("one", "many", "other"),
        "de" to setOf("one", "other"),
        "el" to setOf("one", "other"),
        "es" to setOf("one", "many", "other"),
        "fr" to setOf("one", "many", "other"),
        "hi" to setOf("one", "other"),
        "in" to setOf("other"),
        "it" to setOf("one", "many", "other"),
        "ja" to setOf("other"),
        "nl" to setOf("one", "other"),
        "pt" to setOf("one", "many", "other"),
        "ro" to setOf("one", "few", "other"),
        "ru" to setOf("one", "few", "many", "other"),
        "tr" to setOf("one", "other"),
        "ur" to setOf("one", "other"),
        "vi" to setOf("other"),
        "zh" to setOf("other")
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

    private fun pluralItemsByName(file: File): Map<String, Map<String, String>> =
        childElements(parse(file), "plurals").associate { plural ->
            plural.getAttribute("name") to
                childElements(plural, "item").associate {
                    it.getAttribute("quantity") to it.textContent.orEmpty()
                }
        }

    private fun placeholdersIn(text: String): Set<String> =
        placeholder.findAll(text).map { it.value }.toSet()

    private fun placeholdersByName(file: File): Map<String, List<String>> =
        childElements(parse(file), "string")
            .filter { it.getAttribute("name").isNotEmpty() }
            .associate { element ->
                element.getAttribute("name") to
                    placeholder.findAll(element.textContent.orEmpty()).map { it.value }.sorted().toList()
            }

    /**
     * Placeholders per `<plurals>`, per quantity. Kept separate from [placeholdersByName] because a
     * plural's items are what get formatted, not the element, and because an item may legitimately
     * use fewer than the whole set — Arabic's `one` and `two` name the count in words and so carry
     * only `%2$d`, which is why the check built on this is a subset test rather than an equality.
     */
    private fun pluralPlaceholdersByName(file: File): Map<String, Map<String, Set<String>>> =
        childElements(parse(file), "plurals")
            .filter { it.getAttribute("name").isNotEmpty() }
            .associate { plural ->
                plural.getAttribute("name") to
                    childElements(plural, "item").associate { item ->
                        item.getAttribute("quantity") to
                            placeholder.findAll(item.textContent.orEmpty()).map { it.value }.toSet()
                    }
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

    /**
     * The crash case again, on the half [every translated string uses the same format placeholders
     * as the default] cannot see: it reads `<string>` elements only, so until this existed no test
     * looked inside a `<plurals>` at all. `getQuantityString(id, quantity, args)` formats the item
     * the language selects, so a placeholder that names an argument the call does not pass throws
     * for that locale alone — and only for the quantities that select that item, which is the
     * narrowest failure in this file.
     *
     * A subset test, for the reason [pluralPlaceholdersByName] gives: dropping a placeholder leaves
     * a number unsaid, which is a translation choice, while naming one that was never passed is a
     * crash.
     *
     * Comparing sets is what makes that relation expressible, and it is also the limit: an item
     * that *repeats* a non-positional `%d` the default declares once names an argument that was
     * never passed and still passes here. No locale does, and the positional forms this file's
     * newer plurals use cannot express it, so the gap is left open rather than paid for with
     * multiplicity bookkeeping that would have to know each plural's own argument count.
     */
    @Test
    fun `every translated plural uses only the format placeholders the default declares`() {
        val expected = pluralPlaceholdersByName(baseStrings())
            .mapValues { (_, items) -> items.values.flatten().toSet() }
        assertTrue("The default values/strings.xml should declare plurals", expected.isNotEmpty())

        localeDirs("values").forEach { dir ->
            pluralPlaceholdersByName(File(dir, "strings.xml")).forEach { (name, items) ->
                val base = expected[name] ?: return@forEach

                items.forEach { (quantity, used) ->
                    val unknown = (used - base).sorted()

                    assertTrue(
                        "${dir.name}/strings.xml: plural '$name' item '$quantity' uses $unknown, " +
                            "which the default does not declare ($base) — getQuantityString would " +
                            "throw for this language",
                        unknown.isEmpty()
                    )
                }
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
     * The other direction. [plurals carry every quantity their language requires] only ever reads
     * `required - declared`, so a quantity the language can never select passes it: the item
     * compiles, ships in the APK, and `PluralRules.select()` never returns its name. Nothing is
     * wrong on screen, which is exactly why it survives — it is a translation the next hand-off
     * maintains for a case that does not exist, and it hides which quantities the locale really
     * has.
     *
     * The language list is asserted rather than defaulted: a new `values-*` with no CLDR entry
     * here would otherwise be skipped silently, and this test's whole value is that it covers
     * every locale.
     */
    @Test
    fun `plurals declare no quantity their language can never select`() {
        val languages = localeDirs("values").map { it.name.removePrefix("values-") }

        assertEquals(
            "selectableQuantities does not cover the same languages as values-*",
            languages.sorted(),
            selectableQuantities.keys.sorted()
        )

        localeDirs("values").forEach { dir ->
            val selectable = selectableQuantities.getValue(dir.name.removePrefix("values-"))

            pluralsByName(File(dir, "strings.xml")).forEach { (name, quantities) ->
                val surplus = (quantities - selectable).sorted()

                assertTrue(
                    "${dir.name}: plural '$name' declares $surplus, which this language can never " +
                        "select — dead resource",
                    surplus.isEmpty()
                )
            }
        }
    }

    /**
     * The other half of a plural: the quantities are declared, but does the wording still say how
     * many? Nothing checked that. The two tests above read the `quantity` attributes and never the
     * item text, and every `getQuantityString` assertion in `androidTest` runs on the device
     * default — English — where only `one` and `other` are ever selected. So a translated `few` or
     * `many` that lost its `%d` in the hand-off renders "элементов" with no number in front of it,
     * in a language no test has ever resolved, and the whole suite stays green.
     *
     * Only `few`, `many` and `other` are checked. `zero`, `one` and `two` name a fixed count, and
     * writing it out — English's own `<item quantity="one">1 item</item>` — is idiomatic in many
     * languages, so requiring a placeholder there would fail correct translations.
     *
     * The requirement comes from the default's own `other` item rather than a list here, so a
     * plural added later is covered without touching this file.
     */
    @Test
    fun `plural items covering more than one number keep the count placeholder`() {
        val multiNumberQuantities = setOf("few", "many", "other")
        val defaults = pluralItemsByName(File(resDir, "values/strings.xml"))

        localeDirs("values").forEach { dir ->
            pluralItemsByName(File(dir, "strings.xml")).forEach { (name, items) ->
                val required = placeholdersIn(defaults[name]?.get("other").orEmpty())
                if (required.isEmpty()) return@forEach

                items.filterKeys { it in multiNumberQuantities }.forEach { (quantity, text) ->
                    val dropped = (required - placeholdersIn(text)).sorted()

                    assertTrue(
                        "${dir.name}: plural '$name' item '$quantity' dropped $dropped — the " +
                            "count never reaches the screen for that quantity",
                        dropped.isEmpty()
                    )
                }
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
