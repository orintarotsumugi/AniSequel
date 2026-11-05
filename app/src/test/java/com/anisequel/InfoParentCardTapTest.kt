package com.anisequel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parent card on the detail page is a button, and says what it is a sequel to
 * once.
 *
 * ## What was broken
 *
 * `InfoParentCard` was declared with two parameters - the sequel and a modifier -
 * and nothing else. The card names the watched entry the recommendation hangs off,
 * which is the one line on the page that points at a *different* title, and the
 * `onOpenMedia` lambda that would have followed it was already in scope at the call
 * site in `infoPageItems` and simply was not passed. So a tap on the card did
 * nothing at all: no ripple, no navigation, no accessibility action. The dashboard's
 * own relation banner had been given exactly this tap (`SequelRelationBanner`,
 * `onOpenParent`) and the detail page's copy of the same idea never was.
 *
 * The caption was built as `"${relationLabel} to watched entry"` while
 * `relationLabel` already ends in its preposition - "Sequel to", "Prequel to",
 * "Side story of" - so the card read "Sequel to to watched entry".
 *
 * ## Why this is a source-level test
 *
 * There is no Compose test harness in the unit-test source set -
 * `androidx.compose.ui.test.junit4` is an `androidTestImplementation` only - and the
 * regression is a parameter that was never threaded from the call site, which is
 * exactly what the source shows. Same approach as `RelationDetailResolutionTest`.
 */
class InfoParentCardTapTest {

    private val cardsFile = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/ui/components/detail/info/InfoCards.kt"
    )

    private val pageItemsFile = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/ui/components/detail/info/InfoPageItems.kt"
    )

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }

    private fun sourceOf(file: File): String {
        assertTrue(
            "could not read ${file.path}; this guard silently passing on a missing " +
                "file is the same failure it exists to prevent",
            file.isFile
        )
        return file.readText()
    }

    private fun parentCardBody(): String =
        sourceOf(cardsFile)
            .substringAfter("fun InfoParentCard(", missingDelimiterValue = "")
            .substringBefore("/**", missingDelimiterValue = "")

    @Test
    fun `the parent card takes an open-parent callback and is clickable`() {
        val body = parentCardBody()
        assertTrue(
            "could not find InfoParentCard in ${cardsFile.path}",
            body.isNotBlank()
        )
        assertTrue(
            "the card names the watched entry, so it has to be able to open it. " +
                "With no callback parameter there is nothing for the tap to call:\n" +
                body.take(600),
            body.contains("onOpenParent: (() -> Unit)? = null")
        )
        assertTrue(
            "the callback is only half of it - the surface needs a click target:\n" +
                body.take(1200),
            body.contains(".clickable(")
        )
    }

    @Test
    fun `the call site hands the parent card the detail page's own open route`() {
        val source = sourceOf(pageItemsFile)
        val call = source
            .substringAfter("InfoParentCard(", missingDelimiterValue = "")
            .substringBefore("modifier = it", missingDelimiterValue = "")
        assertTrue(
            "could not find the InfoParentCard call in ${source}",
            call.isNotBlank()
        )
        assertTrue(
            "the parent is in no list the dashboard holds, so the tap must build the " +
                "summary the sheet resolves - the same route SequelRelationBanner " +
                "takes. Passing nothing leaves the card inert:\n$call",
            call.contains("onOpenMedia(sequel.toParentSummary())")
        )
    }

    @Test
    fun `the caption does not repeat the relation label's own preposition`() {
        val body = parentCardBody()
        assertFalse(
            "`relationLabel` already ends in its preposition (\"Sequel to\", " +
                "\"Side story of\"), so the suffix must not add another. This is the " +
                "\"Sequel to to watched entry\" the card shipped with:\n" +
                body,
            body.contains("} to watched entry")
        )
        assertTrue(
            "the caption should still name the watched entry after the label:\n$body",
            body.contains("} watched entry")
        )
    }
}
