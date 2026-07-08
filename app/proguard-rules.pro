# ==============================================================================
# R8 / ProGuard keep rules — Artistant Android (M8 hardening).
#
# minify + resource-shrink are ON for release (app/build.gradle.kts). These rules
# protect the two things R8's whole-program optimizer can silently break and that
# a compile-time build CANNOT catch — they only surface as a runtime crash:
#
#   1. kotlinx.serialization generated serializers (our @Serializable data models,
#      the DataStore-persisted calendar/billing state, AND the type-safe
#      Navigation-Compose routes — a stripped route serializer crashes on the very
#      first navigation).
#   2. Reflection-touched members (annotations, generic signatures).
#
# Most of our deps (kotlinx.serialization, Supabase-kt, Ktor, Hilt, Play Billing,
# Navigation-Compose) already SHIP consumer R8 rules that R8 merges automatically,
# so this file is intentionally small — it adds the officially-documented
# serialization rules plus explicit keeps for OUR packages as belt-and-suspenders,
# not a re-statement of what the libraries already guarantee.
#
# ⚠️ A green `assembleRelease` proves these rules are structurally sound (R8's
# static pass succeeded). It does NOT prove a serializer survives at runtime — the
# release build MUST be smoke-tested on a device before Play submission (see
# docs/RELEASE.md "release smoke test").
# ==============================================================================

# --- Reflection / serialization metadata (kotlinx.serialization needs these) ---
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature,EnclosingMethod

# --- kotlinx.serialization: the official keep rules (verbatim from the docs) ----
# Keep the `Companion` object field of every @Serializable class so `.serializer()`
# is resolvable.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
# Keep `serializer()` on the companion of @Serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep `INSTANCE.serializer()` for @Serializable objects (our nav-route data objects
# and singletons rely on this).
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Explicit keeps for OUR @Serializable surfaces (insurance) -----------------
# Data models + DTOs, the DataStore state, and every type-safe nav route. Keeping
# the classes + their generated $$serializer removes any doubt about the -if rules
# above resolving our specific packages.
-keep @kotlinx.serialization.Serializable class in.artistant.app.data.model.** { *; }
-keep @kotlinx.serialization.Serializable class in.artistant.app.navigation.** { *; }
-keep @kotlinx.serialization.Serializable class in.artistant.app.platform.** { *; }
-keepclassmembers class in.artistant.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
# The generated per-class serializers (R8 renames these away otherwise).
-keep,includedescriptorclasses class in.artistant.app.**$$serializer { *; }

# --- Enums (serialized by name; keep valueOf/values from being stripped) --------
-keepclassmembers enum in.artistant.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
