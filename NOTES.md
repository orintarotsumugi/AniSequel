# Notes — the weird parts, and why they are like that

Almost everything odd in this app is a scar. A screen that only offers one
button where you expected two, a query that asks for a field "nobody reads", a
snapshot file that expires in twenty-four hours — each of those exists because
the obvious version shipped and was wrong.

The wrong version is what a future reader will restore unless they are told.
That is this file. Read the section for the screen before you change it.

- [README.md](README.md) — the tour, and how to build it
- [ARCHITECTURE.md](ARCHITECTURE.md) — the layer-by-layer spec
- [AGENTS.md](AGENTS.md) — the rules, all of which came from here
- [BUILDING.md](BUILDING.md) — tests, releases, CI
- [UPDATER.md](UPDATER.md) — the update system, which has its own file

---

## Contents

- [The title page](#the-title-page)
- [Studio pages](#studio-pages)
- [Character and staff pages](#character-and-staff-pages)
- [The main bar](#the-main-bar)
- [The profile screen](#the-profile-screen)
- [The tier list](#the-tier-list)
- [Home-screen widgets](#home-screen-widgets)
- [Release notes](#release-notes)
- [Crashes you cannot see](#crashes-you-cannot-see)
- [Deliberate non-goals](#deliberate-non-goals)
- [Keeping the list fresh](#keeping-the-list-fresh)
- [What survives closing the app](#what-survives-closing-the-app)
- [One destination, one copy](#one-destination-one-copy)

---

## The title page

What a tap on a title opens, and the answer to "what is this show" in one
scroll: the key visual blurred behind its own title, the viewer's progress, the
synopsis through the app's AniList-markdown renderer, the production facts, one
Categories block (genres, the weighted thematic tags, and AniList's spoiler tags
behind their own strip), the openings and endings (from AnimeThemes — the one
section on the page that comes from a second service), the external links, the
Titles/Synonyms/Producers disclosure rows, the cast with their voice actors, the
crew, the rest of the franchise, and the things the app can do with the title.
Editing the viewer's entry is the page's *second destination*, on a floating
capsule at the bottom, because editing is a mode rather than a section of the
page.

It replaces three tabs — Story, Specs, My Entry — which had split one title's
information across two of them. Five decisions are worth knowing before changing
it.

**The action row is one control or two, and the button knows which.** Every
title that is not already on one of the viewer's lists gets the quick-add
control: tap plans it, hold or swipe moves it to Currently Watching. A
recommendation keeps the dashboard's own write (it also recomputes the card
behind the sheet); any other title goes through the screen's `PlanningAddStore`,
which is what makes a Library row, a studio credit or an activity entry
plannable without leaving the sheet. A title that *is* on a list draws its badge
instead, because AniList's mutation overwrites the entry — offering "Add to
Planning" on a completed show would move it back to Planning and lose the status
the reader chose.

Whenever the row ends up with a single control (a completed title, or a detail
query still in flight), the AniList button is not stretched: it used to hold
`weight(1f)` unconditionally, so a secondary "open on AniList" link rendered as
the page's primary call to action. It is weighted only when it has something to
share the row with, and a failed write draws its reason underneath rather than
failing silently — only the dashboard owns a snackbar.

**It is a `LazyColumn`, and the scroll drives the motion.** The hero's parallax
and the floating bar read the list's scroll state: the parallax through a
function called inside a `graphicsLayer` lambda (draw phase, so a 120Hz scroll
touches no layout), and the bar through a `derivedStateOf` that produces a
`Boolean` (so it recomposes on the crossing rather than on every frame). As the
hero goes, the backdrop lags the page and dims, the title lockup lifts away
against it, and the two hand over to the docked bar, which slides in on the
spatial spring. That handover is timed by three constants that live together in
`InfoMotion.kt`, because it is a relationship between two files rather than a
rule either of them can break alone.

The docked bar's title pill is also the page's orientation and its way out: its
label is a button that animates the list back to the hero, and the same pill
carries the reader's progress as a wash that fills from the left, scaled in the
draw phase from a `() -> Float` so neither costs a recomposition on a scrolled
frame. It is also a fix, and the reason it is worth reading before touching: the
title used to give up half the bar to a `Spacer` that held a `weight(1f)` beside
it, so a long name was measured against half the width and ellipsized there —
`MARRIAGETO...` with a third of the screen empty beside it. One weighted child is
what lets the pill wrap a short title and still use all the room a long one
needs.

**The staggered entrance is capped, one-shot, and skipped on `INSTANT`.** Seven
sections at 55ms each is 385ms of stagger; the rest arrive together.
`sectionReveal` returns no modifier at all when the motion style is `INSTANT`,
because the delay *is* motion. Its "already played" flag is `rememberSaveable`,
not `remember`: the `LazyColumn` keeps an item's saved state under its key after
disposing the item, and the plain `remember` version replayed the whole stagger
on the way back up the page.

**The wavy progress bar is drawn, not a `LinearWavyProgressIndicator`.** The
height of that wave *is* the progress, which the M3 component's parameters
cannot express; its geometry lives in `domain/motion/WaveGeometry.kt` so the
shape is asserted by `WaveGeometryTest` rather than eyeballed. Its travelling
phase is one `rememberInfiniteTransition`, gated on there being progress to
show, and its fill springs from zero once per title.

Track and fill are one path. The fill used to be built as its own wave, with its
own amplitude and its own travelling phase, so the coloured stretch floated at a
different height from the track it was meant to be the first part of — a short
squiggle over a longer one, with the round cap landing off the curve. It is now
the same path drawn twice, the second time inside a `clipRect` of the watched
width. A clip cannot disagree with the shape it clips, which is the whole reason
for the change; a shared *amplitude variable* would have been one more thing to
keep in step, and it is what the two-path version was already passing to both
halves.

**The data it needs was widened, not faked.** `GET_MEDIA_DETAIL` asks for
`characters` (with `voiceActors`), `staff` and `relations` — one request per
open, on a query that already fetched the synopsis, rankings and tags.

The relation row keeps **this title's own medium plus its cross-medium
`ADAPTATION`/`SOURCE` edge**, and sorts what it keeps: the franchise spine
(`PREQUEL`/`SEQUEL`/`PARENT`) in story order, the adaptation next, the side
material last. It used to filter to `ANIME` unconditionally, which is right for
an anime and exactly backwards for a manga: One Piece's 51 relations are almost
all `MANGA` — a `SPIN_OFF` per volume, a `SIDE_STORY`, a `PREQUEL` — and the
`ANIME` ones are all `ADAPTATION` entries, so a manga page listed the TV series
and none of its own franchise. Matching the subject's type fixed that and then
hid the adaptation in turn, because AniList files one in *both* directions — a
manga page stopped showing the anime it became. The sort exists because AniList
returns edges in insertion order (live check: One Piece interleaves a spin-off
per volume with the adaptations) and the row caps at 12, so the raw order
decided what a long franchise showed. `MediaRelationAndSynopsisTest` pins both
halves.

It also asks for `title`, and **that field is not optional on a node this app
uses as a display record**: it was dropped on the assumption that the summary
the sheet opened from already carried one, which is true on the dashboard (the
response is folded into the list node with `withDetail`) and false on a profile,
where the fetched detail *replaces* the activity row's node. Opening a title from
an Activity row therefore rendered a page headed "Unknown Title" above a correct
studio, status, synopsis and cast. `MediaDetailQueryTest` pins the field.

**The relation nodes share `RELATED_NODE_FIELDS` with the list query.** They used
to carry a second, thinner field list of their own — a title, a format, an
episode count and a small cover — so the one screen whose job is to show the rest
of a franchise could name a related title and say nothing else about it. Each
relation now carries the season, the year, the score, the start date, the airing
schedule and the viewer's own entry, which is what `MediaRelationEdge.metaLabel`
and the card's score badge read. The cost is bounded the way it is there on
purpose: this runs once per *open* over at most twelve nodes, never per entry of
a list.

**Friends who watched it are one request, not one per friend.**
`Page.mediaList(mediaId:, type:, isFollowing: true)` answers "which of the people
I follow have this title on their list" in a single query, with
`score(format: POINT_10_DECIMAL)` normalising every rating onto one scale — the
bare `score` field is in each user's own chosen format, so four friends would
otherwise be four incompatible numbers in one row. The alternative shape, fetch
the following list and then ask for each person's entry, is N+1 requests against
a budget of 30 a minute for a card that is a footnote on the page. It is fetched
by `MediaDetailStore.loadFriends` rather than by `load`, because a cached detail
returns early and a viewer-scoped query can fail on its own; a failure draws no
card instead of an error over "nobody you follow has seen this". The card is
added only when the list is non-empty, `PLANNING` entries are dropped (a plan is
the answer "no, not yet"), and the ordering — rating, then progress, then name —
is applied in the repository so the same title cannot come back in two orders.

**Every piece of sheet state is keyed by namespace, not by number.** AniList
numbers `anime/` and `manga/` independently, so `anime/1` and `manga/1` are
different titles. The scroll position, the half-typed entry draft and the saved
tab were keyed on the id alone, which meant a relation crossing namespaces could
open mid-page with the previous title's state — and the entry tab could save one
title's progress under the other's id. All of it carries `media.type` in the key
now, as does the detail cache that feeds it.

**"Read more" asks the text whether it was cut.** The synopsis toggle used to
decide by counting characters, which is wrong in both directions: at a 200% font
scale 200 characters overflow five lines and the button never appeared, while on
a tablet 300 characters fit and the button revealed nothing. `RenderParagraph`
now reports `hasVisualOverflow` from layout, and the flag is sticky — once
expanded every paragraph is unbounded, so a mirrored flag would make "Show less"
vanish the moment it was pressed.

---

## Studio pages

Every studio credited on a title is a chip on the title page — the hero lockup's
studio chip and the credits in the Details card (the main ones; publishers and
licensors have the Producers row, so no name is printed twice) — and each one
opens that studio's own destination (`studio/{studioId}?name=…`). It is the same
page whether it was reached from a title's credits, from another studio's
filmography, or from a profile; it needs no session, because a studio is public
AniList data.

Five decisions hold it together:

- **It is a route, not a sheet.** The title page is a sheet because it is
  something you look at *from* a list. A studio is somewhere you go: its
  filmography is longer than the page that mentioned it, and a sheet would be
  dismissed by the first back gesture and could not be linked or restored.
  Tapping a poster on a studio page opens the same detail sheet the dashboard and
  the profile use — editor included, because the entry on that tab is always the
  reader's own for the title — so a title is the same page everywhere it appears.
- **`Studio.media`, because `Page.media` cannot answer this.** The one query in
  this app whose shape is a requirement rather than a preference: AniList's
  `Page.media` has **no `studioId` argument**, and a document written with one is
  a 400, not an empty page. The studio's own `media` connection is the only
  documented way to ask, its `perPage` ceiling is **25** (not the root `Page`
  query's 50), and it is asked for `START_DATE_DESC`. `StudioQueryTest` pins all
  four facts, because none of them can fail at compile time and all of them fail
  at runtime.
- **The shelves are years, and the grouping is pure.**
  `domain/studio/StudioYearShelf.kt` turns a page-merged title list into shelves
  — newest year first, and one `TBA` shelf at the *top*, because an announced
  title is the most interesting thing on the page and AniList publishes no null
  ordering to rely on. It also drops duplicate ids, which paging makes necessary
  rather than hypothetical: pages are fetched minutes apart while AniList
  re-orders its own results, so one title can straddle a page boundary and
  otherwise appear twice, in two different years. `StudioYearShelfTest` asserts
  every one of those rules.
- **The title page asks for every studio, not the main one.** The document used
  to select `studios(isMain: true) { nodes { … } }`, which lost the publishers,
  licensors and producers AniList files in the same connection *and* lost the
  `isMain` flag that says which credit made the thing. It asks for the edges now,
  so the card draws every credit — filled for the main studios, quieter for the
  rest — and each one carries the id a tap needs. A credit AniList gave no id for
  is still drawn, as text: it is part of the answer to "who made this", it just
  cannot be followed.
- **`Media.studios` is not paginated, and asking it to be takes down the whole
  page.** The edges selection is written `studios { edges { … } }` with **no
  arguments**. It was `studios(perPage: 25)`, on the assumption that a studio
  connection pages the way `characters` and `staff` on the same node do; it does
  not, and the field takes a `sort` and nothing else. This is the same class of
  fact as the `studioId` one above, with a far worse blast radius: GraphQL
  validates the whole document before resolving any of it, so AniList answered
  **400 with `data: null`** and the detail response lost *everything* it carries
  — the synopsis, the studio credits and the hero's studio chip, the cast, the
  crew, the tags, the rankings, the trailer — not just the studios. The dashboard
  hid most of it because `withDetail` merges onto a list node that already had
  `relations`; a title opened from a profile activity row has no such fallback
  and rendered with nothing but its summary. There is nothing to page anyway: a
  title has a handful of credits and AniList returns all of them under `edges`.
  `MediaDetailQueryArgumentTest` pins it, and pins at the same time that the two
  connections which *are* paginated keep their page sizes.

One more, which is not about the fold but about what the card prints: **the
description is read as prose, not as a document.** AniList's `description` is
markdown-with-HTML-glue and it ends with a provenance line — One Piece's with
`\n<br><br>\n(Source: VIZ Media)`, most with `(Source: AniList)`. The card
renders it through the app's AniList markdown pipeline, which reads that line as
what it syntactically is — a paragraph — so every synopsis ended with its credit
printed in body type under the last line of the story. It also counted toward the
length test that offers "Read more", so a description that fitted on screen grew
a button anyway. `MediaNode.descriptionBody` strips it, anchored per line and
case-insensitively so a source merely *mentioned* mid-sentence is left alone.
`RegexOption.MULTILINE` on that pattern is load-bearing: without it Kotlin
anchors `^`/`$` to the whole description, the pattern then matches nothing but a
description that is *entirely* a credit, and the strip is a silent no-op.

---

## Character and staff pages

Every face on a title's cast and crew rows — and every voice actor on a person's
own page — opens a character or staff sheet over whatever drew it. One composable
and one store serve both kinds; the two payloads stay apart in the state (see
`PersonProfileState`), the renderer does not.

- **Both halves of a media connection.** A character asks for `edges`, because
  the edge is the only half that carries `characterRole` and `voiceActors`; a
  staff member has neither and still asks for `nodes`. `ProfileAppearances.titles`
  reads whichever half the query asked for, so one row of covers serves both.
- **The header is a cover.** The hero is the title page's treatment at the size a
  person's artwork can carry it: the portrait full-bleed behind a scrim that ends
  on the sheet's own container colour, the portrait again as a rounded cover, the
  name on the artwork. It used to be an 84dp face clipped to a pill sitting on
  the sheet's container colour, and the "Appears in" posters were 112×158 pills —
  a stretched pill is an *ellipse*, and an ellipse is not a cover. A portrait is
  a rectangle. The corner is `extraSmall` (10dp), not `medium` (20dp): on a
  54dp-wide poster `medium` rounds away more than a third of the artwork and the
  cover reads as a lozenge, which is what 1.6.4 shipped and 1.6.5 corrected. The
  bio's entity cards use `small` (14dp) for the same reason.
- **A creator's page opens on their description; a character's does not.** The
  biography sat below the works on both, because a long bio measured first pushed
  the row the page exists for below the sheet's viewport. That is the right answer
  for a character — the tap asks who voiced them — and backwards for a creator,
  whose page *is* the description. The staff sheet draws it directly under the
  facts, before the filmography; the character sheet keeps it last, above "Open
  on AniList".
- **"Voiced by" needed a second look at the schema.** AniList hangs the
  performers on each media edge, per language, and the field **answers `null`
  when it is asked without one** — checked against the live API before the query
  was written, not discovered in production. It is asked for twice under two
  names (`voiceActors(language: JAPANESE)` and the `voiceActorsEnglish` alias),
  and the model deduplicates per language, Japanese first, because one actor
  usually voices a character in every title they appear in. A row opens that
  actor's own page in the same sheet; the sheet is re-keyed on the person, or the
  new page would inherit the last one's scroll position.
- **A person is fetched once.** The repository caches profiles, but a *request*
  is not what the reader sees: reopening a face re-entered `Loading` and painted
  the same answer back a frame later. `PersonProfileStore` remembers what it has
  loaded (bounded, cleared wholesale past the cap) and answers a re-open
  synchronously, before the sheet can draw a spinner.
- **A creator's work is read by year, a character's by face.** `staffMedia`
  answers by popularity, so a horizontal row put a 2019 series beside a 1998 one
  with nothing saying which came first — and a creator's page exists for that
  list. It is a vertical list grouped by the title's `startDate.year`, newest
  first and undated last, with the role and the score on each row; a character's
  "Appears in" stays a row, because a handful of covers is something a reader
  scans by face. `ProfileAppearanceYearsTest` pins the grouping, and the queries
  ask for `startDate { year }` — without it every group collapses into "Year
  unknown", which is a regression that compiles.

---

## The main bar

Home, Library, the tier list and the reader's own profile are the app's pages,
switched from a floating capsule at the bottom of the screen — the same
`ExpressiveNavigationBar` the title page uses for its read/edit switch, springs
and all. Home is the dashboard (the signed-in one, or the demo — to a reader
without a session they are the same page), Library is the viewer's own list,
Tiers is the tier list, and Profile is drawn as the viewer's own AniList avatar,
falling back to a glyph while the avatar is still on its way. The viewer's face
is also the way in from the top-left of the dashboard and of the Library page,
and all three go through the same tap.

The bar's pages come from `MainTab`'s declaration order, filtered by one setting:
`enabledMainTabs(tierListTab)` in `ui/navigation/MainTabs.kt`. The order *is* the
enum's order, so the tier list sits between Library and Profile by being declared
there rather than by being spliced into a second hand-written list. The switch
lives on Settings → Library > Bottom bar, defaults to **off**, and only takes the
tab off the bar: the trophy in the Library and the row in the profile open the
same page either way. It shipped on by default for one release on the argument
that a feature nobody can find does not exist; that argument holds for the
feature and not for the tab, because the two other doors are visible and the bar
is the most contended strip of screen in the app.

Which routes get the bar is `mainTabForRoute`, and which of those *draw* it is
`showsMainBar(tab, tierBoardOpen)`. Two functions because the tier list is the
exception, and it is a state rather than a route: the library of lists is one of
the bar's pages and draws it, while an open board does not, because the board's
own tray is pinned over the same bottom edge. The flag is reported by
`TierListScreen` (which knows `opened`) and held by the host (which draws the
bar), and it is reset when the route changes so a board left open cannot hide the
bar on the page that comes next. A route that maps to nothing gets the screen to
itself:
Settings, a studio page, a scanned dashboard and somebody else's profile are
pushed *from* a page, and a bar drawn over them would claim the reader is
somewhere they are not — with a selected tab they never chose. The one that looks
like a literal and is not is the profile: `NavDestination` reports the registered
*pattern* (`profile?tab={tab}`), so the mapping matches the pattern.
`MainTabsTest` pins the mapping, the order of the destinations (the bar turns its
index back into a `MainTab` with `tabs[index]`, never `MainTab.entries[index]` —
the tier tab can be off, and an ordinal would then point one slot too far right)
and that only the Profile tab carries the avatar.

A tab tap replaces rather than pushes: `navigateToMainTab` pops back to Home with
`saveState` and restores a tab's saved state on the way in, so Back from any of
them lands on Home instead of walking back through every tab visited, and
returning to a page restores what it was showing. A repeat tap on the tab already
on screen is a no-op (`launchSingleTop`, the same guard every other hop in the
app uses). The demo profile is a tab like any other: it stacks on the demo
dashboard, so its own back arrow returns Home. It used to replace the demo
dashboard instead, on the theory that a demo profile is not on AniList and should
not stack on it — which popped Home out of the back stack, left the profile alone
with a back arrow that did nothing, and pushed a duplicate profile on every
Home/Profile round trip. The tier list takes this same path from all three of its
doors, so Back out of a board opened from the Library goes Home rather than
Library — the alternative was the same page behaving differently depending on
which door the reader used.

**Holding the bar and swiping it.** A hold on the capsule lifts it and lets the
finger drag the selection: one destination per destination-width of travel, with
a tick each time it moves. Tapping is untouched — the drag only starts after the
hold, and while it is running the items' clicks are disabled so the finger-up
that ends a swipe cannot also fire the tap underneath it. The hold is hand-timed
at 220ms (`HOLD_TO_SWIPE_MILLIS`) rather than taken from
`detectDragGesturesAfterLongPress`, whose timeout is the platform's 500ms: on a
52dp bar that reads as the gesture having missed, which is what readers reported.
The timer is cancelled by a finger-up (a tap) or by travel past touch slop (a drag
that never asked to swipe), so a tap still never becomes a swipe. The
travel is measured against the *measured* bar divided by the destination count,
not against a fixed dp, because the selected pill is wider than the others and a
fixed threshold would move two tabs in one gesture on a phone. The current index
is mirrored inside the pointer handler as well as read from the parameter: the
preview is written back so the pill can follow the finger, but the handler must
not read it back, or two steps inside one frame would both start from the same
index and move one destination instead of two. Dragging right pulls the previous
tab in, the way a horizontal pager does.

The drag moves a **preview**; the page changes once, on release. It used to call
the route change on every step, so one swipe across three tabs popped and pushed
the graph three times — three page transitions under one finger, and the two
pages that were only passed through still composed and started loading before
being replaced. Releasing on the tab the swipe started from is not a route change
at all, and a cancelled gesture (the tier tab being switched off mid-hold) commits
nothing: `swipeSelection` returns the destination only on a real finger-up.

**The bar leaves downwards for an open tier board.** It is drawn by the host
inside an `AnimatedVisibility`, and the board rises from the bottom edge at the
same moment, so the two read as one move instead of the app losing its navigation
on the tap and finding it again on Back. The page the board rises over fades and
settles back rather than sliding sideways: the board is already moving vertically
and the bar is leaving through the same edge, so a third direction in the same
frame would read as two moves fighting. Tab-to-tab moves also have a direction
now: the transition asks `tabDirection`, which compares the two tabs' positions in
`MainTab`, so Library → Home travels right and Home → Library travels left
instead of both sliding the same way. A pop is the same rule read the other way,
because the pair is (popped, revealed). Pages pushed from a tab — Settings, a
studio, somebody else's profile — are not a tab pair, and their pops used to fall
through to the push direction: Settings entered from the right and Back slid the
dashboard in from the right too. A non-tab pop now travels right, so the popped
page leaves to the right and the page underneath enters from the left. A tab move
slides on `FastSpatialOffset` rather than the route token: it is a control the
reader can chain faster than any animation — hold, swipe twice — and on the calm
spring the first slide is still travelling when the second starts, which reads as
a stutter rather than a switch.

The rows the bar floats over leave it room with `mainBarClearance()` — the bar's
96dp plus the device's own navigation inset, read at draw time rather than
assumed, so a gesture bar, a three-button bar and a cutout each get their own
answer. The profile reserves it only where the bar is really drawn: on the
viewer's own profile. A scanned or followed profile is a pushed screen with no
bar and reserves the system inset alone, which is why the flag travels as a
`CompositionLocal` rather than through four tab signatures. The tier list's
library reserves it too, now that the bar is drawn there; without it the last row
of lists ended underneath the capsule.

**The chrome reads the route in its own scope.** The bar and the joke toast are
`MainBarHost` and `JokeHost` in `ui/navigation/MainChrome.kt`, and each calls
`currentBackStackEntryAsState()` itself. They used to be inlined in
`AppNavigation`'s `Box`, in the same composition scope that calls `NavHost`, so
every push, pop and tab switch invalidated that scope and re-ran the graph's
destination table to move two overlays. The route read now belongs to the host
that uses it: a route change recomposes a bar and a toast, not the graph. The
split is also a file boundary — `AppNavigation` is the destination table, this is
what floats above it — and it is the first step of the tracked split of that
file.

**The navigation graph is split by destination group.** `AppNavigation` used to
define every destination inline in one function, putting twelve unrelated screens
and their captured dependencies in a single ~1,100-line file. Each group lives in
its own `NavGraphBuilder` extension now (`AuthNavGraph.kt`, `DashboardNavGraph.kt`,
`ProfileNavGraph.kt`, `TierListNavGraph.kt`, `LibraryNavGraph.kt`,
`StudioNavGraph.kt`, `SettingsNavGraph.kt`), with `WidgetSync` and
`RestoringPlaceholder` hoisted. Captured dependencies are passed as parameters,
keeping every destination file under 200 lines and `AppNavigation` at 557 lines.

**The shape scale publishes all five steps.** Material 3 Expressive surfaces
curved on the 10 / 14 / 20 / 28 / 36dp scale. `ExpressiveShapes.kt` now names all
five (`ExtraSmallCorner` through `ExtraLargeCorner`) and exposes ready-to-use
`Shape` tokens (`extraSmall` through `extraLarge`), eliminating raw corner
literals across detail metadata rows, streaming cards, and profile headers.

**The Library moved out of the profile for it.** The list was the fourth tab of
every profile, including a stranger's, which made the thing a reader consults
most often one tap deeper than the least — and spent a tab of every profile on
the one list that profile was least likely to be opened for. The grid itself is
unchanged (`ProfileLibraryTab`, still in `screens/profile/`), and so is its
memory: the media type, filter, sort and its direction are resolved above the
route and restored on return, along with the place in the grid. The anime half
costs no new request — it is the same `user:<id>` list the dashboard already
fetched, which is why the identity travels as the viewer's id rather than their
name: the by-name key would miss that cache and re-download the app's most
expensive query. Manga is still fetched the first time somebody filters to it,
behind a spinner on that chip, and never again.

**A failure on one of these pages is a sentence, not a scene.** The full AniList
failure theatre — the broken heart, the probe's status strip, the joke and the
"go touch grass" off-ramp — was shared by the dashboard, the Profile and the
Library, on the theory that one failure should look the same everywhere. It read
correctly on the dashboard, which has nothing else to show, and wrongly on the
other two: it arrived over content the reader had been reading a second earlier
and pushed the only control that helps below a screenful of animation. The
theatre now belongs to `ApiFailureBody` on the dashboard's error screen and to
the Settings connection check, which reuses the same heart scene; Profile and
Library draw `ProfileErrorView`, which is the same `friendlyCopy()` words
(headline, detail and one quip) with the two honest exits and no scene. That view
is the shared one: the Library route's pre-`ViewModel` fallback, where the
viewer query itself failed, uses it too.

**A joke turns up above the bar, now and then.** `JokeToastHost` is mounted by
`AppNavigation` next to `MainBar`, so it exists only on the pages the reader
allowed — the bar's pages by default, everything except login when they
pick Everywhere — and never over the sign-in screen. Pace, dwell, edge, tones and
the extra moments are the reader's, stored in `JokePreferences` and described in
`JokeSettings`; the shipped defaults wait 1.5–4 minutes for the first line and
4–15 for the rest, keep a line up for five seconds, and draw from every tone. Its
pool is `AppJokes`, a plain object with no Android imports so `AppJokesTest` can
pin the things a review cannot see: that no line is duplicated, that every tone
has enough lines to stand alone, and that a tone filter only ever draws from its
own list. The wait runs inside a resume gate, so a joke cannot come due while the
app is in the background and be waiting when the reader returns.

---

## The profile screen

Four tabs — Home (identity, bio, pinned favourites), Activity (calendar and
paginated list history), Stats, and Social — switched from directly under the
profile's top bar. They used to be a capsule at the bottom edge of the screen,
which the app's main bar owns now; two bars stacked at one edge would be two
answers to one question. Each tab keeps its own scroll position across a tab
switch, because `AnimatedContent` disposes the tab it animates away from and
anything remembered inside it dies with it.

**A profile paints after one request.** The overview — identity, bio, favourites,
and AniList's own aggregates — is published the moment it arrives. The media list
is merged in behind it, because it is one request per 500 entries and, on a
profile, only Stats reads it; the profile used to wait for the whole download
before drawing a bio that had already come back. Between the two publishes
`UserOverviewUiState.Success.isLibraryLoading` is true, and Stats renders "still
counting" rather than a tally of zero — an empty list is a real answer for an
account that tracks nothing, so the flag, not the emptiness, is what the screen
reads.

**Every favourites row on Home opens something.** Anime and manga open the title
page, characters and staff hand off to the browser, and the studios were the only
row that went nowhere — drawn as a `Text` inside a tinted capsule, so a chip
indistinguishable from a control that did nothing on tap. Each studio chip now
opens that studio's page, the same `(Int, String?)` pair the title page's own
credits hand to the same route, and says whether it animates or publishes (a
distinction the studio page leads with and a name alone does not carry). A chip
with no id is drawn as text rather than as a button that cannot work — AniList's
favourites always carry one, a fixture need not. The overview now fetches
`favourites` and `siteUrl` on the studios as well, because the studio page leads
with both and re-fetching them on arrival would break the one-request bargain
above. `FavouriteStudioChipTest` pins the query *and* the three-hop wiring, since
the likely way this breaks later is a new caller omitting the callback and
inheriting the no-op default.

**The anime theme is Material You, seeded from the cover.** AniList's
`coverImage.color` is one server-computed average of the whole file, and it is
frequently the background wash rather than the artwork, so the app used to theme
itself from a colour the reader could not see. The seed now comes from the cover
pixels: `CoverColorRepository` asks Coil for a 128px software decode (memory- and
disk-cached, the same URL the hero is already loading), `CoverColorQuantizer`
bins opaque pixels into a 5-bit histogram and ranks the bins with Material 3's
`Score` rules — chroma is rewarded, hue is scored by a 15-degree neighbourhood so
a gradient counts as one region, and anything below chroma 5 is dropped. A grey
cover therefore produces *no* seed and falls back to AniList's field, then to the
brand blue, rather than inventing a grey theme.

The seed feeds a pure-Kotlin HCT/CAM16 port (`domain/color/Hct.kt`) that builds a
Material 3 tonal-spot scheme: primary, secondary, tertiary, neutral and neutral
variant palettes sampled at fixed tones — 40/80 for primaries, 10/90 for
containers, 6/98 for surfaces, 30/80 for secondary text, 50/60 for outlines — and
all 36 `ColorScheme` roles are generated for **both** light and dark. The error
palette stays red whatever the show. The anime layer no longer forces dark mode:
it follows Light/Dark/System, and the scheme cross-fades through
`AnimatedColorScheme` instead of cutting. Surfaces take the show's hue only for
the anime layer (`tintSurfaces`); the custom accent in Settings shares the builder
and is a standing preference, so it must not repaint surfaces the user did not
ask to change (`appBackgroundColor` exists to move the background on purpose).

The tone ladder is also the accessibility argument: tone in HCT is CIELAB L*, so
`onSurface`/`surface` and every container pair keep their contrast for a yellow
cover exactly as for a blue one, and `readableOn` flips the foreground when a
hue's tone 40 is too bright for white. `MaterialColorSchemeTest` pins Google's
own `#6750A4` values and AA contrast across hues, `CoverColorQuantizerTest` the
image path, `AnimeThemeSurfaceTest` the tint/surface invariant, and
`AnimeCoverThemeWiringTest` the `isLoaded` race that used to make the feature
work only when DataStore beat the tap.

**Bios** are AniList-flavoured Markdown, rendered by `ui/components/markdown/`:
headings, lists and checklists, code, blockquotes, spoilers (`~!like this!~`),
inline GIFs, `~~~a centred line~~~`, AniList links lifted into cover cards, and
images. A spoiler is two controls rather than one: the collapsed prompt reveals
it, and a labelled **Hide** closes it. The whole block used to be a single
toggle, so *any* tap on revealed text closed it again — a thumb resting on the
card hid what was being read, and a link inside a spoiler could never be opened,
because the tap that reached it was swallowed by the toggle. A bio's cover cards
cost **one** request: see `MediaCoversQueryTest` for why that document asks
through a `Page`, and what used to make AniList reject it. Two deliberate gaps:
tables and `(video)`-style embeds are only partly AniList-documented, so they are
parsed conservatively, and underline has no working AniList syntax (`<u>` is
accepted anyway rather than dropped, since dropping it would print the tag).

**One provider, every markdown surface.** A cover card is only half a card
without its poster, and the poster is a *request*: the document is parsed, the
media links are collected, and the repository is asked for all of them at once.
That plumbing used to be per-caller, and the surface nobody had wired was the one
that needed it most — a person's biography drew every AniList link as a badge in
an empty frame while the profile's About card beside it drew posters, because
only the latter had been given a loader. It is one `CompositionLocal` now:
`ProvideMediaCovers` is called once, at the app's composition root, and
`AniListMarkdownWithCovers` is the only call a renderer needs — it collects the
keys, asks once, and draws. `LocalMediaCoverLoader` is null when there is no
repository (a demo), and a null loader is a real answer: the cards fall back to
the badge rather than pretending a request succeeded. The fetch is keyed on the
*keys* and the loader, not on the parsed nodes — an edit that only changes the
prose around the same links must neither re-request the posters nor clear the
ones already drawn, which is exactly what keying the state on the nodes did.

**The comment thread is a place you can manage, not just read.** Replies hang off
an activity and are fetched when the sheet is opened, with the composer in the
same list. A comment can be liked, and the viewer's *own* comments can be
rewritten or deleted: `SaveActivityReply` with an `id` is the schema's update and
`DeleteActivityReply` is its own mutation. The like and reply controls are one
declaration (`EngagementAction`), not two copies free to drift. Both are gated on the signed-in
viewer's id — the sheet checks it to decide whether to draw the menu, and the
thread's store checks it again, because the server refuses a comment that is not
yours and the UI should not invite the tap. The thread's state lives in
`ui/viewmodel/CommentThreadStore.kt` rather than on `UserActivityViewModel`: the
feed and the thread are two state machines that happen to share a profile, and
the one thing they exchange is the card's reply count, which the store reports
through a callback. A posted comment scrolls itself into view, keyed on the id
the store recorded rather than on the list growing — a thread that merely
*loaded* must not jump to its last comment. Dismissing the sheet is one drag
away from opening it, so the store keeps the thread it just closed and seeds the
next open with it: `isLoading` is then false and `isRefreshing` true, the rows
stay on screen while the fetch runs, and a failed fetch leaves them in place
under a retry banner instead of replacing the conversation with an error. Only a
thread with no rows at all gets the full-screen failure. The two engagement
sheets, the activity feed and the Social tab draw a shimmering sketch of the rows
that are coming instead of a spinner in an empty page (`ShimmerBox`,
`ui/components/ShimmerLoading.kt`).

**Stats is why a profile fetches the list at all.** AniList publishes no
aggregate for country of origin, release year, watch year or episode counts, so
the Stats tab forces the whole anime list to be fetched and chunk-merged — and
the profile used to discard all of it once it had tallied what it needed. The
same list is what the Library page reads (see [The main bar](#the-main-bar)),
which is why it costs no request of its own; manga is the one half nothing else
needed, so it is fetched the first time somebody filters to it and then cached.

---

## The activity feed

AniList returns a person's activity as an `ActivityUnion`, and the feed asks only
for the two list members (`type_in: [ANIME_LIST, MANGA_LIST]`, newest first). The
repository then drops anything it cannot draw: a union member that slipped past
the server-side filter, and a list update whose media has since been removed.
That filter is right — a blank card is worse than no card — but it used to make
the feed's *pagination* wrong. The ViewModel treated a page shorter than 25 rows
as the last page, so one removed title could end a history that still had entries
behind it, and the tab showed nothing to explain the stop.

`GET_USER_ACTIVITY` now asks for `pageInfo { hasNextPage }`; `perPage` is a
variable so the document and the repository's page size cannot disagree; and
`UserActivityPage` carries AniList's own answer up to the ViewModel, which pages
on that boolean and never on `activities.size`. A response without `pageInfo`
falls back to the unfiltered row count — the best remaining guess — but the
document makes that fallback unreachable in practice. `Page.pageInfo` is the
signal AniList's pagination guide names, because `total` and `lastPage` are
documented as unreliable.

A refresh follows the same principle. When the feed already has rows, refreshing
keeps them (`Success.isRefreshing`) and swaps the new page in when it lands; a
failed refresh leaves the rows and puts a retry row above them
(`Success.refreshError`). Only a first load may show the skeleton, and only a
first load that fails may replace the tab with the error state. The profile's
page-level refresh button calls `refreshIfStarted()`, so tapping it on Home does
not spend a request on a feed nobody has opened.

---

## The tier list

`domain/tier/` holds the rules, the geometry, the storage format and the exporter
as pure Kotlin with no Android or Compose imports, so the whole arrangement is
testable without a device. The profile's top bar opens it from a small trophy
beside the name — and **only on your own profile**: the ranking is stored against
the signed-in account, so it means nothing on somebody else's, which is why the
control is hidden off your own rather than shown and re-explained. The Library's
card and the bar's own Tiers tab open the same route; see
[The main bar](#the-main-bar) for why all three doors go through
`navigateToMainTab` and why the bar hides only over an open board.

### Two shelves, because the ids collide

AniList numbers anime and manga independently, so media id 1 is both *Cowboy
Bebop* and *Monster*. A board that ranked both at once would key two titles under
one number: the second placement would overwrite the first, and an export would
draw one title's poster under the other's name. So the pool is a property of the
list rather than of the screen. `TierMediaType` lives on `TierListDocument`
(encoded as a sixth codec line, defaulting to anime so every five-line document
written before manga was rankable still opens), `TierCandidate` carries its own
type, and `TierFilter.mediaType` rejects the other pool inside `allows` - which
means the board, the tray, the filter sheet's match count and the seeding pass all
ask the same question.

The library's Anime | Manga switch chooses which shelf is showing. `TierLibraryPools`
keeps two nullable pools and never concatenates them; null is "not asked yet" and
an empty list is "asked, and there is none", which is what lets the manga half be
fetched the first time its shelf is opened rather than at launch - it is a second
request against AniList's 30-a-minute budget that half of all visits never need.
The pools are their own type rather than fields on the ViewModel because the two
answer different questions ("what can be ranked" versus "what is open"), and
`TierListViewModel.candidates` is the single place that picks one by the open
document's type.

The studio filter is anime-only for the same kind of reason: AniList files
animation studios, publishers and magazines under one `studios` connection, so an
anime studio selection applied to a manga board would hide every manga in it. The
selection is kept on the filter and simply not applied, so going back to an anime
list restores what the reader left there.

### Lists are saved projects, and the screen opens on them

The trophy opens a **library**: the lists you have, with the one you opened last
offered to carry on with, a button for a new empty one, and rename/delete on every
row. Picking one opens its board. There is one route and one piece of state
behind that choice (`opened == null` is the library), so a deep link, a restored
back stack and the system back gesture cannot disagree about where they are.

Each list is a `TierListDocument` — an id, a name, its tiers and its placements —
stored under its own key, with an index of ids beside it. That is deliberate:
`Preferences` is an unordered map, so enumerating its keys would hand the library
whatever order the file happened to be written in, and an interrupted delete
would leave a document behind that still looked like a list. Writes are per
document, so editing one list cannot rewrite another, and every edit is one
`edit` transaction — a removed tier and the placements it drops are no longer two
writes that could be separated by a crash. The list that existed before this
change is migrated into the library once, from the two legacy keys, which are
then left in place as the only copy of an upgrade that cannot be repeated.

### Every edit is undoable, and the undo is the previous document

The ViewModel keeps a bounded stack of the documents as they were. A snapshot
rather than a delta, because a delta has to be inverted correctly for a move, for
a removal that dropped entries filed under the removed row, and for a seeding —
and the one that is inverted wrong corrupts the list silently instead of failing.

The snapshot is read *inside* the store's write transaction, which is what makes
it exactly the state the edit replaced: a snapshot taken by the caller can be one
write behind, and undo would then jump back past work the person wants to keep.

### Dragging is positional, and reaches past the screen

A drop names a tier **and** a tile to go above, so dropping a poster between two
others means what it looks like. A caret marks the seam it will land on. Holding
a tile within 72dp of the top or bottom of the viewport scrolls the screen at up
to 9dp a frame, scaled by how close the finger is, and the ghost is translated by
how far the board has moved since the tile was picked up — without that half, the
tile slides out from under the finger exactly when the drag is longest.

One layout is computed and read by every renderer — Compose, the PNG exporter and
the SVG writer — rather than each arranging the list for itself. Independent
arrangements are how an export ends up with the labels in a different order from
the screen it came from, and the bug is invisible because the exported file
looks fine.

### The lock is a mode, not a gesture switch

The lock used to disable the long-press drag and nothing else, so a "locked"
board still opened the placement sheet on a tap and still accepted a drag out of
the unranked shelf. It is view-only now: the tile installs neither its press
spring nor its `clickable`, the shelf cancels any drag in flight and offers no
Browse, the Add pill is disabled, and the screen's tap handlers check the same
flag. Scrolling and pinching stay available because those are how a locked board
is read. It is still session state, not a stored display preference: persisting
it would leave a board that cannot be ranked after the next launch.

### Pinch is two fingers, and the export is up to 4K

The board's zoom used to be `Modifier.transformable`, which reports a one-finger
pan as movement and consumes it. That is why the board refused to scroll under a
single finger - the column above and below it scrolled while the board itself
felt frozen. A small `pointerInput` now only acts while two or more pointers are
down, so a one-finger drag reaches the screen's `verticalScroll` untouched; the
local zoom value is kept for the gesture because the composition's value is a
frame behind the next pointer event.

The posters on the board are decoded at a size from a ladder
(`tierCoverDecodePx`), not at the size they are currently drawn. `AsyncImage`
asks Coil for exactly its constraints, so a request per frame of a pinch
cancelled the previous one before it could finish, and `AsyncImagePainter` keeps
the last successful bitmap while the next loads - the cover on screen was the one
decoded at the previous size, stretched over a tile that had grown since. The
ladder rounds the request up to the next rung (1.4x apart), so the size only
changes about five times across 35%-200%, and it is always at or above the drawn
tile. The cost is bounded at 40% more pixels than the tile needs, which at the
overview zoom is 40% of a thumbnail.

The board's shareable export is a **PNG**, rendered from that same layout, at
1080, 2160 or 3840 pixels across. It is not a screenshot: a screenshot is the
viewport by definition, so a 300-title board would be forty of them at whatever
zoom the person last used. The output scale is the requested width first and the
heap second: `maxMemory * 0.33 / 4` bounds the bitmap's pixels, a failed
allocation halves that budget and retries, and only a board too tall for the
budget gives width back. Posters are requested at their drawn size with Coil's
memory cache disabled and released batch by batch after they are drawn, so the
peak is one batch rather than the whole board. The board's zoom is deliberately
not the export scale: 35% is a useful way to look at the board, not a useful
resolution to share. `renderTierListImage` reports progress through a `suspend`
callback, once when the layout is ready and then once per poster batch, and the
sheet draws it as a determinate bar: the fetching is the wait, so the batches are
the only place a fraction means anything.

Tile corners are a fraction of the tile's width (`tierTileCornerRadius`), not a
fixed radius. A constant 12dp is a rounded square on a 72dp tile and very nearly
a circle on a 25dp one, which is the "peeled" look the overview had at the 35%
zoom floor. The Compose board, the tray, the PNG exporter and the SVG writer all
read the same helper, and the SVG clips its `<image>` to the rounded rect because
an SVG image element has no corner radius of its own.

`domain/tier/TierListSvg.kt` remains the lossless vector writer — every glyph is
still text and every rule is still a rectangle, and posters are embedded as
base64 rather than linked. It is kept as the renderer `TierListTest` can assert
on without a device, and as the route to a printable export; the app's share
sheet uses PNG because every messaging app accepts it.

### The name search matches words, not substrings

The filter sheet's field is not `title.contains(query)`. People type
`attack titan` for *Attack on Titan*, and a literal substring search finds
nothing because "on" sits between the two words. The query is split on
whitespace and every token has to appear somewhere in the candidate's
`searchKey` — a single lowercased haystack built once per title from the caption
and AniList's English, romaji and native names. That is also why the field keeps
the raw string while `TierFilter.allows` normalises it: trimming on every
keystroke made a second word impossible to type, because the space between the
words disappeared before the next letter arrived.

`searchKey` is a body property on `TierCandidate` rather than a function call in
the matcher. The filter runs over every title on every keystroke, so lowercasing
there was an allocation per title per character; built once, the whole dimension
costs no allocation at all. It sits outside the data class's `equals` because it
is derived text, not a second field to keep in step.

### A caption's length is one decision, and the exports used to make their own

`TierDisplay` carries a `TierTitleLength` beside its position. The board, the PNG
exporter and the SVG writer all read it through `tierCaption`, because the same
name cut in three different places is a file that disagrees with the screen it
documents. Two of those disagreements were shipped before it existed:

- **The SVG writer ignored the display entirely.** It drew a caption under every
  poster whether the board showed names, put them on the artwork, or asked for
the short form — and its own 22-character `truncate` was the only length rule in
the codebase. It reads `TierDisplay` now, draws the on-cover scrim, and clips that
  scrim to the same rounded tile as the artwork.
- **The PNG put the score badge through an on-cover name.** The board moves the
  badge to the bottom corner when the title is across the top; the export kept it
  at the top. It follows the board now.

Both exporters also centre a caption from the paint's own ascent and descent
rather than from a constant baseline. `poster.bottom + 16` happened to sit inside
a 26-unit band at 11pt, which is exactly the kind of coincidence that cuts a
descender off the bottom of the image the moment either number changes.

---

## Home-screen widgets

Four tiles, each answering one question without opening the app:

| Widget | Answers | Tapping it opens |
| --- | --- | --- |
| AniSequel | How much have I logged recently — avatar, handle, latest title | Profile → Activity |
| Missed Sequels | How many unwatched sequels are waiting, and the top one's poster | The dashboard |
| Arriving Sequels | How many are airing or announced soon | The dashboard, arriving section |
| Featured Sequel | One recommendation, given a whole poster — and what it is a sequel *to* | The detail sheet |

They are `RemoteViews`, not Compose, which is the one place in the app where
layout is XML: the launcher inflates them in its own process, so a `Composable`
would not be reachable. `res/layout/widget_*.xml` and the three
`res/drawable/widget_*.xml` shapes are therefore the **only** XML in the UI, and
the "vectors only" rule in [BUILDING.md](BUILDING.md#apk-size) holds for the
Compose surface, not for these.

Three decisions are load-bearing:

- **The provider *is* the receiver.** There was once an `AniSequelWidgetReceiver`
  in front of each provider, and it read only `EXTRA_APPWIDGET_ID` — the system
  sends the update list as `EXTRA_APPWIDGET_IDS`, so the extra was almost always
  absent and a freshly placed tile never received its first update at all.
  `AppWidgetProvider` already unpacks both forms, so extending it is the whole
  fix and there is no forwarding class left to get wrong.
- **The shared update path is an `object`, not a base class.** The four providers
  differ in exactly one thing: which layout they inflate and which data they
  read. The placeholder render, the off-main-thread cover fetch and the "always
  finish the pending result" contract were copy-pasted between them verbatim, so
  a fix to the fetch had to be made four times and a mistake in one copy shipped
  quietly in the other three. `WidgetUpdater` holds it — as an object because
  `WidgetDeclarationTest` reads each provider's source and requires the literal
  `: AppWidgetProvider()`, which a base class would hide.
- **Every instance is painted immediately, without a cover.** The count and
  caption are data the app already has; drawing them first is what stops a new
  tile from sitting blank while a bitmap is fetched.

`exported="false"` with no permission: the system broadcast is delivered either
way and nothing outside the app needs to send one. The old
`android.permission.BIND_APPWIDGET` declaration was in no Google sample and
restricted the declaration for no gain.

---

## Release notes

`app/src/main/assets/changelog.json` is the single source of the app's release
notes, and it has two consumers: the "What's new" card in Settings → Info reads
it from inside the APK, and the release workflow reads the same file from the
checkout to write the GitHub release body. Two consumers, one source, so they
cannot drift — which is the whole point, because the notes used to be a
hardcoded `listOf` in the Info tab that still described v1.0.21 features at
v1.0.23, with no version attached to check it against.

**The card shows the newest block in the file, not the block for
`BuildConfig.VERSION_NAME`.** An exact lookup looks more correct and cannot work:
the workflow *derives* the version it publishes by advancing the patch number
past whatever release is newest, so a repo at 1.0.23 publishes as 1.0.29 and no
changelog file can be written ahead of a number that does not exist until the
build runs. A build one patch ahead of its notes then honestly says "what
changed through 1.2.5" instead of showing an empty card or silently borrowing
the wrong block. `ChangelogTest` pins the newest-first ordering, so "newest"
means new *notes* rather than "whatever is at the top of the file".

Every field is nullable and a malformed file degrades to an empty card, because
this is a hand-edited file read by code that must not throw over a missing comma
and take the Settings screen down with it.

**The update prompt draws the same notes, not a link to them.** The workflow
writes `update.json`'s `notes` from this file — one `- Title: detail` line per
entry — and `parseReleaseNotes` turns that back into the same `ReleaseNote`
values the Settings card renders. One composable (`ui/components/ReleaseNotes.kt`)
draws both, so the dialog somebody decides in and the page they read afterwards
cannot disagree. The prompt bounds the list and scrolls it internally; Settings
leaves scrolling to the page, because a second scrollable region inside a
`LazyColumn` makes the gesture ambiguous about which one is moving.

The prompt used to skip the field and offer the release page, because `notes` had
once held the GitHub release body — the same paragraph about the app in every
release. The parser is deliberately lenient about the format it now reads: only
`- ` lines become entries, the `Build N from sha.` footer is ignored, and a final
line cut in half by the workflow's byte cap is kept rather than dropped. A
manifest that parses to nothing falls back to the release page instead of drawing
an empty box. That cap is now a size sanity bound (6000 bytes) rather than the
1200 that was chosen to fit the old fixed-height dialog — at 1200 a six-entry
release was cut off in the middle of its third entry, so the prompt showed less
than the settings page did.

Shipping changes without adding a block is a broken build, not an oversight —
[AGENTS.md §6](AGENTS.md) makes the changelog and the version bump mandatory
together, and `ChangelogTest` and `VersionBaselineTest` enforce it.

---

## Crashes you cannot see

The app has no crash backend and no analytics, and that is a promise the Privacy
card makes on the About page rather than an oversight. The cost of it is that a
force-stop is invisible: the system dialog says the app stopped, `logcat` on a
device nobody is tethered to is gone, and by the time a report reaches the author
the only information left is the sentence the reader can write down. "It
force-closes while loading the comments" is a true report and an unfixable one —
reading the source can rule a line out, it cannot see the line the device died
on.

`data/diagnostics/CrashReports.kt` closes that gap without a server, and
`data/repository/DiagnosticsPreferences.kt` decides whether it runs. It chains
the platform's uncaught-exception handler, writes the exception, its stack, the
build and the device model to `files/diagnostics/last-crash.txt`, and then calls
the handler it replaced, so the app still stops — leaving a note is the only
difference. Settings → Info → Diagnostics holds the switch, shows the exception
on one line when there is a report, and offers Copy and Show for the whole thing.
Nothing leaves the device unless the reader taps Copy.

**It is off until the reader turns it on.** The About page promises there is no
telemetry, and a build that quietly started writing its own failures to disk
would be a quiet reversal of that promise — the kind nobody notices, because the
file is one nobody looks at until something goes wrong. Turning the switch off
also deletes what was stored: "stop keeping this" is not "keep the one you
already have".

Five decisions in it are deliberate:

- **Chained, not replaced.** Android's own handler is what reports the crash and
  ends the process. Swallowing it would leave the app running in the state that
  just threw, which is worse than the force-stop.
- **One file, not a rotating set.** The interesting crash is the most recent one,
  and five traces would mean five dialogs or a picker nobody wants.
- **The head of a trace, not the tail.** The exception type and the first frames
  identify the bug; the bottom is framework plumbing every crash shares. The
  report is cut at 24k characters and says how much was dropped.
- **Installed in `Application.onCreate`, not in an Activity.** A background
  repository call or a widget update runs without one, and a handler installed by
  the first Activity would miss exactly the failures that happen before it exists.
- **`SharedPreferences` for the switch, `DataStore` everywhere else.** The flag is
  read from the handler, on the thread that is already throwing, in a process that
  is dying; a DataStore read is a suspend call that may never be scheduled. The
  instance is created at startup so the read is a map lookup, and `install` takes
  the value as a lambda so a flip in Settings applies to the next crash without a
  restart.

The format and the file round trip are pinned by `CrashReportsTest`, which is a
plain JVM test: the half of the object worth testing takes a header string and a
`File` instead of a `Context`, so the thing that has to be right when the app is
dying can be checked without crashing it. The default is pinned by
`DiagnosticsPreferencesTest`, because "off" is the consent model rather than a
preference.

---

## Deliberate non-goals

- **No offline-first database.** Every scan is a fresh read. Caching AniList
  lists well is a larger project than this one, and a stale list here produces
  confidently wrong answers, which is worse than no answer.
- **No notifications.** The app offers an update when you open it. A background
  poller would be a battery and privacy cost for a feature a sideloaded app does
  not need.

---

## Keeping the list fresh

The AniList list is the app's whole input, and it is expensive: one
`MediaListCollection` is the single most expensive request the app makes, against
an API that allows roughly 30 requests a minute. It is also the thing that goes
stale while you are not looking — you finish something in a browser, or an
episode airs, and the dashboard you come back to is wrong.

That collision used to be resolved badly, in the one direction that kept the
dashboard stale: the list was cached for a flat hour, and nothing observed the
app coming back. Android **resumes** a process rather than restarting it, so
closing and reopening AniSequel answered from the same in-memory entry, and the
ViewModel's one `init` load never ran again. Reopening was not a reload, and the
only route to fresh data was the refresh button.

Two mechanisms now share one setting, in **Settings → Edit**:

| | |
| --- | --- |
| `RefreshIntervalPreferences` | DataStore-backed choice: Always, 15 min, 30 min (default), 1 hour, Manual |
| `AniListRepositoryImpl` | reuses a completed list response for that long, then re-fetches |
| `ListFreshnessWatch` | decides *when* to ask, owned by `DashboardViewModel` |

The watch is driven by `LifecycleResumeEffect` in `DashboardScreen`, so it acts
on every `ON_RESUME` and on every return to the destination, and it runs a timer
only while the app is actually in the foreground. Two deliberate consequences:

- **A setting changed in Settings takes effect immediately.** Both the repository
  and the watch read the interval per lookup rather than capturing it, so
  picking "15 min" does not need a restart.
- **The timer is foreground-only, and there is no WorkManager job.** WorkManager
  cannot run periodic work more often than every 15 minutes, is deliberately
  inexact under doze, and would spend AniList's request budget refreshing a list
  nobody is looking at.

`Always` and `Manual` schedule no timer at all — `Always` has a zero window, so a
loop would re-fetch continuously rather than on resume, and `Manual` must not
re-fetch without the gesture. Both are decided entirely by the resume check.
After a *failed* automatic fetch the loop waits a five-minute floor before asking
again, so one dropped request cannot become a tight retry loop.

---

## What survives closing the app

The section above decides *when* the app asks again. This decides what it has to
ask for at all, and until recently the answer was "all of it": the repository's
caches lived for exactly one process, so closing AniSequel — or letting Android
reclaim it while the reader was in the browser — threw away the list, the
viewer's own profile, and the synopsis, cast and studios of every title they had
opened. The next launch bought the whole list back (one request per chunk of it,
against an API that allows about thirty a minute), fetched the profile before it
could even name the account, and re-read every title page whose data had not
changed in the ten seconds the app was gone.

`SnapshotStore` (`data/repository/SnapshotStore.kt`) is the tier underneath those
caches: one JSON file per kind under `cacheDir/anilist_snapshots/`, written
atomically (temp file, then rename) and read back with the timestamp it was
written with, so reading a snapshot never renews it.

| Snapshot | Reused for | Why that window |
| --- | --- | --- |
| `viewer` — the signed-in profile | **30 minutes**, fixed | The request every launch made before it could draw anything. Username, avatar and score totals do not change between two launches a minute apart, and a pasted token is verified after the caches are dropped, so this can never vouch for a refused session. |
| `list` — the viewer's own anime and manga lists, and scanned profiles' | the refresh interval from Settings | It is the same answer the in-memory cache holds, so it keeps the same window — including `Manual`, where the disk copy is served until the reader refreshes by hand. |
| `overview` — a profile page's bio, favourites and stats | the same setting | Same reason: it is the profile screen's copy of a thing the list window already governs. |
| `detail` — a title's full record | **24 hours** | A synopsis, a cast, a studio and a release year arrive once and stay; what does move (a score, the next airing episode) is worth re-asking for the next day. |
| `cover` — a bio's batched poster URLs | the list's window | Decoration for the profile that follows that window, and the images behind the URLs are already in Coil's disk cache. |

Deliberately **not** on disk: the activity feed, the follower and following lists,
and character/staff profiles. Each is paged, each is read while it is being
browsed, and a feed served from yesterday would be a conversation the reader has
already seen. They stay in memory for the session, exactly as before.

Five rules keep the disk tier from becoming a bug, and each has a test:

- **A snapshot is served with its own timestamp**, never with "now" — otherwise
  the first lookup of every launch would renew it forever.
- **A file is never trusted.** A corrupt file, a payload today's models cannot
  parse, or one written by a previous release (whose queries may have asked for
  fewer fields) is a miss, and the file is deleted rather than re-tried on every
  open. The release that wrote it is part of the file's identity.
- **A mutation clears the disk copy of the list too.** The list's `listEpoch`
  invalidation was the memory half of "the entry you just saved is not in this
  list any more"; leaving the file behind would have resurrected exactly the list
  the mutation replaced.
- **Signing out deletes all of it.** The list, the bio and the progress inside a
  title record are per-person data, and unlike the in-memory maps nothing about
  the process ending would ever take them off the disk.
- **A title page from disk does not carry stale progress.** The snapshot is the
  title's facts; the viewer's entry inside it is corrected against the list the
  app is holding, or dropped when the list no longer has the title — so the sheet
  and the row behind it cannot disagree about how far in you are.

### The saved copy is a floor, not a ceiling

A snapshot used to be readable only inside the window above, which is the right
rule for the answer to "what is my list *now*" and the wrong one for "can I draw
anything while AniList is not answering". Past the window a cold start had nothing
even though the whole list was on disk, and a failed request replaced the screen
with the failure page.

Since 1.5.3 the dashboard, profile and Library paint the last known copy **before**
the first request leaves, then replace it when a live answer lands. If the request
fails, the copy stays and is marked with a `Saved copy` pill rather than being
thrown away. The readers behind this (`lastKnownViewer`, `lastKnownOverview`,
`lastKnownAnimeList`, `lastKnownAnimeListByUsername`) deliberately ignore the
refresh interval but stop at **30 days**: a month-old list is still this account's
list, a two-year-old one is a different season of their life. A rejected token is
the one failure the copy may not hide — that screen needs the sign-in exit the
error state provides.

The store also bounds itself: past a per-kind byte budget the oldest entries go
first, and the entry just written is never the one dropped.

### Artwork is not scratch space

The saved list and the picture on each row have the same problem, and the two
image paths answered it differently: Coil's disk cache was in the wrong
directory, and the widgets kept nothing on disk at all.

Coil's disk cache was under `cacheDir/anilist_cover_cache`. That directory belongs
to the system: Android empties it for space, "Clear cache" in Settings empties it
on request, and either way the app then redrew every poster as a placeholder and
downloaded the artwork again — on launches where nothing about the list had
changed. It lives in `noBackupFilesDir/anilist_cover_cache` now, with a 256 MB
ceiling, because a cover is page content rather than a disposable transform of it.
`noBackupFilesDir` rather than `filesDir` on purpose: these files can all be
fetched again, so a hundred megabytes of poster art must not be uploaded in a
cloud backup or restored onto a new phone in place of the current artwork.

The cache is keyed on the **image URL**, which is what makes "only change when
AniList changes the artwork" true without any explicit invalidation: AniList puts a
content hash in an artwork URL, so replaced art is a new URL and therefore a miss.
There is no TTL to tune and no `respectCacheHeaders` to fight; a changed cover
cannot be served from an old key.

The home-screen widgets had the same bug a layer down. `WidgetCover`'s `LruCache`
is decoded bitmaps and dies with the process, so every launcher refresh after the
app was killed re-downloaded the same four posters. It now saves the raw bytes
under `noBackupFilesDir/widget_cover_cache`, SHA-256 of the URL as the filename,
written to a `.part` file and renamed — a half-written file that still carried the
cover's name would fail to decode forever, because the URL has not changed either.
Eight files are kept, pruned by modification time; a saved file that will not
decode is replaced by the next successful download rather than trusted forever.

### The waiting state has the shape of the page

With a saved copy painted first, there are now two different waits and they used
to look identical: "the list is on disk and the network is still answering" draws
the list, while "there is nothing on disk yet" has to draw something. The second
was a full-screen spinner in every case, which is wrong on the Library — that page
is a three-column grid of 2:3 posters and looks the same for every account, so the
first frame was one dot and the second was twelve posters plus a filter bar.

`LibraryLoadingView` draws the grid that is coming under an expressive header that
names the step ("Connecting to AniList to find your list", not "Loading your
AniList profile"). `ProfileLoadingView` uses the same header shape, because the
two pages are describing the same state. `RestoringPlaceholder` — the frame before
the stored session is read, which is the app's first impression on a cold start —
carries the app mark, the name and "Checking for a saved session…" so it cannot be
mistaken for a screen that failed to load.

The saved-copy notice followed the same rule. On Home it used to be an item in
the list and on the Library a row in the page above the grid, so its appearance
moved every row below it at exactly the moment the reader was looking at the first
ones. It is part of the top bar's subtitle line now, where a second line is
already reserved and the name ellipsizes before the pill; `SavedCopyPill` keeps
its animated height only where it sits in a header the page is not scrolling.

### The empty-state orb is drawn, not clipped

Every empty state — no comments, an empty tier board, a filtered library — draws
`ExpressiveEmptyOrb`: a silhouette that morphs between two `MaterialShapes` while
the icon inside breathes. It shipped as a `Shape` (`MorphShape`) applied with
`Modifier.clip(...)`, with the progress read inside `createOutline` so that the
animation would invalidate only the draw pass. That reasoning is wrong in a way
nothing in review can show: `clip` builds a `graphicsLayer`, and a layer asks its
shape for an outline when the layer is *configured*, not once per frame. Nothing
in the modifier chain read the animated value, so the layer was never invalidated,
and every empty state kept the single frame of the morph it happened to be built
on. The lopsided, torn-looking clover was one frame, frozen.

`Morph.writeSilhouette` writes the outline into a caller-owned `Path` instead, and
`MorphingEmptyOrb` calls it from `Modifier.drawBehind`. Reading the animation in
the draw lambda is what invalidates the draw pass per frame, which is the same
rule `Modifier.graphicsLayer { }` follows for scale. Do not turn this back into a
`Shape`: a shape that looks like a pure function of its arguments is tempting, and
the layer's outline cache is what makes it not one.

Drawing it unlocked a second bug that the frozen frame had hidden. The morph's two
endpoints were a nine-lobed cookie and an eight-petal flower, and `Morph` matches
curves by feature proximity rather than by vertex index. Those two outlines have no
stable correspondence, so the frames in the middle folded through themselves — the
torn clover again, this time moving. The pair is the adjacent cookie/soft-burst
entry from Material's own loading-indicator sequence now, whose curves are authored
to interpolate. Both the silhouette and the icon read one `ExpressiveMotion.OrbMorph`
clock, which rests on each shape before travelling, so the orb breathes instead of
spinning. `writeSilhouette` takes a `scaleFraction` because the fill is held back
from the box edge to leave room for the aura drawn behind it.

The icon inside the orb also stopped being a per-screen decision. The profile's
Activity and Library tabs sit beside each other and were showing a comment bubble
on one and a timer on the other for the same state — an ordinary empty list — with
a magnifier on top of that once the Library's search matched nothing. All three
now say "empty list" with the list glyph. The magnifier is reserved for a search
or a filter that actually narrowed the list, which is also why `LibraryEmpty`
takes `isFiltered` and says "No titles match these filters" rather than "Nothing
in this list yet" when a status chip is on.

---

## One destination, one copy

Every screen-to-screen hop goes through `NavHostController.navigateSingle`
(`ui/navigation/NavigationActions.kt`) rather than a raw `navigate`. It is one
line — `launchSingleTop = true` — and it is there because a plain `navigate`
pushes the same destination again for every tap. A thumb that landed three times
on the dashboard's avatar left three profiles on the back stack; all three look
identical, so **Back** appeared to do nothing until the duplicates ran out, with
three ViewModels and three scroll positions held alive behind them. It is not a
crash, which is why it survived review.

Routes that need a different back stack (sign-in, the demo dashboard) keep their
own `popUpTo` and are left alone; the helper is for the plain "open a screen"
cases. Tabs are not navigation at all — the profile's four tabs and the tier
board's shelves are one route with an index, so they cannot stack in the first
place.

---

## Settings is a hub, not a tab bar

Settings used to be three tabs: **Info**, **Theme** and **Edit**. Those names
described the source files, not anything a reader was trying to do, and "Edit"
held six unrelated cards — refresh rate, quick add, app lock, the Client ID,
sign-out and the redirect hint — because they had nowhere else to go. The first
tab held the profile, the diagnostic, the changelog and the updater for the same
reason. A tab that means "everything else" gets read end to end every time.

Since 1.5.8 the screen opens on a shelf of rounded icon rows, each with a
sentence under it: Appearance, Jokes, Library & dashboard, AniList account,
AniList connection and About & support. A row opens a focused page and the
`BackHandler` steps back to the shelf; the page state is an enum saved with
`rememberSaveable`, deliberately not a nested nav graph, because the hub is the
only entrance and a link should not be able to land on "app lock" without the
shelf. The old Edit tab was split by subject: refresh, arrival rows and quick add
moved to `LibrarySettingsTab`, the Client ID, session and lock stayed in
`EditSettingsTab`. None of the controls were rewritten — the cards were already
good; they were only filed under the wrong headings.

The transition still drops the default `SizeTransform` (`using null`). The pages
are scrollable columns, and animating the container's height while the outgoing
page settles its own scroll is the crash shape that the tab version already
documented. The fades and scales are draw-phase and stay.

About & support was the exception the split missed: it kept the profile card, the
demo prompt, the list snapshot, a connection check and a sign-out button, all of
which the account page or the shelf's connection row already had — two controls
calling one `logout()` is the version a reader notices. Since 1.5.9 those
account-shaped cards live on `EditSettingsTab` beside the session, the connection
card is gone because the shelf row opens the same dialog, and About is one subject
per card: the build, privacy, updates, the newest release notes and the
documentation link. The shelf's rows now take their title and subtitle from the
`SettingsPage` they open rather than repeating the strings, after the Jokes row
had already drifted to describing a page it no longer matched.

## Joke preferences are data, the host is policy

The joke toast's choices — on/off, pace, dwell, edge, page scope, arrival tick,
tones and the extra moments — live in `domain/jokes/JokeSettings.kt` with no
Android import, and are persisted by `data/repository/JokePreferences.kt`. The
waits and the dwell are properties of `JokeFrequency` and `JokeDwell`, not
constants in the host, because "Calm" and "Chatty" are only names for two pairs of
numbers and "Until tapped" is a nullable duration: a settings screen that owns the
labels while the host owns the numbers can promise a pace it does not deliver.

The tone filter is the same argument one level down. `AppJokes` groups its lines
by `JokeTone` and exposes `poolFor(tones)`, so the shuffle bag cycles the
*selected* lines rather than cycling the whole pool and showing the one in five
the reader asked not to see. The last tone cannot be switched off — the repository
refuses that write as well as the UI, because a stored flag is not a guarantee —
and an empty stored tone set means every tone rather than an empty pool, which is
what a cleared preference or a downgrade looks like.

The lines themselves are in `JokeLinesReader` (feelings, backlog, the app's own
voice) and `JokeLinesShow` (craft and dark), and `AppJokes` is only the index and
the draw. Splitting the text out is what kept the object under the file ceiling
without splitting it by line count: the two files are the reader's side of the
hobby and the shows' side, so a line that moves between them is a line whose
subject changed, which is worth seeing in a diff. The selection API did not move
with the text, which is why `AppJokesTest` still pins the pool without knowing
which file a line came from.

There are five tones since 1.6.0. `DARK` was split out of `META` because the
reader who wants Ani-chan's voice is not necessarily the reader who wants a joke
about a grave, and one switch for both would make the whole feature an all-or-
nothing choice for them. "Mixed" is deliberately *not* a sixth tone or a sixth
list: it is every tone switched on, which is what the default has always been. A
sixth list would drift from the other five and would let the bag count the same
line twice. Settings offers it as a preset that calls `JokePreferences.setTones`
in one write — five `toggleTone` calls would emit five values, and the toast could
draw from a half-restored filter in between, or lose one to a fast tap.

Saved lines are a second flow (`JokePreferences.favourites`) rather than a field
of `JokeSettings`: they are content the reader created, they are not a switch, and
"Reset joke settings" deliberately leaves them alone. The host reads the set only
to draw the heart filled; the Settings card reads it to list and replay.

Moments are announced, not owned. Only the dashboard knows a refresh was asked
for, and only the navigation graph knows a tab changed, so those screens put a
`JokeMoment` on `JokeMomentBus`; the host owns whether the reader enabled that
moment, whether a line is already up, and whether the moment's quiet gap has
passed. Announcing at the request rather than after a successful fetch is
deliberate: the reader asked for the refresh, and the pill is not claiming the
data arrived.

This split is also what makes the behaviour testable without a device.
`JokeSettingsTest` pins that Off never schedules, that the master switch wins
over Chatty, that every wait falls inside its frequency's window, that exactly
one dwell arm waits for a tap, that an empty tone set means everything, and that
a value written by a newer build falls back to a working default rather than
throwing on launch. It is the same reason `AppJokes` has no Compose imports.

`Until tapped` is the one arm the host waits on rather than times. It watches
`visible` through `snapshotFlow` instead of holding a channel, because the flag is
already the truth the surface draws from and a channel would need draining
between lines. That is also why "show me one more" during a manual pill replaces
the line instead of stacking a second one.

A line belongs to the effect that showed it, not to the screen. Every show bumps
a counter and returns the value, and the effect hands that token back when it
hides the pill; a hide carrying a token that is no longer current does nothing.
Before that, every trigger ended its turn by hiding "the pill", which meant
whatever happened to be up: a preview tapped two seconds into a timer joke was cut
off when that joke's dwell expired, and a preview could hide a newer timer line
the same way. The token also matters on cancellation, where a hide written after
the suspending dwell never runs at all — a moment line cancelled by navigating
away used to stay on screen with nothing left to take it down.

The preview button does not mock the toast. It bumps a counter in
`JokePreviewBus`, and the single host mounted by `AppNavigation` — which outlives
the Settings page — draws the real pill at the chosen edge, for the chosen dwell,
from the chosen tones. A saved line can be previewed as itself through the same
bus, because the reader tapped that line rather than "a random one". That is the
only way the preview can be honest about placement, and it is why the host is
mounted even when jokes are off: an explicit request is not the ambient feature.

The Settings-side AniList check no longer reads the failure copy's quip pool. It
is a diagnostic the reader asked for, so every line is fixed copy; the random
voice belongs to the ambient toast, where nobody is waiting for a verdict.
