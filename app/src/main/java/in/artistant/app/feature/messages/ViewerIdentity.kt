package `in`.artistant.app.feature.messages

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.platform.auth.SessionManager
import javax.inject.Singleton

/**
 * Who is looking at this thread.
 *
 * A thread has two sides and the UI must always name *the other one*, so every
 * messages surface needs the viewer's uid. [SessionManager] already holds it, but
 * it drags in a Context, the Supabase client, analytics and push — none of which a
 * JVM unit test can build. This one-method seam is the narrow slice the messages
 * feature actually needs, so the ViewModels stay constructible in a plain test
 * (`ViewerIdentity { "…uuid…" }`) while production still reads the live session.
 *
 * Returns null when signed out; callers must degrade rather than assume a side.
 */
fun interface ViewerIdentity {
    fun currentUserId(): String?
}

/**
 * Binds [ViewerIdentity] to the live session. Deliberately reads
 * `session.currentUserId` per call rather than capturing it once — the value
 * changes on sign-out/sign-in and this provider is a @Singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
object ViewerIdentityModule {
    @Provides
    @Singleton
    fun provideViewerIdentity(session: SessionManager): ViewerIdentity =
        ViewerIdentity { session.currentUserId }
}
