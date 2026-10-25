package com.anisequel.ui.components

import com.anisequel.domain.jokes.JokeTone
import kotlin.random.Random

/**
 * One line and the register it is written in.
 *
 * The tone is attached at the source rather than derived from keywords: "the
 * show has a beach episode" could be filed under craft or feelings depending on
 * the punchline, and a heuristic that guesses would file it differently on a
 * Tuesday.
 */
data class Joke(val text: String, val tone: JokeTone)

/**
 * The lines the random joke toast draws from.
 *
 * ## Why this is not [ApiFailureBody]'s pool
 *
 * The failure pools are about AniList being missing. This one appears while
 * everything is working, so a line about naps and servers would read as a
 * warning about a problem the reader does not have. These are about the hobby
 * instead - backlogs, recaps, rewatches - which is what a random toast can
 * safely be about during normal use.
 *
 * ## Why it is an object with no Android imports
 *
 * The pool is user-facing text that has to stay duplicate-free and large enough
 * to survive a long session; that is a plain JVM test, not a screenshot. Keeping
 * it free of Compose and Android means the test that pins it runs with the rest
 * of the unit tests instead of needing a device.
 *
 * ## Why it is grouped by tone
 *
 * The reader can switch a tone off in Settings, so the pool has to be filterable
 * without a second copy of the text. Four lists of strings, one pass that tags
 * them, and [poolFor] is the only place that knows how a tone becomes a draw.
 * The grouping is also what makes the balance reviewable: a tone that grows to
 * ten lines and one that grows to two hundred are visible in the file.
 *
 * None of these are emoji: the app draws its own vectors, and a joke is carried
 * by the words rather than by a face.
 */
object AppJokes {

    // The words "feelings" and "backlog" are the reader's side of the hobby.
    // Craft is the show's side. Meta is the app's own voice, and it is the only
    // tone allowed to mention Ani-chan or this screen.
    private val feelings = listOf(
        "Every rewatch is a speedrun of your own feelings.",
        "The opening theme is skippable. The emotional damage is not.",
        "Three episodes in, you are legally invested.",
        "The beach episode is mandatory. The plot can wait.",
        "The soundtrack knows you are crying. It is playing along.",
        "Your watch history is a diary you did not mean to keep.",
        "You have recommended this show to four people. None have started it.",
        "The finale is two episodes long. Bring a snack.",
        "The best arc is the one you are about to rewatch.",
        "Your comfort show knows exactly what it is doing.",
        "Your list says paused. Your heart says rewatch.",
        "A rewatch is the same show with better hindsight.",
        "The best episode is always the one you cannot remember.",
        "Your comfort episode is doing a lot of work.",
        "You will rewatch the good arc and skip the sad one. Again.",
        "Your favourite show is one bad recommendation away from ruining a friendship.",
        "Somewhere in your list is a show you started during a different life.",
        "You have cried at a talking animal. It was a good scene.",
        "The beach episode had no budget and all the impact.",
        "Your comfort show has a sad episode and you skip it every time.",
        "Your phone wallpaper is a screenshot from episode nine.",
        "The ending song starts and you are not ready.",
        "The villain's backstory was one episode and it broke you.",
        "You have a show you only watch when it rains.",
        "You keep a mental list of shows to recommend and never recommend them.",
        "Your favourite character has seven minutes of screen time.",
        "The show ended and the fan art did not.",
        "The show is about grief and also giant robots. Both work.",
        "The character you related to in season one is the villain now.",
        "You started it for the animation and stayed for the feelings.",
        "You have recommended the same show to three people this week.",
        "You have a favourite background character. It is fine.",
        "Your comfort character is problematic and you have made peace.",
        "The show was cancelled and the fandom is still writing letters.",
        "You have watched a show for a side character and no other reason.",
        "You have a show you refuse to finish because then it is over.",
        "You know a show is good when the credits song hurts.",
        "You have an anime you only recommend to people you trust.",
        "You have watched a finale twice and cried once. Guess which.",
        "You have quoted a line at somebody who has not seen it. Worth it.",
        "The credits rolled and you sat there for a minute. That is the review.",
        "A show can fix a week you did not tell it about.",
        "You did not cry. You watched the credits twice.",
        "The second watch is where the foreshadowing lives.",
        "Your comfort show does not know it is doing that. Lucky it.",
        "Some shows are a place you visit, not a plot you follow.",
        "You heard the first three notes and your chest did the thing.",
        "The episode you avoided is the one you needed.",
        "The character grows up and you realise you did too.",
        "You have an episode saved for a bad day and a worse one.",
        "The last scene was quiet and that is why it worked.",
        "A fictional death ruined a real afternoon. That is a compliment.",
        "You remember the room you watched the finale in.",
        "The show asked a question and left it there. Better that way.",
        "The rewatch is a different show now. Same discs.",
        "You did not recommend it. You described it for eleven minutes.",
        "The sad episode has a name and you say it carefully.",
        "Somebody's favourite episode is your skipped one. Both valid.",
        "You came for the plot and stayed for the hand-holding.",
        "The show ended and you needed a minute before the next thing.",
        "You have described a scene as the one with the thing. Understood.",
        "The soundtrack is banned from shuffle during a rewatch.",
        "You know which episode you cannot watch again. You will.",
        "The character was fourteen and you were fourteen. Timing is everything.",
        "A happy ending can still hurt if the season is over.",
        "The joy is not the twist. The joy is the second time, knowing.",
        "You have a show that feels like a specific friend's couch.",
        "The final shot is a window and you have thought about it since.",
        "You watched it in one night and have not told anyone.",
        "The best compliment is silence at the end and a rewind.",
        "The rewatch is cheaper than therapy and about as slow.",
        "The show got you through a month and never knew.",
        "You put it on for company and it worked.",
        "The end of a season leaves a shape in the week.",
        "You remember who introduced you to it.",
        "You said you were not attached. The playlist disagrees.",
        "You mourned a show that finished, which is not the same as gone."
    )

    private val backlog = listOf(
        "Your backlog is not a queue. It is a lifestyle.",
        "Nobody has ever finished a watchlist. That is the point of it.",
        "The second season was announced four years ago. Stay strong.",
        "Watching at 1.5x is still watching. Mostly.",
        "The final episode is always next week. Even when it is not.",
        "Every \"just one more episode\" is a promise to your sleep schedule.",
        "There is no wrong way to watch anime, only a wrong amount of sleep.",
        "That show you dropped in episode two gets good in episode nine.",
        "One does not simply stop after the first arc.",
        "Season two is announced. Your schedule is not.",
        "A twelve-episode show and a free weekend. We both know how this ends.",
        "Your queue has a queue. It is queues all the way down.",
        "The cliffhanger ended the episode and your sleep schedule took the fall.",
        "The sequel was announced before you finished the prequel.",
        "Your 'on hold' list is a museum of good intentions.",
        "Episode one of a long runner is a commitment, not a sample.",
        "Somewhere, a second season is still 'in production'.",
        "The three-episode rule was invented by somebody with free time.",
        "You will absolutely start it tomorrow. Right after this one.",
        "Your list is a museum, and you are the only visitor.",
        "A two-cour show is a weekend with a plot.",
        "Your queue has seniority now.",
        "Nobody stops at the end of an arc. That is what arcs are for.",
        "You did not choose the three-episode rule. The three-episode rule chose you.",
        "Your backlog has a backlog. It is a family tree now.",
        "Your watchlist knows things about you that your friends do not.",
        "You started a rewatch to fall asleep and now it is four in the morning.",
        "Your queue is sorted by mood, not by release date. Obviously.",
        "The new season premiered and you are still on season one.",
        "You have three tabs open about a show you have not started.",
        "Your watchlist has a show you added for the poster alone.",
        "Your 'planning' list is a wish, not a plan.",
        "You know the release schedule by heart and still check daily.",
        "You have a show you only watch in the background and still follow.",
        "Your list has a show from a genre you claimed not to like.",
        "You started a show because a clip was 15 seconds long.",
        "You are three episodes behind and the group chat is a minefield.",
        "Your 'dropped' list is mostly shows you will restart next year.",
        "The plan was one episode. The plan has been dead for hours.",
        "Your backlog is a savings account you never withdraw from.",
        "A new season starts and your list gains a third column.",
        "You added a show during a sale and counted it as progress.",
        "The queue is organised. The evening is not.",
        "You call it a watchlist. It has outlived two phones.",
        "Nobody needs a reminder that the new episode dropped. Nobody.",
        "You will watch the first episode of eleven shows tonight. Maybe.",
        "The good kind of problem is a season drop on a weeknight.",
        "You have a show saved for a rainy day and it has not rained.",
        "The list has a category for shows you will never start. It is called Planning.",
        "You will finish it this weekend. The weekend disagrees.",
        "Two episodes left and you have been saying that since spring.",
        "You downloaded the whole season for a flight and slept.",
        "The watchlist is a promise the calendar keeps refusing.",
        "You check the release schedule like it owes you money.",
        "Your sleep schedule has a season pass and no refunds.",
        "There is a show you add to the list every year and never start.",
        "The episode count is a threat if you look at it directly.",
        "You finished a show and immediately added three more. Net negative.",
        "On hold is not a status. It is a mood.",
        "You planned a long weekend around a twelve-episode drop.",
        "The backlog is not behind. It is just wide.",
        "Autoplay was a mistake and you have not turned it off.",
        "You have watched more openings than episodes this month.",
        "The queue is at peace. You are not.",
        "You bookmarked a three-hour video essay as if that were watching.",
        "The sleep debt is real and the next episode is twenty-four minutes.",
        "The sequel you were waiting for is on your list. Paused.",
        "You have a show you are saving for the right mood. It has been years.",
        "The list grew while you were reading this.",
        "The next episode is downloaded and unwatched. A hostage.",
        "You have watched two episodes tonight and added four.",
        "You made a rule about two episodes a day. The rule is gone.",
        "You have planned an evening around a show and cancelled the evening.",
        "The queue survives every attempt to tidy it.",
        "You will recommend it before you finish it. Again."
    )

    private val craft = listOf(
        "A recap episode is the show asking whether you were paying attention.",
        "Filler arcs build character. Mostly the viewer's patience.",
        "Nobody remembers the filler. Except that one beach episode.",
        "Your favourite character survives. In the manga.",
        "The dub is fine. The subtitles are also fine. You are fine.",
        "Anime openings lie. Nothing is that fast.",
        "The movie recap is canon if you believe hard enough.",
        "You can hear the ending theme starting. Do not skip it.",
        "The manga spoiled the anime. The anime spoiled your evening.",
        "The side characters deserve a spin-off. So does your backlog.",
        "The plot twist was in the opening. Nobody watches the opening.",
        "The insert song started and suddenly you are running somewhere.",
        "The villain had a point. The soundtrack agreed with them.",
        "You remember the ending theme. Not the ending.",
        "The episode you skipped had the one line everybody quotes.",
        "You know the opening lyrics. You do not know the plot.",
        "The movie is canon until somebody mentions the movie.",
        "The side story explained more than the main one.",
        "Season three is where the source material catches up.",
        "You paused to read the subs and missed the face.",
        "The fight was two frames and three weeks of discussion.",
        "The plot hole is load-bearing. Leave it alone.",
        "You can hear the insert song. It is that one.",
        "The special was mostly recap and entirely necessary.",
        "The power system makes no sense and you have a forty-minute video explaining it.",
        "Nobody warned you that the recap movie is three movies.",
        "You know the studio. You do not know the director. That is fine.",
        "The side quest episode is load-bearing and nobody admits it.",
        "The ending was ambiguous. The fan wiki is not.",
        "The opening changes in season two and you have opinions.",
        "You recognise the voice actor before the character.",
        "The manga reader in your group chat is being very calm about it.",
        "The movie recap added two scenes and you watched all of it.",
        "A show about people eating is somehow the most tense thing you own.",
        "The tournament arc is a calendar event.",
        "You will not skip the second opening. You have tried.",
        "The best fight was in the episode with the worst animation.",
        "The show has a beach episode, a festival episode and a hot spring episode. It is a documentary.",
        "You have defended a plot hole on the internet. It was a good plot hole.",
        "The season finale was a trailer for the next season.",
        "A recap episode is just the show checking in on you.",
        "Nobody in the show has a normal job and nobody asks.",
        "The opening spoils the ending and you still watch it every time.",
        "The filler episode introduced your favourite character.",
        "The second movie does not exist. You and the fandom have agreed.",
        "The episode with the train was a bottle episode and a masterpiece.",
        "You paused to check the character's name and lost the plot.",
        "The studio announced a movie and the fandom has already written essays.",
        "The plot is a metaphor for growing up. The beach episode is not.",
        "The opening band got popular and now the tickets cost a season.",
        "The dub has one line that lives in your head forever.",
        "You know the exact episode where it gets good.",
        "The spin-off is better than it has any right to be.",
        "You have watched the same fight scene more than the show.",
        "The episode title was a spoiler and you read it anyway.",
        "The fandom invented a ship and the show noticed.",
        "You have a favourite ending song and it is not the popular one.",
        "The show is twelve episodes and the fandom has a three-hour lore video.",
        "You can name the studio from the lighting.",
        "The anime-original ending has its defenders. You are one of them.",
        "The show has a talking cat. That is the whole pitch.",
        "The second arc is better and nobody believes you.",
        "The show has an episode about cooking and it is the best one.",
        "The opening is 90 seconds and you have heard it 400 times.",
        "The show ended on a cliffhanger and the manga is on hiatus.",
        "The show is good. The fan edits are better. Do not tell anyone.",
        "The season is eight episodes and the recap takes two.",
        "The new animation style grew on you. It took a season.",
        "The show's pacing is a crime and you love it anyway.",
        "You can hear the soundtrack in your head right now.",
        "The show has a time skip and everyone looks tired.",
        "The best episode has almost no dialogue.",
        "The show has a hot spring episode and the plot waited. Respect.",
        "The show made you look up a real historical figure.",
        "The ending was rushed and you have accepted it. Mostly.",
        "You have a personal ranking of the openings. So does everyone.",
        "The show has a narrator and he is the best character.",
        "The show has an episode from the villain's perspective and it rules.",
        "The show's food looks better than anything you ate today.",
        "The show has a cold open and you always forget the plot.",
        "The show's title is a spoiler in a language you do not speak.",
        "The show has a training arc and you have notes.",
        "The show's mascot character is doing more plot than the hero.",
        "The show has a silent episode and it is the loudest one.",
        "You recognise the composer's work in the first four notes.",
        "The show's worldbuilding is in the background art and you noticed.",
        "The show has a rival who is right and a hero who is loud.",
        "The anime is a commercial for the manga and it worked.",
        "You have a show you watch only for the ending songs.",
        "The show's cast is enormous and you know all their names.",
        "The episode was 22 minutes and the discussion was four hours.",
        "You can tell the budget episode by the still frames.",
        "The show's best arc is the one everybody else skips.",
        "You have a favourite season and it is not the first one.",
        "The sakuga episode arrives and the group chat goes quiet.",
        "You can hear the budget in the strings.",
        "The background artist drew a town and you want to live there.",
        "A fight with one cut is braver than ten with no idea.",
        "The director's name is in the credits and now it is on your list.",
        "The colour script is doing the emotional work.",
        "A voice actor switches register and the scene lands.",
        "The composer had one motif and eleven ways to break it.",
        "The storyboard for the opening is a short film with a deadline.",
        "You paused a pan and found three characters you never met.",
        "The anime added a scene the manga never had and it fixed the arc.",
        "The adaptation cut the right arc. Somebody had taste.",
        "The sound design made a door into a plot point.",
        "You know the episode by its key frames alone.",
        "The in-between frames are why the walk looks like a walk.",
        "The film grain was a choice and you respect it.",
        "The second season changed studios and the eyes changed with it.",
        "The follow-through on a coat is the whole character.",
        "The source material is finished. The adaptation is still thinking.",
        "You can tell a two-cour show by the recap nobody needed.",
        "The manga panel and the frame are the same picture, and that is skill.",
        "The opening has three cuts that tell the story backwards.",
        "The ending changes at the last episode and it is a knife.",
        "The lighting warmed up a season before the plot did.",
        "The voice director let the silence sit. Rare.",
        "A background track from a drama is doing the exposition.",
        "The remake is good and the original is still right.",
        "The filler had a better board than the canon episode.",
        "The artist drew the same street four hundred times and it got better.",
        "The official art is fine. The fan colourist is better.",
        "The key animator is credited under a name you now follow.",
        "A cut holds for four seconds and says more than the monologue.",
        "The key light tells you who to trust.",
        "The voice actor recorded the scream once and it is in the final mix.",
        "The episode cuts on the beat and the fight becomes a song.",
        "The compositing is why the sky feels like a warning.",
        "The same shot returns in the finale and you have been waiting.",
        "The recap was a chance to fix the pacing and it took it."
    )

    private val meta = listOf(
        "You paused on the credits to feel something. We saw that.",
        "Tier lists are just opinions wearing a nice font.",
        "Ani-chan counted your list again. It is still a lot.",
        "You said last episode out loud. The app heard you.",
        "You opened the app to check one show. That was twenty minutes ago.",
        "Nobody has ever needed a tier list. Everybody has made one.",
        "Ani-chan is not watching your backlog. She is counting it.",
        "The tier list has a lock. Your opinions do not.",
        "This app has one voice and it just used it.",
        "You are reading a toast. The toast is winning.",
        "Ani-chan moved the pill to the top. Do you approve?",
        "The bag refilled, so this line is technically a reprint.",
        "Somewhere in Settings there is a switch for this.",
        "You can turn Ani-chan off. She will not be hurt. Probably.",
        "The list is a work of fiction. So is the plan to finish it.",
        "The app counted again. It gets a different answer every time.",
        "There is no analytics for how many times you rewatched.",
        "The update button and this joke have the same amount of urgency.",
        "You found this line in Settings. It is not a secret.",
        "The dashboard is technically a tier list with extra steps.",
        "A toast is a phrase the app says to nobody in particular.",
        "You closed a sheet and this appeared. That is the timing budget.",
        "The app has been open for a while. So have you.",
        "The favourite button is real. The storage is on this device.",
        "If you saved this line, the heart is on the right.",
        "The pill has a shadow because artwork is busy.",
        "Ani-chan does not read your messages. She reads your progress.",
        "This is the fourth wall. Please do not lean on it.",
        "The tier list is saved. Your keyboard is not.",
        "The filter knows what you meant. The code does not.",
        "You turned some tones off. This one made the cut.",
        "The preview button exists because you did not trust the settings.",
        "Ani-chan has no opinions about season two. She has a schedule.",
        "The app noticed you paused. The app is polite about it.",
        "There is a joke in the release notes. This is not it.",
        "The pill moved. The opinion did not.",
        "Ani-chan counted your tones and then this one slipped through.",
        "The settings screen has more switches than this joke has words.",
        "You are on the jokes page. This is where the lines come from.",
        "The pill can be tapped away. No offence taken.",
        "This line was written before you changed the settings.",
        "Ani-chan draws from a bag so the repeats are not personal.",
        "The app has a timer and no sense of occasion."
    )

    /**
     * Every line, in one list, tagged once.
     *
     * Built from the four groups rather than written as `Joke("...", tone)`
     * pairs: the pairs put the tone on every line and made a 300-line file read
     * as a roll call, while the groups keep each register's voice together and
     * make an accidental tone change visible in a diff.
     */
    val pool: List<Joke> = buildList {
        feelings.forEach { add(Joke(it, JokeTone.FEELINGS)) }
        backlog.forEach { add(Joke(it, JokeTone.BACKLOG)) }
        craft.forEach { add(Joke(it, JokeTone.CRAFT)) }
        meta.forEach { add(Joke(it, JokeTone.META)) }
    }

    /** Just the text, in pool order. The tests and the shuffle bag both key on it. */
    val lines: List<String> = pool.map { it.text }

    /**
     * The lines a draw may use.
     *
     * An empty filter means the whole pool, matching
     * [com.anisequel.domain.jokes.JokeSettings.activeTones]. The fallback is
     * defensive as well as semantic: an empty candidate list would make every
     * draw below return nothing, and a quiet toast with no explanation is harder
     * to diagnose than a slightly wrong filter.
     */
    fun poolFor(tones: Set<JokeTone>): List<Joke> {
        if (tones.isEmpty()) return pool
        val filtered = pool.filter { it.tone in tones }
        return filtered.ifEmpty { pool }
    }

    /** How many lines the current filter can draw from. */
    fun countFor(tones: Set<JokeTone>): Int = poolFor(tones).size

    /**
     * The reader's saved lines in a stable order, with lines this build no
     * longer knows about kept at the end.
     *
     * Pool order rather than save order because DataStore stores a set: the
     * insertion order was never persisted, and pretending otherwise would mean a
     * second key recording timestamps for a list that rarely has ten entries.
     * Unknown lines are not dropped - a saved line that outlives a pool edit is
     * still the reader's line, and deleting it silently is the one outcome worse
     * than an odd sort position.
     */
    fun orderedFavourites(saved: Set<String>): List<String> {
        val known = pool.mapNotNull { joke -> joke.text.takeIf { it in saved } }
        val knownSet = known.toSet()
        val unknown = saved.filterNot { it in knownSet }.sorted()
        return known + unknown
    }

    /** One line, uniformly at random from the tones that are switched on. */
    fun pick(
        tones: Set<JokeTone> = JokeTone.entries.toSet(),
        random: Random = Random.Default
    ): String {
        val candidates = poolFor(tones)
        return candidates[random.nextInt(candidates.size)].text
    }

    /**
     * One line that is not in [seen], or a fresh pick once the pool is spent.
     *
     * Avoiding only the previous line meant the toast could repeat a joke it had
     * told an hour ago - a reader does not experience the pool as a sequence of
     * adjacent draws, they experience it as "has this app told me this before".
     * The caller keeps the set and clears it when every line in the *current
     * filter* has been used, which turns the pool into a shuffle bag: each line
     * once per cycle, in random order, and never the same line twice in a row
     * across the seam.
     *
     * [tones] is the filter rather than the caller's problem: a reader who
     * switched Craft off must not have the bag count Craft lines as unread and
     * then spend eleven draws on lines it will never show.
     */
    fun pickUnseen(
        seen: Set<String>,
        tones: Set<JokeTone> = JokeTone.entries.toSet(),
        random: Random = Random.Default
    ): String {
        val candidates = poolFor(tones)
        val unseen = candidates.filterNot { it.text in seen }
        val drawable = unseen.ifEmpty { candidates }
        return drawable[random.nextInt(drawable.size)].text
    }
}
