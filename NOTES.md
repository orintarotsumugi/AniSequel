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
- [The LiveChart cover](#the-livechart-cover)
- [Studio pages](#studio-pages)
- [Character and staff pages](#character-and-staff-pages)
- [The main bar](#the-main-bar)
- [The profile screen](#the-profile-screen)
- [The tier list](#the-tier-list)
- [Home-screen widgets](#home-screen-widgets)
- [Release notes](#release-notes)
- [Crashes you cannot see](#crashes-you-cannot-see)
- [App-wide notices](#app-wide-notices)
- [Deliberate non-goals](#deliberate-non-goals)
- [Keeping the list fresh](#keeping-the-list-fresh)
- [What survives closing the app](#what-survives-closing-the-app)
- [One destination, one copy](#one-destination-one-copy)
- [A seasonal sky is one clock and one seeded list](#a-seasonal-sky-is-one-clock-and-one-seeded-list)
- [Snow does not simulate flakes](#snow-does-not-simulate-flakes-it-remembers-where-they-crossed-the-edge)

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
phase is one `rememberInfiniteTransition`, and its fill springs from zero once
per title.

The transition's existence is gated on the *clock* — resumed, and not on
`INSTANT` — never on the progress. It used to be built only when `target > 0`,
which swapped the composable's shape the first time a title went from untouched
to one episode in, tearing down and rebuilding the transition exactly under the
reader's thumb, and it kept the loop alive with the screen off and against the
motion setting alike. `target` is read where the phase is consumed: an idle bar
draws a still wave at zero phase.

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
*pattern* (`profile?tab={tab}&segment={segment}`), so the mapping matches the pattern.
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

**A hold that never moves is not a shortcut.** It used to be: the main bar routed a
still hold on the Profile tab to `profile?tab=activity&segment=messages`, so the
inbox was one press away. That made the gesture that *starts* a swipe also be a
navigation, and every press held a beat too long and released landed somewhere the
reader had not asked for — reported as the bar opening Messages on its own. The
handler, the route (`navigateToProfileMessages`) and its `OWN_MESSAGES` constant
are gone; a hold now only lifts the bar and ticks, and only travel can choose a
destination. The `segment` argument stays on the route because a link that names a
segment still arrives with it — dropping `&segment=` from the pattern would match
nothing and fail silently.

The `segment` argument defaults to `""`, **not** to null, and that is load-bearing.
A query argument whose default is null still has to appear in the URI, which is the
whole reason every caller here spells `tab=` out — `navigate("profile")` matched
nothing and left the dashboard's avatar tap dead. Adding the segment the way the tab
is declared would have silently un-matched `profile?tab=` and
`profile?tab=activity`, breaking the bar's Profile tab and the home-screen widget
with no error anywhere.

The `segment` argument defaults to `""`, **not** to null, and that is load-bearing. A
query argument whose default is null still has to appear in the URI, which is the whole
reason every caller here spells `tab=` out — `navigate("profile")` matched nothing and
left the dashboard's avatar tap dead. Adding the segment the way the tab is declared
would have silently un-matched `profile?tab=` and `profile?tab=activity`, breaking the
bar's Profile tab and the home-screen widget with no error anywhere.

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
profile's top bar, and a fifth, List, appears only on somebody *else's* profile
(below). They used to be a capsule at the bottom edge of the screen,
which the app's main bar owns now; two bars stacked at one edge would be two
answers to one question. Each tab keeps its own scroll position across a tab
switch, because `AnimatedContent` disposes the tab it animates away from and
anything remembered inside it dies with it; the state saved *inside* a tab — the
Social tab's search text, its A-Z switch, its grid-or-list choice — survives for
the same reason, through a `SaveableStateHolder` keyed on the tab index, because
the transition disposes the tab and `rememberSaveable` alone could not tell one
tab's saved values from the next tab's.

**A profile paints after one request.** The overview — identity, bio, favourites,
and AniList's own aggregates — is published the moment it arrives. The media list
is merged in behind it, because it is one request per 500 entries and, on a
profile, only Stats reads it; the profile used to wait for the whole download
before drawing a bio that had already come back. Between the two publishes
`UserOverviewUiState.Success.isLibraryLoading` is true, and Stats renders "still
counting" rather than a tally of zero — an empty list is a real answer for an
account that tracks nothing, so the flag, not the emptiness, is what the screen
reads.

**That download is now the tab's to ask for, and only on somebody else's
profile.** `domain/usecase/ProfileLibraryAccess.kt` decides it: the reader's own
list still comes with the profile, because the main bar's Library page renders
`libraryAnime` and has no trigger of its own; a stranger's waits for the tab that
shows it (Stats or the borrowed list), because forty sequential chunk requests is
a bill the reader who came for a bio never agreed to pay. The same flag covers a
third state now — not "counting" but "nobody has asked yet" — and
`UserOverviewViewModel.loadAnimeLibrary` is the one call that clears it, guarded
against re-entry the same way the manga fetch is. The by-name routes get the id
back from the overview (`updateUserId`) for the manga half only: `getUserMangaList`
is addressable by id alone, while the list and profile keep fetching by name so
they hit the `userName:` cache entry the dashboard already filled.

**Another person's list is a fifth tab, on one condition.** `ProfileLibraryTab` is
reused rather than rewritten, and `canBrowseProfileLibrary` refuses it for the
reader's own profile and for the demo — their list is the bar's page, and two
doors to it would be two remembered views behind one label. The condition the
reuse has to honour is *whose* view the chips write to: `borrowedLibraryView()`
hands a borrowed grid a store that forgets, because pointing it at the reader's
`LibraryPreferences` would both draw a stranger's grid in the reader's own
"manga, by score" and write the stranger's sort into the store the reader's
Library page opens from.

**The Social tab's two lists are one job, and one failure.** It fetches followers
*and* following because the reader is one tap from switching, so `SocialState`
carries both: `isRefreshing` keeps the rows already on screen through a re-fetch
(skeletons over fifty people read as the data being lost), and `failedList` with
`failureMessage` attributes a refusal to the half it happened to instead of
replacing the grid being read with an error for the one nobody asked for.
`domain/usecase/SocialFetchPolicy.kt` holds the two rules that keep that to one
pair of requests per visit: entering asks only when nothing is on screen, nothing
is in flight and nothing is failing (a failure stays behind its own retry button),
and a chip tap during a fetch moves what is *shown* rather than cancelling the job
that already answers both. The unresolved-id case publishes an `Error` and not a
`Loading`, because `Loading`'s only exit is the answer to a request that branch
never makes — a permanently spinning tab.

**A profile route says *who*, and "nobody" is an answer.** Three routes reach this
screen — the viewer's own, a scanned username, and `profile_id/{userId}?name=` for
anybody tapped in the Social grid. The by-id route used to read
`getInt("userId") ?: 0`, which turned an unfilled route into a live request for
AniList user 0: a real account, with a real bio and a real name, painted as the
person the reader tapped. `domain/usecase/ProfileIdentity.kt` replaces that — a
non-positive id is not a person, a name with no id still is (fetched by name), and
a route carrying neither is reported as a failure that cannot be retried, because
retrying it would ask the same question of nobody. The name travels with the id
because the tapped row is the only place in the app holding both: it fills the top
bar on the first frame instead of reading "Profile" for a round trip, and it is
half of the ViewModel key — `profileViewModelKey` needs name *and* id, since two
accounts can share a display name and a scanned profile has no id at all.

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

**The immersive layer and the retained colour are two different things.** They
used to be one stored boolean, `anime_theme_active`, which answered "may a detail
sheet recolour the app" and "is the app recoloured right now" at once — and both
answers were wrong half the time. A retained colour left the *immersive* layer
switched on, so the show's hue stayed in every surface, container and outline on
every screen and came back on the next launch: the reader kept the accent, but the
app kept the tint too, which is exactly what a standing preference is not allowed
to do. A temporary colour was worse. Its revert timer is an in-memory coroutine,
the flag is on disk, so killing the app before the timer fired left a "5 minutes"
tint applied with nothing left to clear it, until the next title overwrote it.

Now `ThemePreferences` stores the switch (`anime_theme_enabled`, read from the old
key until the first write) and holds the applied layer in a process-scoped
`StateFlow`. `applyAnimeColorLayer(hex, retain)` is what a sheet does on opening;
`clearAnimeColorLayer()` ends it on closing. A third stored key,
`anime_theme_retained_color_hex`, records that the current accent *is* the anime's,
which is what lets turning the feature off take back that colour and leave a hue
the reader chose alone — the old code guessed by comparing hexes, so a custom
`#7FA9D4` could be deleted by a cover that happened to share it. Anything on a
timer now dies with the process, which is the honest lifetime for a preview.

**Retention has to keep the background, and that is a correction to the design
above.** The first version of this split was defensive in the wrong direction: it
kept the colour as an accent and deliberately dropped the tinted surfaces, so the
reader who opened a reddish show watched the app go red and then watched every
surface snap back to the brand navy on the way out. Retain, to someone who chose
it, means "keep what I am looking at" — and the thing they are looking at is the
background. So the theme now seeds its tinted scheme from
`anime_theme_retained_color_hex` whenever no sheet is open, and the live layer is
only what makes the same colour arrive *before* the marker is written. The two
lifetimes still differ, which is the part that was right: the layer is process
state, the marker is on disk, and the marker is the only one that can survive a
restart. `MainActivity` gates the marker on the feature *and* the retain switch, so
a marker left by a downgrade cannot outrank the reader's own palette, and the exits
that remove it (feature off, Retain off, an explicit accent, Reset appearance) are
the same ones that were already there. Retention and the timer stay mutually
exclusive: off means the sheet is a preview and the app returns to its own theme.

**The retained tint has a strength because full and accent-only are both honest.**
`RetainedTint` (Subtle 0.35, Balanced 0.7, Full 1) is stored beside the retain
switch. It is applied only in the retained branch, by blending the generated
scheme's neutral roles toward the palette (or the custom accent's own neutral
surfaces) while every accent and container role stays the cover's. While a sheet
is open the layer is always full strength - a preview that disagrees with the
sheet is not a preview - and a strength of 1 is the old behaviour exactly. This is
why the blend is at the theme boundary and not in `MaterialColorSchemes`: the
tonal ladder still generates a contrast-safe scheme, and the setting only decides
how much of it the background keeps.

The seed's own tone is normalised on the way out as well. `CoverColorQuantizer`
scores pixels by chroma and population, not by tone, so a night-sky cover legitimately
wins on a near-black bin — and the app then painted that pixel as a swatch. The
Settings chip and the hero's placeholder and shadow ask
`MaterialColorSchemes.accentFor(seed, dark)`, the tone 80/40 the scheme itself uses
for `primary`, so the colour a reader is shown is the colour the app is drawn with
and neither can be a black box on a black sheet.

The tone ladder is also the accessibility argument: tone in HCT is CIELAB L*, so
`onSurface`/`surface` and every container pair keep their contrast for a yellow
cover exactly as for a blue one, and `readableOn` flips the foreground when a
hue's tone 40 is too bright for white. `MaterialColorSchemeTest` pins Google's
own `#6750A4` values and AA contrast across hues, `CoverColorQuantizerTest` the
image path, `AnimeThemeSurfaceTest` the tint/surface invariant, and
`AnimeCoverThemeWiringTest` the `isLoaded` race that used to make the feature
work only when DataStore beat the tap.

**One vocabulary for AniList's six list statuses.** `MediaListStatus` has exactly
`CURRENT`, `REPEATING`, `COMPLETED`, `PLANNING`, `PAUSED`, `DROPPED`, and four
surfaces used to keep their own private copy of what those words mean: the entry
editor's picker, the Library's filter chips, the Stats tab's tally and the tier
board's filter sheet. They drifted — the editor and the chips called `PAUSED`
"Paused" while the tally called it "On-Hold", so a status you had just set was
not findable under the name you had given it. `domain/list/ListStatuses.kt` is
the one table now, and it carries *both* spellings per option because AniList
models one enum for anime and manga and nobody says they are watching a book;
callers pick with `labelFor(isManga)`.

The same table is what makes the Library's chips work on old data. The chips used
to compare the raw status string, so an entry recorded under API v1's `ON_HOLD`,
or a cached `WATCHING`, or v1's seventh status `CLEARED`, matched no chip and
vanished from every filter while still counting under "All". `normalize` folds
those aliases onto the six; an unknown token still matches nothing, because
filing a status this build has never seen under its nearest neighbour is a lie
about the entry. The activity feed is deliberately *not* in the table: a list row
says a title *is* "Watching", the feed says the reader "Watched" it, and those
are different sentences about different things.

**The shimmer measures its own box.** `rememberShimmerPhase` publishes a fraction
and `shimmerBackground` scales it, because the sweep used to run between two
absolute pixel constants with a fixed 300px bleed: on a 100dp chip the highlight
crossed in a tenth of the cycle and on a tablet card it never reached the far
edge at all, so the same skeleton looked half-painted at one width and strobed at
another. It also branches on `MotionStyle.INSTANT` like every other loop in the
app — the one control the reader has over motion had no idea the most
constantly-drawn animation in the app existed — and it sweeps linearly, because
ease-in-out braked into both edges, which read as the highlight stopping in the
middle of a card and starting again.

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

AniList returns a person's activity as an `ActivityUnion`, and the feed asks for
the two members it renders: the two list types (`ANIME_LIST`, `MANGA_LIST`) and
`TEXT`, newest first. The repository then drops anything it cannot draw: a union
member that slipped past the server-side filter, and a list update whose media
has since been removed. That filter is right — a blank card is worse than no card
— but it used to make the feed's *pagination* wrong. The ViewModel treated a page
shorter than 25 rows as the last page, so one removed title could end a history
that still had entries behind it, and the tab showed nothing to explain the stop.

`TEXT` was missing for the feed's whole first life, which meant a person's status
posts — the part of the profile where they actually talk — never appeared on the
tab built to show their activity. The feed was only ever showing list updates.
One flat model now reads both members: a list update has `media` and `status`, a
text post has `text` and no media, and `ActivityCard` picks the card shape from
exactly that. `MESSAGE` stays out — it is a site message between two users, and a
private message on a public tab is not a feed. The body alone cannot tell a
message from a post (both have `text`), so the model reads the union's
`__typename` as the discriminator; `type_in` already excludes `MESSAGE`, and the
typename is the second line of defence for a response that ignored it.

The three engagement documents (`GET_ACTIVITY_REPLIES`, `GET_ACTIVITY_LIKES`,
`TOGGLE_ACTIVITY_LIKE`) each fragment *both* members. They fragmented only
`ListActivity`, so opening a text post's thread or likers answered with an
object that had no selected fields — an empty sheet over a post that has
comments. Liking a post worked; reading what anyone said about it did not.

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

## App-wide notices

Two failures the reader must be told about even when no screen asked for them,
and the one write confirmation that has nowhere else to go, are raised on a
process-wide channel and drawn as a `Toast` at the composition root
(`MainActivity`). The reason is a hole no screen can cover: a *background*
refresh - a silent re-fetch, a sheet's paging request, a comment still in flight
after its sheet closed - has no surface left to explain itself on, so without a
global notice it fails as a dead spinner or silent success.

- **`RateLimitNotice`** carries only a 429, because a rate limit is a distinct
  instruction ("wait, do not tap") with a `Retry-After` number attached. It
  reports from the one place a 429 is classified (`AniListException`), so both
  roads that recognise one - the HTTP status and a GraphQL `status == 429` entry -
  cannot drift into one notifying and the other going quiet.
- **`SystemNotices`** carries the rest: `SessionExpired`, `ServerDown`, `Offline`
  (all raised from the same classification choke point, so reads *and* refused
  writes are covered in one place) and `CommentPosted` (raised only when a comment
  lands after its sheet closed - the inline scroll confirmation has no window left
  to draw in).

Both buses deduplicate a burst to one notice per episode, because an outage or a
dead token fails every in-flight request in the same second and each would
otherwise post its own Toast. The window is **per kind**, not global: a server
blip can clear in a minute, a dead token should not nag more than every five, and
keying the window on the notice means one kind's storm cannot silence another's.

This is deliberately a `Toast`, not a `Snackbar`: the failures land most often on
`ModalBottomSheet`s, which live in their own window over the host screen, so a
Scaffold snackbar would post behind the sheet where the reader is not looking.

---

## The LiveChart cover

**The app now reads one image from a site it used to only link to.** For its whole
life LiveChart was "a link, not a scrape": it publishes no API - its FAQ offers
only RSS - so the app built the search URL the outbound "LiveChart" pill uses
(`liveChartSearchUrl` in `ui/components/LiveChart.kt`) and drew nothing from the
site, on the reasoning that scraping a host that has asked not to be consumed that
way is not AniSequel's call to make. **The owner has reversed that for one bounded
thing: the full-screen cover viewer now adds LiveChart's own poster as a third
source**, because LiveChart's artwork is more numerous and higher quality than
AniList's for many titles. This is a deliberate decision recorded here and in the
KDoc on `LiveChart.kt` and `FullScreenCoverViewer.kt`, not an oversight - everything
the old rule warned about (reverse-engineered endpoints, reading the schedule,
touching anything but a poster) is still off-limits.

**What is actually fetched, verified against the live site.** There is no endpoint,
so the fetcher (`data/network/LiveChartPosterApi.kt`) opens the one page the site
advertises in its own schema.org `SearchAction`, `https://www.livechart.me/search?q={term}`,
and parses the poster straight out of the HTML. A result's cover is an `<img>`
tagged `data-anime-item-target="poster"` whose `alt` is the title and whose `src` is
the thumbnail; LiveChart serves posters from the CDN host `u.livechart.me` at
`/anime/{id}/poster_image/{hash}.webp/{size}.jpg`, where `small.jpg` is the result
thumbnail, `large.jpg` is the anime page's own `og:image`, and the underlying source
is the `.webp`. So the viewer is handed the `large.jpg` variant, not the little
thumbnail. The pure parser is pinned in `LiveChartCoverTest` without a network.

**The gallery is the title's own `/visuals` tab, and it is read from the poster's
id.** LiveChart's search result names a poster, and that URL carries
`/anime/{id}`; the fetcher derives the title page from it rather than matching the
same title a second time against the search page's anchors, then opens
`/anime/{id}/visuals` with the same browser User-Agent and a `Referer`. Every card
on that page puts its full-size image in `data-anime-visual-url-value`; those are
the gallery's pages, in site order, de-duplicated and capped at 40. This is the
same path AnymeX uses. A title with no visuals keeps its LiveChart poster as a
single-image source; a failed second request still leaves the poster and never
fails the viewer. Both requests share `LiveChartCovers`, so two opens of one title
cost one gallery fetch, and an empty answer is cached for the shorter miss window.

**A browser User-Agent is load-bearing, not a nicety.** Verified:
`https://www.livechart.me` answers **403** to OkHttp's own default agent and to an
empty one, and only returns the page for a normal desktop Chrome string - which is
what the fetcher sends. The poster host is less strict (`u.livechart.me` returns
200 to any agent), so Coil loads the chosen image directly with the app's normal
image loader; only the *discovery* request needs the header.

**The match is conservative, and failure is invisible.** LiveChart titles entries in
romaji and treats English as an alias, so a search for one show returns a dozen
near-misses - sequels, spin-offs, unrelated shows sharing a word. The picker scores
exact > prefix > partial, prefers the shortest agreeing title (so "Sousou no Frieren"
settles on the base rather than its 2nd Season), and returns nothing when no result
overlaps the query at all rather than guessing. The request runs on `Dispatchers.IO`,
is cached for a session by `LiveChartCovers` on the same politeness argument as
`AniThemes`, and every failure path - 403, offline, a page that changed shape, a
manga (LiveChart tracks anime only, so none is searched) - means one thing: no extra
pill. AniList's poster and banner still paint immediately; the LiveChart source is
appended only once it arrives.

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

## The comment sheet is a chat, and the file host is a second service

AniList's `Activity(id:){replies}` has **no arguments**. It is the thread's whole
comment list in one response, and the busiest text posts on the site run to
thousands. That was invisible while threads were small and is the reason opening a
comment sheet on a popular post was the slowest thing in the app: one request, one
parse and a list of a hundred-odd rows for a reader who wanted the last screen of
the conversation.

So the sheet reads `Page(page, perPage){activityReplies(activityId)}` - one page -
and the newest comments are on the **last** page, because replies come back in
ascending id order. That fact is verified against the live API rather than assumed,
and [CommentWindow](app/src/main/java/com/anisequel/domain/usecase/CommentWindow.kt)
holds the arithmetic: `ceil(replyCount / perPage)` aims the first request, and the
count that arrives with the page is checked against the aim, because the feed card's
own count is minutes stale on a thread people are posting to. A guess that lands on
the same page is not re-requested; a guess that hides the newest page costs exactly
one more request, and never a second re-aim.

`GET_ACTIVITY_REPLIES` stays, and is what the sheet uses when a reader turns
Settings ▸ AniList ▸ **Chat view** off. It is not dead code and it is not a
compatibility shim: it is the other answer to "show me this conversation", and the
one that is a single request for a short thread either way.

Row grouping - hiding the avatar and name when the person above is still speaking -
is [CommentGrouping](app/src/main/java/com/anisequel/domain/usecase/CommentGrouping.kt),
and it requires the *same depth* as well as the same author. `ReplyThreader` indents
an answer under what it answers; merging across that indent would draw somebody's
answer as a continuation of the wrong message, which is a claim about the
conversation rather than a saving on pixels.

Polling is a separate switch from the window for one reason: opening at the newest
message is one request, and staying current is one every fifteen seconds. AniList
budgets roughly thirty requests a minute, so a reader on a metered connection may
want the first and not the second.

**A reopen refreshes the newest end of the cache, not its oldest page.** The store
keeps the last closed thread so a dismiss by accident is instant (see
`CommentThreadStore.close`), and the cache records both `oldestPage` and
`shownRows`. `open` used to aim the reopen at `oldestPage` - the page a reader
reached with Older - and merge it as a first read, which *replaces* the window. So
the sheet came back on the oldest page that was loaded, with every newer row gone,
and the conversation appeared to jump backwards. Messages kept going through the
same window and jumped with it. A reopen now aims at `lastPage(cachedReplyCount)` -
the newest page the cache ends on - and merges as a refresh, so the rows on screen
stay. A refresh does not re-aim at the current newest page: the page in hand is
still the right page, and jumping forward would skip the rows between. If the thread
grew exactly one page meanwhile, that page is fetched too; if it grew by more than
one, the pages in between were never fetched and there is nothing to bridge them
with, so the sheet opens the newest page as a fresh window rather than drawing a
conversation with a hole in the middle that Older can never fill.

**Three things must not move the reader, and each of them did.** A chat is a place
with a position in it, and this sheet has three code paths that change the rows above
the position - an Older page, a window that reveals rows it had already fetched, and
the scroll that follows a comment you just posted. Each one was wrong in its own way,
and all three were invisible in a screenshot.

- *Rows arriving above you.* `LazyColumn` is not reversed here (replies ascend, and
  the newest belong at the bottom), so prepending a page keeps `firstVisibleItemIndex`
  and moves the conversation: the comment being read slides off the top and every
  placement spring animates the jump. The sheet now remembers which reply was at the
  top of the viewport and how far into it you had scrolled, and puts that back after
  the list grows. [CommentWindow.prependedRows](app/src/main/java/com/anisequel/domain/usecase/CommentWindow.kt)
  decides whether a change is a prepend at all - only a list that *ends* with the old
  one is - so a refresh that replaced the newest end, a delete, and the first fill of
  an empty list are left alone rather than "compensated".
- *The editor closing.* The flag that says "this thread has been placed at its newest
  row" lived inside the thread, and the thread is *disposed* whenever the composer is
  up, because the two panes replace each other. Quote a comment five pages back,
  Cancel, and the flag came back false and the sheet re-ran its open-at-newest scroll:
  a teleport to the bottom of a conversation you had walked up. It is held by the
  sheet now, saveable, keyed by the thread. The same effect also stands down when a
  post is being scrolled to, because two scrolls starting on one frame cancel each
  other and the reader lands on neither row.
- *The scroll after you post.* It counted your comment's index in the *fetched* list
  while the list draws the *window*, so on a thread you had scrolled up in the index
  pointed past the end - `animateScrollToItem` clamps rather than throwing - and you
  stopped on the newest comment instead of your own. `CommentWindow.hiddenPrefixRows`
  is the difference between the two lengths, and the rows above the replies (the
  message head, the failure banner, the Older control) are counted by the one
  function the list itself uses.

**Refresh is a button, and it is one request.** The header carries a Refresh control
beside the line that says when the thread was last checked, because a reader who can
see "Checked 40s ago" does not need to tap, and one who sees "8m ago" knows the tap
will answer. It issues exactly the poll's request, and a second tap while one is out
does nothing: AniList holds a session at roughly thirty requests a minute - verified
against the live API's `X-RateLimit-Limit: 30` - and every screen in this app spends
from that one purse. Turning Chat view off re-reads the whole thread instead, since a
control that only worked in one of the two modes is a control that can only fail.

**A list item's key is a promise about its identity, and three rows broke it.** The
failure banner above a thread used to be keyed by *which* failure it was
(`window_error`, `refresh_error`, `load_error`), so a background check failing over an
Older page that had already failed re-keyed one row: Compose destroyed it and inserted
another, which ran the banner's fade *and* the placement spring of every reply below
it - the whole thread sliding because one sentence changed. It is one stable key now,
with the message and the retry chosen inside the item. The Older control and a
message's head row had the opposite problem: they were the two rows in the list with
no placement animation at all, so they popped into existence above rows that did
animate. And the shimmer over the GIF picker's waiting grid read its animated value
while *composing* colours, which recomposes the grid every frame - the failure
`ShimmerBox` exists to prevent, and the reason the picker draws one now.

The header's loader is gated the way every other loop in this app is. Material's
morphing indicator is an indefinite animation, and the header stays composed under the
manage dialog and the likers tray, so it ticked for a sheet nobody could see and
ignored the one setting the reader has about motion; `INSTANT` and a stopped lifecycle
now get a word instead of a loop.

**Attachments go to catbox.moe, and to pomf when catbox refuses them.** AniList has
nowhere to put a file, so "attach" means "hand it to a host and paste the address".
Catbox takes an anonymous upload - no account, no key, no OAuth - and serves the file
unchanged at `files.catbox.moe`, which is the only reason this can be offered without
asking anybody to register for a credential. The price is retention: anonymous files
are kept until two years of no views, so an embed on a comment nobody reads can
disappear. Settings says so, because a dead image six months later should be a thing
the reader chose.

In November 2026 that stopped being enough: catbox's API answers every anonymous
upload with HTTP 412 and "Uploads paused until I can resolve storage issues", so the
button could not attach anything at all. [ImageUploader](app/src/main/java/com/anisequel/data/network/ImageUploader.kt)
tries catbox first, then **imgbb.com**, then **pomf.lain.la**, and takes the first
host that stores the file. pomf is an anonymous clone that keeps the file permanently
(`pomf.lain.la` uploads, `pomf2.lain.la` serves), and imgbb is the site's own
anonymous form - a token scraped from its homepage and one POST to `imgbb.com/json` -
rather than its documented API, because API v1 requires a key and a key in a public
repository is a credential shared by every install. The alternatives were tried rather
than assumed: catbox's own Litterbox answers 403, `0x0.st` has disabled uploads, and
uguu.se and tmpfiles.org delete after three hours and one hour - an hour-long URL in a
comment is a broken image by the time anybody reads it.

**The order of that chain is a measurement, not a preference.** pomf *stores* the file
and then refuses to show it to AniList: a GET of a stored file carrying
`Referer: https://anilist.co/` is answered by nginx with an HTTP/2 stream reset (curl
exit 92), while the same file with no referer at all returns 200. This app sends no
referer, so the photo looked fine here; every browser on the site where the comment was
posted showed a broken image, which is what "my uploaded image doesn't work on Firefox"
report actually was. `i.ibb.co` answers 200 to the same referer, so imgbb now goes
before pomf, and the outcome carries `showsOnAniList` so the composer can say plainly
when a file landed on a host the site cannot read. `ImageUploaderChainTest` pins both
halves: the order, and which host declares itself invisible. The fallback stays a
chain, not a setting: the reader is choosing a photo, not infrastructure, and a failed
host is the app's problem to route around. Each host's success test is on its own
parsed file domain, because a refusal also arrives with a body that is prose or a CDN
page and that page must never become the embed; the chains are pinned in
`CatboxUploadProtocolTest`, `PomfUploadProtocolTest` and `ImgbbUploadProtocolTest`.
[CatboxApi](app/src/main/java/com/anisequel/data/network/CatboxApi.kt) is its own
`OkHttpClient` with no interceptors - the shared one attaches the reader's bearer
token, and a file host has no business receiving it. The host answers a refusal with
HTTP 200 and prose, so "is the body a `files.catbox.moe` URL" is the whole success
test, pinned in `CatboxUploadProtocolTest`.

The upload is `enqueue`d rather than `execute`d, and the call is cancelled from the
coroutine. A blocking call cannot be interrupted, so closing the sheet mid-transfer
kept the request, the file's bytes and a thread alive until the 150-second call
timeout - and the `mapCatching` that wrapped it caught the cancellation as a
failure, wrote a reason nobody asked for and left `isUploading` true. The `finally`
clears the flag whatever ends it.

**A message thread is a comment thread.** A direct message is a `MessageActivity`,
so its body is the activity and the rows under it are the replies AniList gives any
activity. The messages tab used to draw the body in a second sheet - conversational
bubbles, one reply box at the bottom of a scroll, no way to answer one line - and
the comment sheet drew the replies without the message. Two sheets, one exchange,
and the reader could quote neither. The comment sheet now takes the tapped message
as `CommentsState.head` (mapped by `MessageNode.asThreadHead`) and draws it with
the same row as every reply, so the message itself can be replied to and quoted;
the inbox row and the friend card both route there. The old sheet survives only for
a conversation with no message yet, where there is no activity to open - that is the
one place a first `SaveMessage` can be written. The head row draws no heart:
a message's like is an activity like, and this store only speaks the reply mutation.

GIF search answers from two places: **Safebooru**, anonymous, asked for
`animated_gif` at `rating:safe`, and **Tenor v2** when the reader has pasted a
key. Both were chosen by elimination against the live services rather than against
documentation: Tenor's public key-free API is gone (`g.tenor.com/v1` returns HTTP 403
with `{"code":7,"error":"Tenor API is discontinued"}` and `tenor.googleapis.com/v2`
rejects the same string as `API_KEY_INVALID`), Giphy's historically published beta key
answers HTTP 403 `"msg":"BANNED"` - the *key* is blacklisted, not the client, since a
curl, a browser and an OkHttp user agent are refused identically - and a Giphy key
now needs a signup, starts as a beta key capped at 100 calls an hour, and its
`media.giphy.com/api/v5` surface is OAuth-issued. DuckDuckGo's image endpoint replies
with a bot challenge, Wikimedia Commons has MIME search disabled at the API ("Miser
Mode" - a `filetype:gif` search reports `totalhits: 0`), **Openverse - which does
answer anonymously and was the default for one CI cycle - indexes Creative Commons
media, so `anime reaction` returns zero tiles**, Gelbooru's DAPI answers 401 without
an account-issued key pair, Rule34 answers a CAPTCHA, and **Danbooru, which answers
200 and hotlinks fine, caps an anonymous search at two tags** - not enough to ask for
a safe animated series GIF, which is four. What is left that is anonymous *and* anime
is Safebooru, which is also what the browser GIF pickers in this community already
use.

Two rules come with that choice. `rating:safe` goes into the query **and** a post
whose own rating field is not safe is dropped on the way out: a booru is user-tagged,
a filter argument is a request rather than a guarantee, and this picker puts an image
into a comment that other people read. (The spelling is load-bearing: this asked for
`rating:general`, which the site does not know, so every search answered an empty
page.) And a phrase is rewritten into tags, because a booru matches tags, not
sentences.

**The rewrite is three requests, tried in order, not one.** `spice and wolf` ANDs
`spice` with `wolf`, and no post carries both - the series is the single tag
`spice_and_wolf`. So a search is asked first as its words, then as the whole phrase
with the stop words kept and joined by underscores, then as its **first word with a
`*` on the end**. The third is the one that finds a show whose catalogue name is not
the name anybody types: `kaguya_sama` and `kaguya sama` both answer nothing, while
`kaguya*` answers `kaguya_wa_kokurasetai` posts, measured live. A single word gets no
prefix tier - `holo*` is a wider and worse question than `holo`, and the first tier
already *is* `holo`. Only a page that found nothing *and* has nothing left falls
through: a full server page that this app's rating filter emptied is a search that is
working, and handing it to the next spelling would abandon it over a patch of
filtered posts. The tier a reader is paging inside is carried in the cursor's prefix,
because the providers do not page the same way and the spellings must not be mixed
mid-scroll.

The picker says which index it is answering (`Search Safebooru for a GIF`), because a
booru's miss and a keyed index's miss deserve different sentences, and naming it is
also the only visible proof that a Tenor key in Settings took effect. An empty search
offers two labelled rows rather than a cursor - reactions, for the thing you want to
say, and series, for the show you have in mind - and every chip in both rows was
checked against the live index with this app's own request shape before it went in. A
chip that searches into an empty grid reads as a broken picker rather than as a gap in
the catalogue.

A key is never committed: `KeystoreIntegrityTest` is not the reason so much as the
same reasoning - a bundled credential is a shared rate limit and a public secret.

Storing that key is why `KeystoreTokenCipher` now takes its alias. A decrypt failure
deletes the keystore entry, deliberately, because an invalidated key is worse than no
key - and a reader's unreadable GIF key must not be able to destroy the entry their
AniList token is sealed under, which would sign them out of the account to fix a
setting.

## A centred image, and why the parser could not see one

`~~~img100%(https://…/card.svg)~~~` is what AniList's own editor writes for a
centred banner, and the API answers the same bio as
`<center><img width='100%' src='…'></center>`. The app printed the markup as a
centred sentence. Every whole-line image form in
[AniListMarkdownParser](app/src/main/java/com/anisequel/ui/components/markdown/AniListMarkdownParser.kt)
is matched with `matchEntire` on the **source line**, and inside a single-line
`~~~…~~~` span the source line is the whole span including the tildes - so the image
code could never be the whole line, and the centred branch went straight to the
paragraph builder. `centredSingleImage` re-parses the inner text as a block and keeps
the result only when it is exactly one `ImageBlock`, which leaves centred prose and
centred AniList media cards on their old path. The width suffix travels with the
node, so `img100%` is a banner and `img220` stays a strip.

The second half was not a parser bug at all: an SVG could not be *decoded*.
Android's `ImageDecoder` - the only decoder this app registered, and the one that
runs at `minSdk = 31` - reads PNG, JPEG, GIF, WebP and HEIF. A bio card drawn live by
a server arrives as `image/svg+xml` and failed silently, so the image area was an
error placeholder over a picture the site itself shows. `coil-svg` registers
`SvgDecoder` beside it. **AndroidSVG is a partial implementation of a large
specification**: a card that nests `data:` images, `@font-face` fonts or animated
WebP inside itself may draw incompletely, which is a limit of the format on Android
rather than something this registration can fix.

**Every image is a link on AniList, and every image is now tappable here.** The
renderer only installed `clickable` when the markup wrapped the image in a link
(`[![cover](img)](target)`), so a plain `img(url)` or `![alt](url)` - the form an
ordinary screenshot or GIF uses - had no press feedback and opened nothing. The
author's target wins when there is one, and the image's own URL is the fallback
(`imageTapUrl`): the parser has already made that address absolute, and it is the
same URL Coil draws, so the tap opens the picture the reader is looking at.

**A bio's images keep their own sizes, and formatting cannot hide one.** A profile
bio is the one AniList field that is mostly pictures - a full-bleed banner, a GIF
between two headings, a spoilered reveal - and the app was the worst place to read
them, in three ways that produced no error. The API returns a *sized* image as HTML
(`<img width='100%' src='…'>`), and the parser read only `src` and `alt`: every
sized image fell through to the renderer's size-less default, so a bio built from a
100% banner and two 220px strips drew three full-column slabs. The width is captured
now and carried in the same spelling as `img220(...)` uses, so one pair of clamps
sizes both kinds - and `?width=` inside a URL is not the image's width, which is why
the attribute pattern requires preceding whitespace. An image *inside* formatting was
worse: `**![banner](x.gif)**` is what a bold heading line of a bio produces, and the
question "does this paragraph hold a bitmap" only looked at top-level tokens and at
links, so it answered no, the paragraph took its clamped-`Text` branch, and the
reader saw the alt text as a link. The search is recursive now, over every run that
can carry children, and opening a run costs only its own styling when it really does
wrap a bitmap. Third, and the one that deleted content: the about card refuses to
collapse a bio carrying an image, but it asked that question of the *top level* only -
so a banner inside a centred `~~~` span, a `~!…!~` spoiler or a quoted comment (each
of which holds its content in a child list) left the bio looking image-free and
collapsed it with the picture below the clip. The check walks the document the way the
renderer does, and `previewKeysIn` - which decides which posters to fetch - walks the
same three nesting sites, because a collector and a renderer that disagree about where
content hides produce a card with no artwork, not an error.

**The composer's size commands are for every attachment, not only for GIFs.** The GIF
pane has always written the width the reader chose - `img220(url)`, `img50%(url)` -
and centred or spoiler framing with it, because a full-size animation in a comment is
the one embed that shoves a 360dp thread around. A photo picked from the device went
through the same `img()` markup and got neither choice: it was always the site's full
width, always unframed, while the composer had offered the controls for the other
file type all along. A screenshot of a title card is precisely the image a reader
wants small and behind a lid. [AttachmentEmbedRows](app/src/main/java/com/anisequel/ui/screens/profile/AttachmentEmbedRows.kt)
is now one control used by both paths, and the choice is held per draft: a size picked
for a new comment must not decide the size of a photo inserted into the edit open
behind it. `""` is the spelling of "Original" throughout, which is also what the chip
row compares against - comparing the label instead of the value left every chip in the
row looking unchosen after you picked Original.

## The window colour, seen only in a fast tap

Every page arrives scaled to 94% while fading up, and the page it covers fades out
at 98%. For a few frames neither is full-size and neither is solid, so what shows
through is the `Box` behind the `NavHost` - which had no background, and whose
fallback is the platform window, painted black. A double tap on the Settings icon
was therefore a black screen between two correctly themed screens. The fix is one
`Modifier.background(MaterialTheme.colorScheme.background)` on that container: the
cross-fade is the app's motion language and stays, and what was missing was a
surface behind it.

## A comment row is one line, and what gives way when it is not

The controls under a comment were a `FlowRow`, which is the right answer to "five
targets under a body of any width" and the wrong one to "where is the button that
edits my comment". A wide top-level row put the overflow beside Reply and Quote; a
threaded row ran out of line and dropped it under the timestamp. The control that
changes your own comment was therefore in a different place depending on how deep
somebody else's reply was - and the rows that needed it most were the narrow ones.

The bar is one `Row` now, and the order of what gives way is a rule with numbers in
it: the words "Reply" and "Quote" go first, because their glyphs stay inside the same
48dp targets, and only a row too narrow even for that loses a continuation's
timestamp - the one thing on the line that the header above it has already said,
because a continuation is somebody speaking again within minutes. A *control* is
never the thing that goes. `commentActionBarForm` is pure and `CommentActionBarTest`
holds the arithmetic, because there is no device here to measure a phone against.

The same row also carries a line down the gutter its avatar would have used, in one of
three colours: primary for the comment the open editor is answering, faded primary for
the viewer's own, surface grey for everybody else. One element with three meanings is
deliberate. A thread is read by following lines, and a reader who can see which line is
theirs does not have to hold their own name in working memory across a hundred
comments - which is what finding your four comments in somebody else's thread actually
requires today.

The composer's size and framing chips follow the same principle: they describe a file,
so they appear with a file. The rule is the URL the upload wrote, tested against the
draft's own text rather than a `hasPhoto` flag, because the reader can delete the
`img220(...)` the upload inserted and the chips would then be describing a file that is
no longer in the message. Posting clears the field, and clears them, without the
editor having to remember to reset anything.

The same reason makes the chips *rewrite* the embed: `embedSpanFor` finds it by its URL
and `embedMarkdown` writes the new one over the whole span, wrapper included, because a
row that only sets the style for the next upload is a row that does nothing while you
look at it. The two share their wrapper spellings - `~!`/`!~` and the fence pair - so
the row cannot fail to recognise what the upload wrote.

## A seasonal sky is one clock and one seeded list

The overlay above every page - sakura, clover, fireflies, dandelion, maple, oak,
ginkgo, snow and starlight, grouped by the season that owns each one - is
deliberately the cheapest thing that can look alive: no per-particle animation objects,
no `Animatable` per flake, no composition state per frame. `seasonalParticles` builds
one immutable list from a fixed seed, and `SeasonalParticleLayer` computes every
position from a single `Float` of elapsed seconds. That single value is read only inside
the `Canvas` draw lambda, so sixty frames cost sixty repaints of one layer and zero
recompositions of the page behind it. Adding an animation driver per particle would have
made the layer the most expensive thing on the screen it decorates.

The seed is fixed rather than re-rolled, for the same reason the dashboard memoises its
computed rows: the overlay is mounted in the app root, so a list rebuilt on a
recomposition would visibly re-roll the entire sky whenever anything anywhere in the app
published a new state. Because the same random stream is consumed in order, raising the
density *appends* flakes to the end of the list - the ones already falling keep their
positions. That property is pinned by a test; without it the density slider would read as
a "re-shuffle" button.

Two gates stop it. `rememberIsResumed()` keeps the loop from ticking behind another app,
and `MotionStyle.INSTANT` freezes the clock where it is - a still scattering of shapes,
not an animation that ignores the one setting that says "stop animating". The loop is
throttled to sixty updates a second so a 120Hz display does not draw the sky twice as
fast, and a stalled frame is capped so returning from a pause does not teleport every
particle across the screen at once.

The layer itself still has no pointer input. It is drawn above every page, so one
`clickable` or `pointerInput` in `SeasonalParticleLayer.kt` or
`SeasonalParticleHost.kt` would eat the first tap everywhere in the app, and the
symptom would be blamed on whichever screen the tap landed on.
`SeasonalParticleHostTest` fails if either file grows one. Since 1.8.1 the sky does
react to taps and scrolls, so those observers live in `SeasonalAmbient.kt` and are
installed on the app root by `rememberSeasonalAmbientModifier`. A *parent* pointer
observer does not compete with its children: it reads every event in the Initial
pass and never calls `consume()`, so buttons, chips and lists still get their first
tap. The scroll side is a `NestedScrollConnection` that observes `onPostScroll` and
returns `Offset.Zero` - an observer that returned the delta would shorten every
list in the app. The same test pins both properties. Navigation is a third input
with no observer of its own: `MainBarHost` stirs the same wind when the route
changes, so the sky notices the UI moving even without a finger.

## Snow does not simulate flakes; it remembers where they crossed the edge

A settling drift is the one part of the sky with memory, and the obvious design -
a particle object per flake with a landing position and collisions - would have
made the decoration heavier than the pages it covers. `SeasonalPile` instead keeps
one normalised column of heights. When the clock wraps a falling particle's cycle,
the layer deposits a small kernel of mass at that particle's lateral position. The
pile is therefore made of exactly the particles the reader watched go past, without
a second simulation that could disagree with the drawing. The field is normalised,
so the depth slider is a multiplier on the drawn height and never resets what has
already accumulated.

Two details are deliberate. Columns are interpolated in `heightAt`, because drawing
48 nearest-column samples looks like a bar chart rather than a bank of snow. And
landings spread across five columns rather than one, because a spike-per-landing
pile is a comb. The drift's baseline is the screen's bottom edge: it gathers on
the floor and passes behind the floating bar. It used to be lifted to the bar's
measured top edge, which left a gap the height of the bar's own padding and made a
drift that should look like it gathered on the floor look like a shelf.

The bar now publishes its actual capsule bounds from its own layout - not a height
- so a second overlay can draw snow along the surface the bar really has. The
bridge is `SeasonalChrome`, and its values are snapshot state rather than the old
plain volatile field: the cap has to invalidate when the sky is switched off or
its shape changes, and at that point the layer that owned the frame loop is gone.
Both values are still read only in draw lambdas, so the bar's layout never
recomposes the page behind it.

The drift also has to stop growing. A deposit adds less the fuller a column already
is, and every landing frame compacts the field by a small fraction, so the two
find a stable depth instead of reaching the cap and turning into a flat white
band. Relaxation still runs on the low tier with no diffusion, because compaction
is the half that keeps the field below its ceiling. Glyphs on the resting drift are
drawn at a wider stride than the 1.8.1 version: a full leaf path every two columns
was a carpet rather than a drift, and it was the most expensive thing in the pile's
draw.

Colours are fixed per shape and per variant rather than taken from the theme. A
snowflake tinted `onSurface` is black on the light theme and white on the dark one,
which reads as debris rather than snow, and a seasonal effect that follows the
wallpaper is no longer the season the reader chose. The 1.8.0 palette measured below
3:1 against a white surface for every shape but maple - and each particle is drawn
at 0.55 to 1.0 alpha, which takes them lower still. The 1.8.1 palettes keep that
trade rather than claim it was solved: a flake dark enough for 3:1 on white is a grey
smudge on a true-black page, and this layer carries no information, so it is allowed
to be quieter on light than on dark. The numbers are kept as history, not as a
current measurement; re-derive them before changing a palette and claiming contrast.

The shape chips in Settings draw their glyphs with the same `drawSeasonalParticle`
the overlay uses, so a chip cannot promise a snowflake the sky does not draw. Each
shape also carries its variant count and palettes there, which is why a sky mixes
three snowflakes instead of stamping one sixty-four times.

## 1.8.2: the sky is matter, and it all falls one way

Ten shapes, four seasons, one direction. Fireflies and starlight were removed, and
with them `ParticleDirection`: they were the only two shapes that rose, the only two
drawn from light rather than an outline, and the only two readers could not name at
fourteen dp - a firefly is a soft dot with two slivers, a sparkle is a dot with four.
Encoding a second travel direction, a second speed band and a `twinkle` sine that only
ever drove those two, all to keep two glow shapes in a list of petals and leaves, was
the more expensive option. A removed shape is still read back as snow rather than as a
blank sky (`SeasonalEffect.fromStorage`), and `SeasonalParticleHostTest` pins that all
three deleted entries stay deleted - in the domain *and* in the drawing files, because
an unreachable `drawFirefly` is dead ink that the next reader will assume is load-bearing.

Everything that is flat enough to catch air now turns over as it falls. The layer
computes a signed squeeze per particle and the shape dispatcher applies it as a
horizontal scale *inside the rotated frame* - which is the whole point: a leaf turning
edge-on narrows along its own stem, not along the side of the screen. It is a matrix
rather than a re-parameterised path because this runs for every particle on every frame,
and it never reaches zero (`MIN_FACE_RATIO`): a particle that disappears for a frame
reads as a dropped frame, not as an edge-on leaf. How far a shape rolls is
`SeasonalEffect.tumbleResponse`, a property of the shape next to `windResponse` and
`swayResponse` for the same reason as always - a `when` branch in the draw loop is a
branch a new shape can forget, and forgetting it takes another shape's motion silently.

The autumn leaves stopped being polygons. Maple, oak and ginkgo are now drawn from
width functions whose samples are walked as quadratics anchored at the midpoints between
them, which makes the tangent continuous wherever the reader can see it; the oak's twelve
hardcoded curves (which is why every oak in the sky was the same oak) are one loop over an
index instead. Nothing in those builders allocates per particle - no local captured
helpers, no fresh path per shape - because they run inside the same loop as the placement.

Two rules that look like regressions and are not. **Settling and wind default to off**, and
`SeasonalPreferences` reads an absent key as false: a drift along the bottom of every page
and a sky that leans on every scroll change how the app itself feels, so an install that
predated them must not wake up with both switched on. **Quality is a setting**, resolved
once per process (`SeasonalDevice` caches the `ActivityManager` read - both the host and
Settings were making the binder call). `Auto` classifies from RAM and core count and lands
on mid-range when the reading comes back empty, because guessing "this phone is weak" from a
stub degrades a flagship invisibly. The tier changes how many particles are generated, how
often the clock steps, whether a flake gets its branches, and whether the drift gets smoothed.

The last one is a bug fix worth keeping straight: the pile's smoothing pass used to run on
*every* frame, which is a diffusion stepped at sixty hertz - within a minute it flattened the
whole drift into one band and spent a full pass plus a fresh `FloatArray` doing it. It now
runs only on a frame in which something actually landed, into a scratch buffer the pile owns.
`settlePile` returns the landing count for exactly that decision.

The bar now carries the weather too. `SeasonalBarTrail` draws a cap along the
capsule's real bounds, over the bar, from the same `SeasonalPile` the drift is drawn
from - so the thickest snow on the bar is the deepest part of the heap below it. It
takes no pointer input, runs on the sky's existing clock, and falls back to a thin
dusting when Settling is off so a snow sky does not leave the pill perfectly clean.
The layering matters: the drift is a sibling of the bar and paints behind it, while
the cap paints after it. The two are one composition contract, pinned by
`SeasonalParticleHostTest`; the old height-only bridge could not tell where the pill
actually ended, which is how the drift ended up hovering in the bar's padding.

The falling field is no longer flat. Size, fall speed and opacity are rolled together
(`seasonalParticles`), so a large flake is near, fast and bright while a small one
hangs back and fades; each particle's gust response varies with its flutter; and the
far half of a turn is dimmed as its underside comes round. The vertical path is eased
within each cycle, and every harmonic is evaluated on an angle folded into one turn,
so a long uptime cannot erode the wave into a fraction of a degree. The draw reuses
one `Path` and one placement object for the whole frame rather than allocating them
per flake.

Wind had two bugs of the same family. Gesture gusts were the only wind, so switching
Wind & touch on with a still page open did nothing at all - which reads as a broken switch,
not as an unfinished feature; there is now a slow ambient breeze underneath the gestures. And
a drag was counted twice, once by the pointer observer and once by the nested-scroll
connection that sees the same delta, so every list storm. The finger owns the drag and the
connection owns the fling after lift (`SeasonalWind.fingerDown/fingerUp`) because that is the
only split that keeps both the pages that participate in nested scroll and the ones that do
not.

## 1.8.2: spoilers are a nesting problem, and GIF search is a provider problem

AniList writes spoilers as HTML: `<span class="markdown-spoiler" data-label="Episode 4">`
inside a sentence and `<div class="markdown-spoiler">` around a block, with the older
`<div rel="spoiler">` still in old posts. The parser now reads all three. The hard half is
pairing, not recognising: `matchingCloseTag` counts same-named opens by tag depth, so an
arbitrary `<div>` inside a spoiler closes before the spoiler does and two spoilers on one page
each get their own lid. A `<span>` whose payload contains a newline is a block, not an inline
spoiler; the block walk claims it before the paragraph path runs, so the two cannot disagree
about ownership. Depth is capped at three (`MAX_SPOILER_DEPTH`); a fourth opening is left as
the author typed it because a control inside a control inside a control inside a control has
nowhere to put its tap target, while the text is still readable.

The label is metadata, not markup: it is trimmed, an empty one disappears, and one containing
braces, angle brackets or a line break is dropped whole rather than sanitised. What is behind
the lid is parsed as a full Markdown document, which is why `RenderSpoilerBlock` now re-enters
`AniListMarkdownView` with the host's cover and preview maps instead of drawing a paragraph.
The inline form parses its payload too, but keeps its children out of the link annotation:
two `LinkAnnotation`s over one run is the shape Compose resolves arbitrarily, so a nested
inline spoiler is not independently tappable - the block form is.

GIF search changed from "Tenor or the booru" to a provider choice. Tenor's public API was
retired in June 2026; keeping the saved key is deliberate (losing a credential silently is
worse than a search that says why it failed), but it no longer decides which index is used.
`CommentMediaSettings.gifProvider` records the choice, each key lives under its own encrypted
DataStore entry, and a keyed provider with no key falls back to Safebooru at read time while
keeping the choice. Klipy and GIPHY are the keyed replacements, and nekos.best is a second
keyless reaction index. The empty-grid sentence moved onto the provider (`GifSearchApi.emptyHint`)
because "boorus match tags" is true of Safebooru and false of nekos.best, and the picker used to
write the booru's advice for every narrow index.

The loading shimmer was allocating a new `LinearGradient` per skeleton per frame. Its sweep is
a three-stop band with the same colour at both ends, so the frames outside the band are exactly
`surfaceContainerHigh`; drawing that colour as a solid base and translating one cached band over
it is the same picture with one shader instead of sixty a second. `drawWithCache` keys the band
on the box size, and the phase is still read in the draw phase.


## 1.9: the inbox is two queries, and the sky has a frame budget

### The received half of the inbox was never asking for its own type

`Page.activities` has no recipient filter, so a message the reader *received* can
only be listed through `Page.notifications(type_in: [ACTIVITY_MESSAGE])`, and that
field answers `NotificationUnion` - not a concrete type. A union has to be told
which member each row is, and AniList's own documentation says so: `__typename` is
required. The document did not select it, while
`MessageNotificationNode` kept reading `@Json("__typename")` and `isUnrecognised`
kept filtering on it. Three consequences, all silent:

- the guard that decides "this is not a message notification" could never fire,
  because the field it checked was always absent;
- the fragment had no discriminator for the union, which is the case AniList warns
  about rather than the case it handles;
- every unit test stayed green, because the hand-written fixture supplied the
  `__typename` the query never asked the server for. That is the exact shape of
  test the repository rules warn about: it pinned a payload, not a requirement.

The sent half kept working the whole time, which is what made this read as "one tab
is broken". `Page.activities` answers `ActivityUnion` and the server resolves those
from the row's own class - measured against the live endpoint with a public query and
no token at all. So one union resolved and the other did not, and only the half that
needed authentication *and* the missing field was affected.

The fix is one line in the document, plus the assertion in `FriendsAndMessagesTest`
that fails without it: `__typename` must appear inside the notifications selection
set, before the fragment.

### A frame rate is a separate choice from a quality tier

`SeasonalQualityTier` has carried a `frameHz` since 1.8.2 (60/45/30), and it was the
only answer: the reader could say how much the sky draws but not how often it moves.
Both are now choices, and they are independent for two reasons a reader actually
meets. A 120Hz phone may want a full sky stepping at thirty because frame *time* is
what competes with scrolling. A 60Hz phone cannot be given 120 by any tier, however
high-end its chipset.

`SeasonalFrameRate` (Auto / 30 / 60 / 120 / 144) resolves through
`seasonalFrameHz(rate, tierHz, deviceHz)`: what was asked, clamped to the tier's
range, then clamped to the panel. Rounding rather than truncating is the load-bearing
part - Android reports 59.94 and 119.81 for the names people use, and pacing a 120
budget against 119.81 skips a step every second and a half. A device that reports
nothing (a context with no display, a stub, a fold mid-transition) is read as 60
rather than as "no ceiling", because the failure being guarded is the overlay asking
a slow panel for frames it will never hand over.

Above sixty there is a second half to the problem, which is why the host and not the
layer deals with it: the system buckets a window into a refresh-rate *category* and
leaves ordinary content in the default one, so Compose can pace its frame loop at 144
and still be handed sixty frames a second. The host asks the composited view for the
max category while the sky is on and above sixty, and puts it back otherwise - the
battery cost of a high-rate window belongs to the state that needs it.

The Settings page offers only the rates the screen reports (`availableSeasonalFrameRates`),
because a 120 Hz pill on a 60 Hz phone is a control with exactly one effect: none. The
list is re-read when the app resumes, which is what makes the pill animate in when the
reader goes and unlocks the rate in the system settings - so unlike the quality tier,
this reading is deliberately not cached for the process. A choice that has *become*
unavailable stays drawn and still selected: the system setting can come back, and a
selected option that has gone silent reads as the app forgetting the choice.

### The chip is a legend, not a frame of the sky

The Settings shape chips draw with `drawSeasonalParticle`, and that stays: the
day a chip is illustrated separately is the day it can advertise a shape the sky
does not fall. What was wrong was the *pose*. Every chip asked for `variant` zero
at a radius of `0.32` of its own box, which had two consequences. For the four
shapes that share the snow dispatcher, variant zero is one drawing shown four
times, so the winter row could only be read from the words beside it. And 0.32 of
an 18dp chip sits about two pixels above the point where each shape drops its
branches, veins and barbs (`radius > 5f`), so the same saved setting drew a fern
on one phone and six bare spokes on another.

`seasonalGlyphLegend` is a pose table: a radius fitted to each shape's farthest
ink (a petiole at 1.2 radii, a seed's halo at 1.3), the variant that *names* the
shape at chip size, a tilt small enough to stay a posture rather than the sky's
tumble, and a hairline floor for ink that would otherwise fade under half a
pixel. The floor is a parameter the dispatcher caps at six percent of the radius
and defaults to zero, which is what the overlay and the drift pass - so a frame
of weather pays nothing for a pictogram, and the legend cannot fuse a seed's
twelve filaments into one fan.

The snow family and the glyph moved out of the dispatcher when this landed: that
file was at 699 lines under a 700 ceiling, and routing, weather and a pictogram
change for three different reasons.

### The snow stays off the bar

1.8.2 added a cap of snow on the navigation bar's own pill: a wavy strip along its
top edge, drips over its face, a sparkle, and the pill's measured bounds published
from the bar's layout into `SeasonalChrome` so a second Canvas could draw over it.
The reader asked for the weather to stay off the control they press, and the honest
way to remove a behaviour like that is to remove the whole channel with it. So
`SeasonalBarTrail`, `SeasonalChrome`, the layer's `publishesBarTrail`, the bar's
`onCapsuleBounds` parameter and the `onGloballyPositioned` that fed it are all gone;
the drift keeps gathering on the screen's real bottom edge, behind the bar, which is
what the pile was doing anyway. Deleting the publishing path also deletes a layout
callback that wrote state on every pass.

### Hiding a row is the reader's, not AniList's

The inbox can now dismiss a row and can mark what arrived since the reader last read
it. Both are preferences (`MessageVisibilityPreferences`), because AniList has no
"remove this from my inbox": a message deleted server-side stops existing for the
person who sent it, and a private message that vanishes from one side of an exchange
is a data loss, not a tidy-up. So:

- A row is hidden under the *side* it belongs to, decided by `MessageNode.sideOf`
  (the same `isSentBy` test that draws the direction line), not by the chip that was
  selected - under `All` one list holds both, and dismissing a message the reader
  wrote must not also hide the reply they received.
- The stamp is the epoch second a message was *opened*, never the second the tab was
  drawn: a badge that cleared the moment the list appeared would never be readable,
  which is the only thing it is for.
- `resetNotificationCount` is still not sent. AniList's unread count is not this
  app's to reset.
- The option switch lives on the tab rather than in Settings because it is about this
  list, and switching it off does not move the stamp: the two are separate decisions,
  and turning the badge back on should show what it showed before.

### The paged caches had a TTL and no ceiling

Every cache in the repository expires on the reader's refresh interval, but the
expiry is only consulted when the *same key* is looked up again. Nothing ever asked
for page 42 of a feed once it had scrolled away, so nothing released it: the maps
grew for the whole session, and the cost arrived as a hitch in the middle of a fling.
`MAX_PAGED_CACHE_ENTRIES` plus `expiredThenOldestKeys` bounds them - expired first
(an answer outside the TTL is a wrong answer kept around), then the oldest stamps, so
what stays is what the reader is likely to scroll back into. The rule is a top-level
`internal` function for the reason `mediaCoverBatches` already is: it is the thing
that must not regress, and the maps it is applied to are private state.

### Covers: the best art each slot can actually show

`MediaCoverImage.bestUrl` stays `extraLarge` first - the hero, the backdrop and the
full-screen viewer are the pictures the reader looks at, and they should be the best
AniList serves. What changed is the 22dp relation chip on a dashboard row: it asked
for the same file as the backdrop, and AniList's `medium` is already about three times
the pixels a 66-device-pixel slot can place. `smallSlotUrl` exists for that, and the
fallback order is unchanged so a response that omits the smaller size still draws.

Two frame-cost fixes came with it. The wavy progress bar rebuilt a fresh `Path` with
96 segments *per frame per card*, which is how a smooth fling turns into a GC pause
when a screenful of cards is on screen; it rewinds one remembered path now, the way
the seasonal shapes already draw. And the lists that mix row types - the dashboard,
the inbox, the library grid, the arriving rows - name a `contentType`, so a fast
scroll recycles a slot instead of composing a new one every frame.
