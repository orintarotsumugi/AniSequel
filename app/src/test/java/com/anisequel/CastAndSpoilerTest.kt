package com.anisequel

import com.anisequel.data.network.GraphQLQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The cast row, and the spoiler block it shares a page with.
 *
 * ## What was wrong with the cast
 *
 * `characters(perPage: 12)` capped the cast at twelve and asked for three fields:
 * an id, a name and a portrait. So the row said a character's name, their role, and
 * who plays them — which is the three facts a poster already gives you — and nothing
 * else, while AniList was holding the rest.
 *
 * Verified against the live API rather than guessed at. `anime/1` answers twenty-five
 * characters to `perPage: 25`, and each one carries, per character:
 *
 * ```json
 * { "name": { "full": "Spike Spiegel", "alternative": ["Spike Spike", "Swimming Bird"] },
 *   "favourites": 14370, "gender": "Male", "age": "27",
 *   "siteUrl": "https://anilist.co/character/1", "description": "__Height:__ 185 cm…" }
 * ```
 *
 * `Faye Valentine`'s `age` is `"23 (Physical), 77 (Actual)"`. That is why it is a
 * `String?` and not an `Int?` — the same trap `MediaNode.season` documents, where Moshi
 * refused to read an enum string as an int and threw out of the only call that fetches
 * the user's whole list.
 *
 * ## What is deliberately *not* drawn
 *
 * `description` is fetched and never rendered on the card. Twenty-five descriptions
 * is a wall of text in a horizontal row, and a spoiler-laden one at that. It is asked
 * for so a character is not a dead end — the card opens that character's AniList page,
 * which is where the description lives, in the same way a studio credit chip opens a
 * studio's filmography rather than drawing it.
 */
class CastAndSpoilerTest {

    private val charactersBlock = GraphQLQueries.getMediaDetailQuery("ANIME")
        // `substringAfter("characters(")` and not the whole argument list: taking the
        // full `characters(sort: [...])` as the marker *consumes* the sort, so the
        // block below would no longer contain the thing the next test asserts on.
        .substringAfter("characters(")
        .substringBefore("staff(sort: [RELEVANCE")

    @Test
    fun `the whole cast is asked for, not the first twelve`() {
        val query = GraphQLQueries.getMediaDetailQuery("ANIME")

        assertTrue(
            "twelve was a display decision made in the query, so the row could only " +
                "ever show a third of a long cast with no way to reach the rest - and " +
                "AniList serves twenty-five on this connection:\n$query",
            query.contains("characters(sort: [ROLE, RELEVANCE, ID], perPage: 25)")
        )
    }

    @Test
    fun `the role sort is kept`() {
        // Without it AniList's own ordering decides what a reader sees first, and
        // that ordering is relevance-within-role rather than role itself.
        assertTrue(charactersBlock.contains("sort: [ROLE, RELEVANCE, ID]"))
    }

    /**
     * Every field the card draws, asserted individually.
     *
     * They were absent together, which is why the row was a caption: a name, a role
     * and a portrait is what a poster already says.
     */
    @Test
    fun `the character selection carries what the API provides`() {
        listOf(
            "id" to "id",
            "name" to "name { full native alternative }",
            "image" to "image { large medium }",
            "favourites" to "favourites",
            "siteUrl" to "siteUrl",
            "gender" to "gender",
            "age" to "age",
            "description" to "description(asHtml: false)"
        ).forEach { (field, needle) ->
            assertTrue(
                "the cast card draws `$field`, so the detail query has to ask for " +
                    "it:\n$charactersBlock",
                charactersBlock.contains(needle)
            )
        }
    }

    /**
     * `alternative` is what makes a searchable character findable by the nickname a
     * reader knows them by — Spike Spiegel is "Spike Spike" and "Swimming Bird" on
     * AniList, and the card printed one line.
     */
    @Test
    fun `the aliases are asked for on the character name`() {
        assertTrue(
            "`name { full native }` was the whole name; the aliases are a third " +
                "field on the same selection and cost nothing extra to carry:\n" +
                charactersBlock,
            charactersBlock.contains("name { full native alternative }")
        )
    }

    /**
     * Declared once.
     *
     * `name` appearing twice inside one selection is a document AniList rejects, and
     * the failure would be an HTTP 400 on **every** title open rather than on the one
     * the edit was about — see `MediaDetailQueryArgumentTest` for what that costs.
     */
    @Test
    fun `the character name is selected once`() {
        assertEquals(
            "a duplicate field in one selection is a 400 for the entire detail " +
                "document, so the synopsis and the cast and the staff all disappear " +
                "together:\n$charactersBlock",
            1,
            charactersBlock.split("name { full native alternative }").size - 1
        )
    }

    /**
     * The voice actor hangs off the *edge*, not the character.
     *
     * AniList returns the same character with a different actor per language, so only
     * the edge can say "this actor plays this character". Selecting `voiceActors`
     * anywhere else compiles and returns no pairing.
     */
    @Test
    fun `the voice actor is still on the edge`() {
        val edges = charactersBlock.substringAfter("edges {")
        assertTrue(
            "voiceActors must be an edge field, beside `role`:\n$edges",
            edges.contains("voiceActors(language: JAPANESE)")
        )
        assertFalse(
            "and not on the character node, where it would answer nothing:\n$edges",
            edges.substringAfter("node {").substringBefore("voiceActors").contains("voiceActors")
        )
    }

    @Test
    fun `both documents are still balanced`() {
        listOf(
            "typed" to GraphQLQueries.getMediaDetailQuery("MANGA"),
            "untyped" to GraphQLQueries.getMediaDetailQuery(null)
        ).forEach { (name, query) ->
            assertEquals("$name braces", query.count { it == '{' }, query.count { it == '}' })
            assertEquals("$name parens", query.count { it == '(' }, query.count { it == ')' })
        }
    }

    // ---- the spoiler block ------------------------------------------------------

    private fun spoilerSource() = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/ui/components/novamarkdown/NovaMarkdownBlocks.kt"
    ).readText()

    /**
     * The control is a chip on the block's top edge, and it is the only thing visible
     * while the spoiler is closed.
     *
     * It used to be a row of text *inside* the container, styled so close to the body
     * text beneath it that a reader scanning a bio could not tell where the hidden
     * part began — which is the only thing a spoiler has to say before it is opened.
     */
    @Test
    fun `the spoiler control is a chip above the block, not a row inside it`() {
        val source = spoilerSource()

        assertTrue(
            "the spoiler must be one composable with one control, not a header row " +
                "plus a Hide button",
            source.contains("internal fun NovaSpoiler(")
        )
        assertTrue(
            "and the chip is drawn before the revealed surface, so it sits on the " +
                "block's top edge:\n$source",
            Regex(
                """testTag\("nova_markdown_spoiler_chip"\)[\s\S]{0,1200}?if \(revealed\) \{\s*\n\s*Surface\("""
            ).containsMatchIn(source)
        )
    }

    /**
     * The chip sits in the middle of its own line.
     *
     * It was drawn against the left edge of a full-width column, where it read as
     * the start of the paragraph it actually hides - the one shape a reader's eye
     * is trained to read through rather than to press. Centring it is what makes
     * it a button: the renderer never centres prose, so the shape alone says
     * "control".
     */
    @Test
    fun `the spoiler chip is centred over the block it hides`() {
        val chip = spoilerSource()
            .substringAfter("internal fun NovaSpoiler(")
            .substringBefore("\n\n/**")

        assertTrue(
            "the chip is back against the left edge of the column, where it reads " +
                "as the start of a paragraph:\n$chip",
            Regex(
                """Row\(\s*modifier = Modifier\.fillMaxWidth\(\),\s*horizontalArrangement = Arrangement\.Center\s*\)"""
            ).containsMatchIn(chip)
        )
    }

    /**
     * The state has to be in the words.
     *
     * The previous header's only "you can close this" cue was an unlabelled `Done`
     * icon — which reads correctly in neither greyscale nor a screen reader, and is a
     * cue nobody guesses.
     */
    @Test
    fun `the chip says what it will do in both states`() {
        val source = spoilerSource()

        assertTrue(
            "a closed spoiler has to offer to open itself in words",
            source.contains("\"Show spoiler\"")
        )
        assertTrue(
            "and an open one has to offer to close itself in words",
            source.contains("\"Hide spoiler\"")
        )
    }

    @Test
    fun `the eye glyph is there and it is a real icon`() {
        val source = spoilerSource()

        assertTrue(
            "the eye is the affordance that says there is something to look at",
            source.contains("AppVectorIcons.Visibility")
        )
        assertFalse(
            "and it must resolve: `VisibilityOff` is not defined in AppVectorIcons, " +
                "and naming it would not compile:\n$source",
            source.contains("VisibilityOff")
        )
    }

    /**
     * The revealed text takes no taps.
     *
     * The block used to be one toggle, so *any* tap on revealed text closed it — a
     * spoiler is several lines, it is often scrolled with a thumb resting on it, and
     * it can contain links, every one of which was swallowed by the toggle.
     */
    @Test
    fun `revealed spoiler text is not a close button`() {
        val source = spoilerSource()

        // The block used to carry the click handler on its whole surface, so a tap
        // anywhere on the revealed text closed it - and a link inside a spoiler was
        // swallowed by it. The toggle now lives on the chip and nowhere else.
        val block = source.substringAfter("internal fun NovaSpoiler(").substringBefore("\n\n/**")
        assertFalse(
            "the revealed text has to behave like any other paragraph:\n$block",
            block.substringAfter("if (revealed) {").contains("clickable")
        )

        assertTrue(
            "the chip itself is the click target",
            block.contains("onClick = { revealed = !revealed }")
        )
        assertTrue(
            "and the press signal is shared with it, or the tap never lands:\n$block",
            Regex("""clickable\([\s\S]{0,200}interactionSource = interactionSource""")
                .containsMatchIn(block)
        )
        assertEquals(
            "one control, or a second tap target appears inside the card",
            1,
            Regex("""\bclickable\(""").findAll(block).count()
        )
    }

    @Test
    fun `the reveal survives a scroll and a rotation`() {
        val source = spoilerSource()
        assertTrue(
            "`remember` would be discarded when a LazyColumn disposes the item, so " +
                "a revealed spoiler re-hid itself on the way back up:\n$source",
            source.contains("var revealed by rememberSaveable { mutableStateOf(false) }")
        )
    }

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