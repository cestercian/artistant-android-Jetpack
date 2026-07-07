package `in`.artistant.app.platform.observability

/**
 * Crash-reporting seam. No Sentry dependency in M0 — no-op until a DSN is wired.
 * The real impl will run the same PII-scrub regex as iOS in a BeforeSend hook.
 */
interface Crash {
    fun record(throwable: Throwable)
    fun setUser(userId: String?)
}

/** Does nothing. Bound by ObservabilityModule until Sentry is added. */
class NoopCrash : Crash {
    override fun record(throwable: Throwable) = Unit
    override fun setUser(userId: String?) = Unit
}
