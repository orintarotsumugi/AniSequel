package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The GIF picker's search field, and the typing rule it exists to keep.
 *
 * ## The failure this pins
 *
 * The field was fed `TextFieldValue(state.query)` - a value rebuilt from the store on
 * every recomposition. A rebuilt `TextFieldValue` carries the selection it was built
 * with, which for `TextFieldValue(text)` is caret-zero, so the second keystroke went
 * in *before* the first: typing "cat" into the search box produced "tac", and the
 * search the reader watched was never the word they typed. It is invisible in a
 * screenshot of a settled field, it fails on a real device in the way that makes
 * people blame the keyboard, and the fix is one type - the `String` overload, where
 * the selection is the field's own business.
 *
 * This is a source scan rather than a UI test because there is no Android SDK in this
 * environment and no IME to type with: what can be pinned on a JVM is that the field
 * is *built on the safe overload*, and that no `TextFieldValue` is constructed out of
 * the store's query anywhere in the file. A future edit that reintroduces the rebuild
 * fails here, in CI, instead of in somebody's comment thread.
 */
class GifSearchFieldCaretTest {

    /**
     * The browser's code, with its comments gone.
     *
     * The file documents this very bug in a comment that quotes the offending
     * expression, so a scan of the raw text matches its own explanation. Stripping
     * comments first is what lets the comment stay - the next reader needs to know why
     * the field is a `String` overload, and a scan that forbids the sentence is a scan
     * that gets the sentence deleted instead of the bug fixed.
     */
    private val code: String by lazy { withoutComments(source) }

    private val source: String by lazy {
        File(findProjectRoot(), "app/src/main/java/com/anisequel/ui/screens/profile/GifBrowser.kt").readText()
    }

    private fun withoutComments(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("//", i) -> {
                    val stop = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                    i = stop
                }
                text.startsWith("/*", i) -> {
                    val stop = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                    i = stop
                }
                else -> {
                    out.append(text[i])
                    i++
                }
            }
        }
        return out.toString()
    }

    @Test
    fun `the field is fed the store's string, not a rebuilt TextFieldValue`() {
        assertTrue(
            "the search field must bind `value = state.query` (the String overload)",
            Regex("""value\s*=\s*state\.query\s*,""").containsMatchIn(code)
        )
        assertFalse(
            "a `TextFieldValue(state.query)` puts the caret at 0 on every recomposition",
            Regex("""TextFieldValue\(\s*(state\.)?query""").containsMatchIn(code)
        )
    }

    @Test
    fun `the picker does not build a text-field value at all`() {
        // The picker has no reason to own a selection: it holds one string, and the
        // composer owns the draft the pick is written into.
        assertFalse(code.contains("TextFieldValue"))
    }

    /**
     * The repo root, walked up from wherever the tests were started.
     *
     * A Gradle unit test's working directory is the module (`app/`) in some setups
     * and the repository root in others, so a relative `File("app/src/...")` is a test
     * that passes or fails depending on how it was launched. `findProjectRoot` is what
     * every other source scan in this directory does.
     */
    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }
}
