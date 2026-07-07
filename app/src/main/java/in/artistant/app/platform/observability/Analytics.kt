package `in`.artistant.app.platform.observability

/**
 * Analytics seam. No PostHog dependency in M0 — the no-op impl runs until a key
 * is wired (the iOS "dark-until-key" gating). The interface exists so callers
 * don't `#if`-gate; the SDK-backed impl swaps in later behind the same seam.
 */
interface Analytics {
    fun capture(event: String, props: Map<String, Any?> = emptyMap())
    fun identify(userId: String)
    fun reset()
}

/** Does nothing. Bound by ObservabilityModule until PostHog is added. */
class NoopAnalytics : Analytics {
    override fun capture(event: String, props: Map<String, Any?>) = Unit
    override fun identify(userId: String) = Unit
    override fun reset() = Unit
}
