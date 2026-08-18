package `in`.artistant.app.platform.observability

/**
 * Analytics seam. No PostHog dependency yet — the bound impl ([PostHogAnalytics]) stays a
 * guarded no-op until a key is wired (the iOS "dark-until-key" gating). The interface
 * exists so callers don't `#if`-gate; the SDK send lands inside that impl later.
 */
interface Analytics {
    fun capture(event: String, props: Map<String, Any?> = emptyMap())
    fun identify(userId: String)
    fun reset()
}

/**
 * Does nothing. Not bound anywhere — production gets [PostHogAnalytics] from `AppModule`;
 * this is the stand-in a unit test hands a ViewModel when the events aren't what it's
 * asserting on.
 */
class NoopAnalytics : Analytics {
    override fun capture(event: String, props: Map<String, Any?>) = Unit
    override fun identify(userId: String) = Unit
    override fun reset() = Unit
}
