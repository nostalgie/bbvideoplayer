# Consumer rules: keep rules for libraries that live inside :core
# (apps consuming :core get these automatically).

# ==============================
# libVLC
# ==============================
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**

# ==============================
# DataStore Preferences
# ==============================
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**
