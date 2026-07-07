package `in`.artistant.app.domain.auth

/**
 * Composes the router-advance key from the user id + the sign-in generation (port of iOS
 * `AuthService.authAdvanceKey`). Pure so it's unit-testable and so the behaviour is explicit:
 * the key changes when EITHER the identity changes (nil→uuid, uuidA→uuidB) OR the SAME user
 * completes a fresh sign-in (uuidX→uuidX with a bumped generation). The generation component
 * is the whole point — without it a returning user re-authenticating into the same uuid would
 * leave the key unchanged and the router would never re-fire (the stuck-on-auth bug).
 */
fun authAdvanceKey(userId: String?, generation: Int): String = "${userId ?: "nil"}:$generation"
