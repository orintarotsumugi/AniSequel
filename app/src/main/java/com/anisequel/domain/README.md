# `domain/` — pure logic, no Android

Anything that can be decided without a screen, a network call or a Context lives here,
and is therefore testable on a plain JVM with no Robolectric shadow.

## Where things go

| Path | Holds |
|---|---|
| `domain/usecase/` | The rules about media: which sequels were missed, what is arriving now, what the activity calendar says. One use case per decision. |
| `domain/tier/` | The tier list model, its codec, and the SVG it renders to. |
| `domain/studio/` | Pure derivations over a studio's titles — currently the year shelves. |
| `domain/motion/` | Wave geometry for the progress bar. |

## Rules

- **No Android imports. Ever.** No `Context`, no `Compose`, no `DataStore`, no
  `android.graphics`. If a file in here needs one, the decision being made is partly a
  presentation decision, and it belongs in `ui/`.
- **A use case takes data in and returns data out.** No repository handle, no
  coroutine dispatching of its own, no reading from disk. The ViewModel or repository
  owns that.
- **This is the cheapest layer to test.** `FindMissedSequelsUseCase` and
  `StudioYearShelf` are both plain functions over data classes, and their tests are the
  fastest in the repo. If a rule is awkward to test, it is probably in the wrong layer.

## Moving something here

The test is: could this run on a machine with no Android SDK? If not, it is not a use
case yet. When code grows too big to hold invariants in one class — `DashboardViewModel`
is the standing example — the fix is usually to extract a *value type* here (a filters
object, a shelf list) rather than to extract more `internal` fields and make the
invariants harder to hold. See `TO-DO.md` §1.2.