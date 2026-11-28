package com.anisequel

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * One back press leaves one layer.
 *
 * The reported failure was not a missing way out - it was too much of one: with a
 * character's page open over a title, a single press closed the sheet *and* the title
 * page, dropping the reader on Home with the page they were reading gone. The
 * bottom sheet's own dismissal was never reached, so the press fell through to the
 * screen's back handler and popped the destination the title page was drawn over.
 *
 * Back also has to unwind the layers above this sheet in the same way - the
 * appearance editor and My Entry each answer first - which is why this is a
 * `BackHandler` in the sheet rather than a flag on the title page: registered while
 * the sheet is composed, it is the newest callback on the dispatcher and therefore
 * the one that answers.
 *
 * A JVM test cannot press a key in a real window, so the guard is the call itself:
 * without it the sheet has no claim on back at all.
 */
class PersonSheetBackPressTest {

    private val sheet =
        "app/src/main/java/com/anisequel/ui/components/detail/PersonProfileSheet.kt"

    @Test
    fun `the person sheet answers back itself`() {
        val source = source(sheet)

        assertTrue(
            "the sheet registers no back handler, so a press goes past it to the " +
                "screen behind and closes the title page as well",
            source.contains("BackHandler { onDismiss() }")
        )
        assertTrue(
            "the sheet's back handler must dismiss the sheet, not the page under it",
            source.contains("import androidx.activity.compose.BackHandler")
        )
    }

    private fun source(path: String): String = File(findProjectRoot(), path).readText()
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("//[^\\n]*"), "")

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
