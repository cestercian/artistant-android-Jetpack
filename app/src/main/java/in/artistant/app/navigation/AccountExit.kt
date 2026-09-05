package `in`.artistant.app.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * What "Close Artistant" does when the account is gone but the session would not go with it.
 *
 * Both tab graphs passed `onFinished = {}` here, on the reasoning that a cleared session
 * propagates to the root gate and swaps the whole graph for the signup flow — which is true, and
 * is why [DeleteAccountViewModel.finish] only reaches this when the sign-out did NOT land. In
 * that case there is no swap coming: the delete succeeded server-side, the local wipe ran as the
 * DPDP §11 backstop, and the app is sitting in an account graph belonging to a row that no
 * longer exists, with a receipt whose only button did nothing.
 *
 * `finishAffinity` rather than a `popBackStack`: there is nowhere in this graph to pop TO. The
 * receipt already tells the user to restart the app, so closing it is the action that sentence
 * describes, and the next launch comes up with no session and lands on the signup flow.
 *
 * A context that is not (and does not wrap) an Activity is a no-op rather than a crash — the
 * receipt is already showing the restart line, which is the instruction either way.
 */
internal fun Context.finishAfterDelete() {
    findActivity()?.finishAffinity()
}

/**
 * [finishAfterDelete] bound to the composition's context — what both graphs pass as the delete
 * screen's `onFinished`.
 *
 * A helper rather than two `LocalContext.current` locals, because the exit is one decision and
 * the two graphs must not be able to drift on it: the delete screen is the one place where the
 * artist side has historically been the copy nobody updated.
 */
@Composable
internal fun rememberDeleteAccountExit(): () -> Unit {
    val context = LocalContext.current
    return remember(context) { { context.finishAfterDelete() } }
}

/** Compose hands out a wrapped context; the Activity is somewhere under it. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
