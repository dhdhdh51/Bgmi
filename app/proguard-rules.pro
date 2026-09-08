# R8 / ProGuard rules for the release build.
#
# The app deliberately has no reflection-based dependencies: networking uses
# HttpURLConnection and JSON is parsed with the platform's org.json, so the
# default optimised rules are enough.
#
# Keep the parcelable/serializable-free model classes' names readable in crash
# reports without keeping their members.
-keepnames class com.bgmi.sensitivity.data.** { *; }

# Line numbers in stack traces, with the source file name hidden.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
