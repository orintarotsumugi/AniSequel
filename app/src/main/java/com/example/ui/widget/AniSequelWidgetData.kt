package com.example.ui.widget

import com.example.data.repository.DemoProfileProvider

/**
 * Where the widget's number comes from.
 *
 * ## Why this is separate from the provider
 *
 * The provider is about the platform: broadcasts, `RemoteViews`, the tap target.
 * This is about the app's data, and it is the only file that has to change when
 * the widget stops reporting the offline fixture and starts reporting the
 * viewer's real feed. Two responsibilities, two reasons to change - see the
 * "size and structure" rules in AGENTS.md.
 *
 * ## Why the fixture, and not the repository
 *
 * A widget is updated by the system at times the app has no say in - including
 * before it has ever been opened - and it has no session. `DashboardViewModel`
 * and the AniList client are built behind a token held by the Activity, so there
 * is nothing to read from a widget's first update.
 *
 * The demo fixture is the same one the demo dashboard renders, so what the
 * widget counts is what the app shows. It is deliberately *not* a hardcoded
 * number: a fresh install with no account would otherwise sit at a constant,
 * which is indistinguishable from a widget that never received its update.
 */
object AniSequelWidgetData {

    /** How many list entries the viewer logged recently. */
    fun recentActivityCount(): Int = DemoProfileProvider.getDemoActivity().size
}
