package `in`.artistant.app.platform.observability

/**
 * Crash-reporting seam. No Sentry dependency yet — [SentryCrash] (bound in `AppModule`)
 * is a guarded no-op until a DSN is wired, and it runs the same PII-scrub regex as iOS
 * over every reported throwable regardless. There is no separate no-op impl: a second
 * do-nothing class would only be a second thing to keep in sync.
 */
interface Crash {
    fun record(throwable: Throwable)
    fun setUser(userId: String?)
}
