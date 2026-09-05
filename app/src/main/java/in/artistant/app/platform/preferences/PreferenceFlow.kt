package `in`.artistant.app.platform.preferences

import `in`.artistant.app.platform.storage.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Read one boolean out of the generic string [KeyValueStore], with an explicit default.
 *
 * [KeyValueStore] holds strings because that is all the small persisted snapshots ever needed.
 * A boolean preference stored through it has to answer a question that `String?.toBoolean()`
 * gets wrong: `toBoolean()` returns false for null, so EVERY unset switch would read as off —
 * which is the correct answer for the two marketing toggles and the wrong one for the six that
 * describe what the app already does. So the default is the caller's, and it is the ABSENT
 * value, not a coercion:
 *
 *  - the literal `"true"` / `"false"` written by [KeyValueStore.setString] round-trip exactly;
 *  - anything else — null, a value written by an older build, a corrupted entry — falls back to
 *    [default] rather than silently reading as off.
 *
 * Same shape as `PrivacyPreferences.toBoolOrDefault`, generalised because three preference
 * classes now need it. It is a top-level extension rather than a member so a unit test can
 * exercise it against a fake store without constructing any of them.
 *
 * `distinctUntilChanged` because DataStore emits the WHOLE preferences object on every write,
 * so a switch on screen 124 re-emitted all eight of its neighbours plus both accessibility
 * flags — and `NotificationSettings.all` combines eight of these, so one tap recomposed the
 * list eight times and re-ran the push-delivery read behind it.
 */
internal fun KeyValueStore.bool(key: String, default: Boolean): Flow<Boolean> =
    getString(key).map { it.toBoolOrDefault(default) }.distinctUntilChanged()

/** @see bool — the parse, extracted so it is directly testable. */
internal fun String?.toBoolOrDefault(default: Boolean): Boolean = when (this) {
    true.toString() -> true
    false.toString() -> false
    else -> default
}
