package com.anisequel.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/*
 * gifukai's reaction index (api.gifukai.com), the default.
 *
 * ## The catalogue, and why it is default
 *
 * Every GIF on gifukai is hand-picked: near-16:9, no bugged frames, no baked-in
 * text, and each carries its anime's name - which is exactly what a *comment*
 * wants, because AniList renders the file itself at whatever size the embed
 * dictates. Requests randomise one reaction per action (`/v1/hug`, `/v1/pat`,
 * ...), so "search" here means "match the words the reader typed to an action",
 * not "ask an index for a term". No key, no signup, and no published rate limit;
 * it is free to use (https://github.com/lucialv/gifukai-api).
 *
 * ## Why a search is several draws, and still has no pages
 *
 * The endpoint answers one random scene per ask and has no cursor and no count
 * parameter (its README documents neither; `/v1/library` can browse by anime but
 * needs an X-Library-Key that is not publicly issued). One tile per search read
 * as "there is exactly one hug", so a search fires [MAX_DRAWS] asks in parallel
 * and keeps the first [BATCH_SIZE] distinct scenes - parallel, so the grid costs
 * one request's latency, and deduplicated, because a small action folder repeats
 * itself long before twelve draws. `next` is still always null: a second ask is
 * a re-roll, not a page, and the picker's shuffle row (see GifSearchStore.reroll)
 * evicts the cached batch and draws a fresh one.
 *
 * ## Why a name is handed to Klipy rather than refused
 *
 * The keyless API cannot search anime or character names at all. It used to
 * refuse every non-action word with a sentence naming Klipy or Safebooru - which
 * left the reader to find Settings and switch provider to finish the thought
 * they started. The factory now wires [nameSearch] to Klipy on the app's bundled
 * key, so a name is answered where it was typed, and the answer carries a
 * [GifPage.note] saying which index gave it: a substitution that is not said out
 * loud is the reader searching one catalogue and getting another's tiles. The
 * refusal below survives only for a client built without a hand-off.
 */

/**
 * gifukai's `/{action}` endpoints on api.gifukai.com, keyless.
 *
 * [nameSearch] is the index a query that names no action is handed to - the
 * factory wires Klipy on the bundled key so anime and character names answer
 * from the default provider. Null keeps the old refusal, which is what a direct
 * construction (a test, a preview) gets.
 */
class GifukaiGifs(
    private val client: OkHttpClient = SafebooruGifs.defaultClient(),
    moshi: Moshi = NetworkClient.json,
    private val nameSearch: GifSearchApi? = null
) : GifSearchApi {

    override val providerName: String get() = "Gifukai"

    override val catalogueIsNarrow: Boolean get() = true

    override val rerollable: Boolean get() = true

    /** The hand-off the factory wired, pinned by the factory test like Klipy's key. */
    internal val nameSearchInUse: GifSearchApi? get() = nameSearch

    override fun emptyHint(query: String): String =
        "Gifukai carries hand-picked reaction scenes: \"$query\" matched none of " +
            "them. Try a reaction like \"hug\", \"pat\", \"laugh\" or \"cry\" - " +
            "\"two girls hug\" or \"boy and girl hug\" asks for a pairing - or " +
            "pick Klipy or Safebooru to search a title."

    private val adapter = moshi.adapter(GifukaiResponse::class.java)

    override suspend fun search(query: String, cursor: String?): Result<GifPage> =
        withContext(Dispatchers.IO) {
            val term = query.trim()
            if (term.isEmpty()) return@withContext Result.success(GifPage(emptyList()))
            val resolved = resolve(term)
            if (resolved == null) return@withContext searchNames(term, cursor)
            val (action, type) = resolved
            if (cursor != null) {
                // There is no page after the page; a cursor on an action answer
                // means a caller ignored `next = null`, and answering it with the
                // same random draw would pretend to page a catalogue that does
                // not. A hand-off answer's cursor is Klipy's own and passes through
                // in searchNames instead.
                return@withContext Result.success(GifPage(emptyList()))
            }
            val pairing = resolvePairing(term)
            gatherGifukaiScenes(MAX_DRAWS, BATCH_SIZE) { fetchScene(action, type, pairing) }
                .map { scenes -> GifPage(results = scenes, next = null, totalCount = scenes.size) }
        }

    /**
     * The hand-off for a query that names no action: an anime title, a character
     * name, a sentence. Every answer - tiles, a miss, a refusal - carries the
     * sentence that says Gifukai did not give it, because the placeholder still
     * names Gifukai and the reader deserves to know whose catalogue answered.
     */
    private suspend fun searchNames(term: String, cursor: String?): Result<GifPage> {
        val fallback = nameSearch ?: return Result.failure(
            GifSearchFailure(
                "Gifukai answers reaction scenes by action - " +
                    resolveGifukaiHint.joinToString(", ") +
                    ". Pick Klipy or Safebooru to search a title or a sentence."
            )
        )
        val handoff = "Gifukai answers reaction words only, so ${fallback.providerName} took this search."
        return fallback.search(term, cursor).fold(
            onSuccess = { page ->
                Result.success(
                    page.copy(
                        note = if (page.results.isEmpty()) {
                            // The miss belongs to the index that just answered, not
                            // to the one the sentence goes on to recommend: naming
                            // Safebooru as the search that found nothing told the
                            // reader a catalogue they never asked had refused them.
                            "$handoff Nothing there matches \"$term\" - try another " +
                                "spelling, or pick Safebooru, which searches anime tags."
                        } else {
                            handoff
                        }
                    )
                )
            },
            onFailure = { error ->
                Result.failure(GifSearchFailure("$handoff ${error.message ?: "It did not answer."}"))
            }
        )
    }

    /** One random scene from an action endpoint, as a failure the batch can report. */
    private suspend fun fetchScene(
        action: String,
        type: String?,
        pairing: String?
    ): Result<GifResult> {
        val built = runCatching {
            ENDPOINT.toHttpUrl().newBuilder()
                // A path segment, so a query cannot reach another endpoint.
                .addPathSegment(action)
                .apply {
                    if (type != null) {
                        addQueryParameter("type", type)
                    }
                    // The one filter the keyless API has beyond the action: the
                    // genders in the scene. Sent only when the query named one,
                    // so the endpoint keeps its own spread otherwise.
                    if (pairing != null) {
                        addQueryParameter("pairing", pairing)
                    }
                }
                .build()
        }
        val request = built.getOrNull()?.let {
            Request.Builder()
                .url(it)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
        } ?: return Result.failure(GifSearchFailure("That search could not be built."))

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(
                        GifSearchFailure(
                            when (response.code) {
                                404 -> "Gifukai does not carry that reaction."
                                429 -> "Gifukai is limiting requests. Wait a moment and try again."
                                else -> "Gifukai is not answering right now (HTTP ${response.code})."
                            }
                        )
                    )
                }
                val parsed = runCatching { adapter.fromJson(response.body?.string().orEmpty()) }
                    .getOrNull()
                    ?.toGifResult()
                    ?: return Result.failure(
                        GifSearchFailure("Gifukai answered with something unreadable.")
                    )
                Result.success(parsed)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(GifSearchFailure("Could not reach Gifukai."))
        }
    }

    companion object {
        private const val ENDPOINT = "https://api.gifukai.com/v1"
        private const val USER_AGENT =
            "AniSequel (https://github.com/orintarotsumugi/AniSequel)"

        /**
         * Twelve asks for nine tiles: the endpoint draws with replacement, so a
         * folder repeats scenes before it has given nine distinct ones, and a
         * small folder may hold fewer than nine in total - the duplicates are
         * dropped and what remains is the whole folder. The API documents no
         * rate limit, and the asks run in parallel so the grid waits one
         * request's latency, not twelve.
         */
        private const val MAX_DRAWS = 12
        private const val BATCH_SIZE = 9

        /**
         * The actions the live API offers, checked against `GET /v1/actions`.
         * A query that names one of these - or one of the aliases besides -
         * answers with a random hand-picked scene.
         */
        internal val ACTIONS = setOf(
            "angry", "bite", "bleh", "blowkiss", "blush", "bonk", "bored", "bye",
            "carry", "clap", "confused", "cry", "cuddle", "dance", "eat",
            "facepalm", "feed", "handhold", "handshake", "happy", "hi", "highfive",
            "hug", "kick", "kill", "kiss", "lappillow", "laugh", "lick", "nod",
            "nope", "nya", "pat", "peek", "poke", "pout", "punch", "run", "salute",
            "scared", "shake", "shocked", "shoot", "shrug", "shy", "sing", "sip",
            "slap", "sleep", "smile", "smug", "sorry", "spin", "stare", "surprised",
            "taunt", "teehee", "think", "thumbsup", "tickle", "tired", "wag",
            "wallslam", "wave", "wink", "yawn", "yay", "yeet"
        )

        /**
         * Words a reader actually types, mapped to the action that answers.
         *
         * The first half is the API's own alias table (`GET /v1/actions`); the
         * rest is the ordinary English shapes of those reactions - a reader types
         * "crying", not the bare "cry", and "hugs" or "hugged" are the same
         * request with a different tense. Every search chip and substitution
         * sentence in this app assumes the reader's word, not the endpoint's.
         *
         * Plurals and verb forms are listed rather than stemmed: the action set
         * is small and a stemmer would map "waving" correctly and "sad" nowhere,
         * while also turning a name like "Fern" into a reaction. A hand-written
         * table is auditable against [ACTIONS] by a test, which a stemmer is not.
         */
        internal val ALIASES: Map<String, String> = mapOf(
            // The API's own aliases.
            "mad" to "angry", "rage" to "angry",
            "mwah" to "blowkiss",
            "flustered" to "blush",
            "cya" to "bye", "goodbye" to "bye",
            "claps" to "clap",
            "sob" to "cry",
            "snuggle" to "cuddle",
            "nom" to "eat",
            "hello" to "hi", "hey" to "hi",
            "murder" to "kill",
            "peck" to "kiss",
            "lmao" to "laugh", "lol" to "laugh",
            "agree" to "nod", "yes" to "nod",
            "deny" to "nope", "no" to "nope",
            "meow" to "nya", "neko" to "nya",
            "headpat" to "pat",
            "boop" to "poke",
            "bang" to "shoot",
            "dunno" to "shrug", "idk" to "shrug",
            "drink" to "sip",
            "nap" to "sleep", "zzz" to "sleep",
            "gaze" to "stare",
            "like" to "thumbsup",
            "thinking" to "think",
            "kabedon" to "wallslam",
            // The word forms the reaction chips and a phone keyboard spell out.
            "crying" to "cry",
            "laughing" to "laugh",
            "smiling" to "smile",
            "waving" to "wave",
            "hugging" to "hug",
            "patting" to "pat",
            "kissing" to "kiss",
            "sleeping" to "sleep",
            "angried" to "angry",
            "sad" to "cry",
            "cheeks" to "blush",
            // Plurals, past tenses and the near-synonyms a reader reaches for.
            "anger" to "angry", "angered" to "angry", "furious" to "angry",
            "annoyed" to "angry", "irritated" to "angry", "pissed" to "angry",
            "biting" to "bite", "bites" to "bite", "chomp" to "bite",
            "blep" to "bleh", "tongue" to "bleh",
            "smooch" to "blowkiss", "smooches" to "blowkiss", "airkiss" to "blowkiss",
            "embarrassed" to "blush", "embarrassment" to "blush",
            "blushing" to "blush", "blushes" to "blush", "blushed" to "blush",
            "bonks" to "bonk", "bop" to "bonk", "headbonk" to "bonk",
            "boredom" to "bored", "boring" to "bored", "dull" to "bored",
            "farewell" to "bye", "later" to "bye", "seeya" to "bye",
            "carrying" to "carry", "carried" to "carry", "lift" to "carry",
            "clapping" to "clap", "applause" to "clap", "applaud" to "clap",
            "confusion" to "confused", "baffled" to "confused",
            "puzzled" to "confused", "huh" to "confused",
            "cries" to "cry", "cried" to "cry", "sobbing" to "cry",
            "sobs" to "cry", "tears" to "cry", "weep" to "cry", "weeping" to "cry",
            "bawl" to "cry", "bawling" to "cry", "wailed" to "cry",
            "snuggling" to "cuddle", "snuggles" to "cuddle", "cuddling" to "cuddle",
            "cuddles" to "cuddle", "embrace" to "cuddle", "embracing" to "cuddle",
            "dancing" to "dance", "dances" to "dance", "danced" to "dance",
            "boogie" to "dance", "grooving" to "dance",
            "eating" to "eat", "ate" to "eat", "munch" to "eat", "munching" to "eat",
            "food" to "eat", "devour" to "eat", "devouring" to "eat",
            "despair" to "facepalm", "facepalming" to "facepalm",
            "feeding" to "feed", "fed" to "feed", "feeds" to "feed",
            "handholding" to "handhold", "holdhands" to "handhold",
            "handshaking" to "handshake", "handshakes" to "handshake",
            "glad" to "happy", "joy" to "happy", "joyful" to "happy",
            "cheerful" to "happy", "happiness" to "happy",
            "heya" to "hi", "hiya" to "hi", "howdy" to "hi",
            "highfiving" to "highfive", "highfives" to "highfive",
            "hugs" to "hug", "hugged" to "hug",
            "kicking" to "kick", "kicks" to "kick",
            "killing" to "kill", "kills" to "kill", "murdered" to "kill",
            "slay" to "kill", "slaying" to "kill",
            "kisses" to "kiss", "kissed" to "kiss", "xoxo" to "kiss",
            "pillow" to "lappillow", "lappillows" to "lappillow",
            "laughs" to "laugh", "laughed" to "laugh", "giggle" to "laugh",
            "giggles" to "laugh", "giggling" to "laugh", "chuckle" to "laugh",
            "chuckles" to "laugh", "chuckling" to "laugh", "kek" to "laugh",
            "licking" to "lick", "licks" to "lick",
            "nodding" to "nod", "nods" to "nod", "yep" to "nod", "yeah" to "nod",
            "nuh" to "nope", "refuse" to "nope", "refusal" to "nope",
            "meowing" to "nya", "meows" to "nya", "mew" to "nya", "mewing" to "nya",
            "kitty" to "nya", "cat" to "nya",
            "headpats" to "pat", "pats" to "pat", "patted" to "pat",
            "peeking" to "peek", "peeks" to "peek", "peep" to "peek",
            "pokes" to "poke", "poking" to "poke", "prod" to "poke",
            "pouting" to "pout", "pouts" to "pout", "sulk" to "pout",
            "sulking" to "pout", "sulks" to "pout",
            "punching" to "punch", "punches" to "punch", "punched" to "punch",
            "running" to "run", "ran" to "run", "sprint" to "run",
            "sprinting" to "run", "sprinted" to "run", "dash" to "run",
            "salutes" to "salute", "saluting" to "salute", "saluted" to "salute",
            "afraid" to "scared", "fear" to "scared", "fearful" to "scared",
            "frightened" to "scared", "terrified" to "scared", "spooked" to "scared",
            "shaking" to "shake", "shakes" to "shake", "shook" to "shake",
            "tremble" to "shake", "trembling" to "shake", "shiver" to "shake",
            "shivering" to "shake",
            "shock" to "shocked", "shocking" to "shocked", "gasp" to "shocked",
            "gasped" to "shocked",
            "shoots" to "shoot", "shooting" to "shoot", "gun" to "shoot",
            "fired" to "shoot",
            "shrugs" to "shrug", "shrugging" to "shrug", "meh" to "shrug",
            "bashful" to "shy", "timid" to "shy",
            "singing" to "sing", "sings" to "sing", "sang" to "sing",
            "song" to "sing", "karaoke" to "sing",
            "drinking" to "sip", "drank" to "sip", "slurp" to "sip",
            "slurping" to "sip", "tea" to "sip",
            "slapping" to "slap", "slaps" to "slap", "smack" to "slap",
            "smacking" to "slap",
            "napping" to "sleep", "naps" to "sleep", "sleepy" to "sleep",
            "sleeps" to "sleep", "slept" to "sleep", "snooze" to "sleep",
            "snoozing" to "sleep", "goodnight" to "sleep",
            "smiles" to "smile", "smiled" to "smile", "grin" to "smile",
            "grins" to "smile", "grinning" to "smile", "beaming" to "smile",
            "smirk" to "smug", "smirking" to "smug", "smirks" to "smug",
            "apology" to "sorry", "apologize" to "sorry", "apologise" to "sorry",
            "apologies" to "sorry", "regret" to "sorry",
            "spinning" to "spin", "spins" to "spin", "spun" to "spin",
            "twirl" to "spin", "twirling" to "spin",
            "gazing" to "stare", "stares" to "stare", "staring" to "stare",
            "glared" to "stare", "glaring" to "stare",
            "surprise" to "surprised", "surprising" to "surprised",
            "astonished" to "surprised", "startled" to "surprised", "wow" to "surprised",
            "taunting" to "taunt", "taunts" to "taunt", "mock" to "taunt",
            "mocking" to "taunt", "mocks" to "taunt",
            "hehe" to "teehee",
            "thinks" to "think", "thought" to "think", "ponder" to "think",
            "pondering" to "think", "hmm" to "think",
            "approve" to "thumbsup", "approval" to "thumbsup", "okay" to "thumbsup",
            "ok" to "thumbsup",
            "tickling" to "tickle", "tickles" to "tickle", "tickled" to "tickle",
            "exhausted" to "tired", "exhaustion" to "tired", "drained" to "tired",
            "weary" to "tired",
            "wagging" to "wag", "wags" to "wag", "tail" to "wag", "tailwag" to "wag",
            "waves" to "wave", "waved" to "wave",
            "winking" to "wink", "winks" to "wink", "winked" to "wink",
            "yawning" to "yawn", "yawns" to "yawn", "yawned" to "yawn",
            "hurray" to "yay", "hooray" to "yay", "woohoo" to "yay",
            "yayy" to "yay", "yippee" to "yay", "celebrate" to "yay",
            "celebrating" to "yay",
            "yeeted" to "yeet", "yeeting" to "yeet", "throw" to "yeet",
            "throwing" to "yeet", "toss" to "yeet", "tossing" to "yeet"
        )

        /**
         * Phrases whose meaning lives in two words, where neither word alone is
         * the action: "high five" is not a five, "holding hands" is not holding.
         *
         * Matched longest-first against the word sequence after the exact-action
         * pass, so "high five" can answer while "high five hug" still answers the
         * hug - the same first-reaction-word rule as [resolve].
         */
        internal val PHRASES: Map<String, String> = mapOf(
            "high five" to "highfive",
            "high fives" to "highfive",
            "hold hands" to "handhold",
            "holds hands" to "handhold",
            "holding hands" to "handhold",
            "hand in hand" to "handhold",
            "shake hands" to "handshake",
            "shaking hands" to "handshake",
            "hand shake" to "handshake",
            "lap pillow" to "lappillow",
            "lap pillows" to "lappillow",
            "wall slam" to "wallslam",
            "wall slams" to "wallslam",
            "thumbs up" to "thumbsup",
            "good night" to "sleep",
            "fall asleep" to "sleep",
            "falls asleep" to "sleep",
            "fell asleep" to "sleep",
            "worn out" to "tired",
            "glomp" to "hug"
        )

        /**
         * Kiss scenes are split into exactly typed folders (`mouth`, `cheek`,
         * `forehead` - `kiss.has_types` is true and it is the only typed action),
         * so a query that says which kind carries the folder. Nothing else can
         * carry a `type`, and sending an unknown one makes the index answer
         * nothing, so only these three map. Plurals and the lip folder are here
         * because "kiss on the cheeks" is how a reader writes it.
         */
        private val KISS_TYPES: Map<String, String> = mapOf(
            "mouth" to "mouth", "lips" to "mouth", "lip" to "mouth",
            "cheek" to "cheek", "cheeks" to "cheek",
            "forehead" to "forehead"
        )

        /**
         * The pairing the query asks for, or null for "any".
         *
         * Pairing is the one thing gifukai's keyless API has that no other index
         * in the app does: `?pairing=ff|mm|fm|mf` picks the genders in the scene.
         * It is not a search parameter, so the client reads it from the query's
         * own words and the picker's Pairings row is four whole searches built
         * that way: a reader asking for "two girls hug" gets the pairing because
         * those words named it, and the same scene without them is still a hug.
         * Both genders named anywhere in the query resolve by the order they were
         * named, which is how the API spells the mixed pairs: female-then-male is
         * `fm`, male-then-female is `mf`.
         */
        internal fun resolvePairing(query: String): String? {
            val words = wordsOf(query)
            if (words.isEmpty()) return null
            val female = words.indexOfFirst { it in FEMALE_WORDS }
            val male = words.indexOfFirst { it in MALE_WORDS }
            if (female >= 0 && male >= 0) {
                return if (female < male) "fm" else "mf"
            }
            val padded = " ${words.joinToString(" ")} "
            return when {
                FEMALE_ONLY_PHRASES.any { padded.contains(" $it ") } -> "ff"
                MALE_ONLY_PHRASES.any { padded.contains(" $it ") } -> "mm"
                else -> null
            }
        }

        /**
         * The gender nouns that name a pairing. Kept apart from [ALIASES] because
         * these never select an action - "two girls" on its own is still not a
         * reaction, and a query with no action is handed to the name index.
         */
        private val FEMALE_WORDS = setOf(
            "girl", "girls", "woman", "women", "female", "females", "she", "her",
            "lesbian", "yuri", "wlw", "wife", "girlfriend", "mum", "mom",
            "mother", "sister", "queen", "princess", "lady", "f"
        )

        private val MALE_WORDS = setOf(
            "boy", "boys", "man", "men", "male", "males", "he", "him",
            "gay", "yaoi", "mlm", "husband", "boyfriend", "dad", "father",
            "brother", "king", "prince", "guy", "guys", "dude", "m"
        )

        /**
         * Same-gender phrases, because a lone "girl" is a person in the scene and
         * "two girls" is the pairing. Only the explicit forms count; "girl hug"
         * keeps the index's own choice.
         */
        private val FEMALE_ONLY_PHRASES = listOf(
            "two girls", "girl and girl", "girl girl", "girls only", "girls",
            "lesbians", "yuri", "wlw", "female female", "ff", "f f", "women",
            "ladies", "sisters", "sister and sister"
        )

        private val MALE_ONLY_PHRASES = listOf(
            "two boys", "two guys", "boy and boy", "boy boy", "boys only", "boys",
            "guys", "gay", "yaoi", "mlm", "male male", "mm", "m m", "men",
            "lads", "brothers", "brother and brother"
        )

        /** Named in the refusal above, so the reader has something to type. */
        private val resolveGifukaiHint = listOf("hug", "pat", "kiss", "cry", "laugh", "sleep")

        private fun wordsOf(query: String): List<String> = query.lowercase()
            .split(Regex("[^a-z]+"))
            .filter { it.isNotBlank() }

        /**
         * Matches [query] against the catalogue: an exact action, a two-word
         * phrase, an alias, or a single word *inside* the phrase — "crying
         * scared" is a cry, because the first reaction word wins. Returns the
         * action, and a `type` folder when the action is a typed one and the
         * query names the folder.
         */
        internal fun resolve(query: String): Pair<String, String?>? {
            val words = wordsOf(query)
            if (words.isEmpty()) return null
            val action = words.firstOrNull { it in ACTIONS }
                ?: resolvePhrase(words)
                ?: words.firstNotNullOfOrNull { ALIASES[it] }
                ?: return null
            val type = if (action == "kiss") {
                words.firstNotNullOfOrNull { KISS_TYPES[it] }
            } else {
                null
            }
            return action to type
        }

        /** A longer phrase is tried first; within one length, the earliest wins. */
        private fun resolvePhrase(words: List<String>): String? {
            for (size in PHRASE_WINDOW downTo 1) {
                for (start in 0..words.size - size) {
                    val phrase = words.subList(start, start + size).joinToString(" ")
                    PHRASES[phrase]?.let { return it }
                }
            }
            return null
        }

        /** The longest phrase in [PHRASES], so the window follows the table. */
        private val PHRASE_WINDOW =
            PHRASES.keys.maxOf { it.split(' ').size }
    }
}

/**
 * Several random draws gathered into one grid's worth of distinct scenes.
 *
 * The endpoint draws with replacement, so the same scene can arrive twice and a
 * grid that showed it twice would read as a broken picker rather than a small
 * folder: duplicates are dropped by id, and the answer is capped at [wanted].
 * A draw that failed does not sink the batch - the scenes that arrived are the
 * answer - but when every draw failed the first failure is the answer, because
 * an empty grid over a refused host is the silent substitution this app does
 * not do. Top-level and internal so the dedupe and the failure rule are pinned
 * by a JVM test without a network.
 */
internal suspend fun gatherGifukaiScenes(
    drawCount: Int,
    wanted: Int,
    draw: suspend () -> Result<GifResult>
): Result<List<GifResult>> = coroutineScope {
    val draws = List(drawCount) { async { draw() } }
    val scenes = mutableListOf<GifResult>()
    var firstFailure: Throwable? = null
    for (pending in draws) {
        val result = pending.await()
        val gif = result.getOrNull()
        if (gif == null) {
            if (firstFailure == null) firstFailure = result.exceptionOrNull()
        } else if (scenes.none { it.id == gif.id }) {
            scenes += gif
        }
    }
    when {
        scenes.isNotEmpty() -> Result.success(scenes.take(wanted))
        firstFailure != null -> Result.failure(firstFailure)
        else -> Result.success(emptyList())
    }
}

/**
 * gifukai's answer, flattened to the picker's shape.
 *
 * The dimensions do not exist in this response - the size is given in bytes
 * only - so they arrive null. The tile does not need them (it crops to a square
 * cell) and the comment embeds the file itself; what the reader's comment gains
 * is the anime's name, which is why [GifukaiResponse.anime] is the title.
 */
internal fun GifukaiResponse.toGifResult(): GifResult? {
    val url = url ?: return null
    if (!contentType.orEmpty().startsWith("image/")) return null
    return GifResult(
        id = url,
        embedUrl = url,
        gridUrl = url,
        title = listOfNotNull(anime, action?.let { it.replaceFirstChar { c -> c.uppercase() } })
            .joinToString(" - ")
            .takeIf { it.isNotBlank() },
        width = null,
        height = null
    )
}

@JsonClass(generateAdapter = true)
internal data class GifukaiResponse(
    val action: String? = null,
    val type: String? = null,
    val pairing: String? = null,
    val anime: String? = null,
    val url: String? = null,
    @Json(name = "content_type") val contentType: String? = null,
    @Json(name = "size_bytes") val sizeBytes: Long? = null
)
