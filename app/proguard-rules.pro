-dontobfuscate

-keep,allowoptimization class eu.kanade.**
-keep,allowoptimization class tachiyomi.**
-keep,allowoptimization class mihon.**

# Keep common dependencies used in extensions and dynamically loaded CloudStream plugins
-keep class androidx.preference.** { *; }
-keep class androidx.appcompat.** { *; }
-keep class androidx.fragment.** { *; }
-keep class androidx.recyclerview.** { *; }
-keep class com.google.android.material.** { *; }
-keep class android.content.** { *; }
-keep class uy.kohesive.injekt.** { *; }
-keep class android.test.base.** { *; }
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class okhttp3.** { *; }
-keepclassmembers class okhttp3.** { *; }
-keep class okio.** { *; }
-keepclassmembers class okio.** { *; }
-keep class org.jsoup.** { *; }
-keepclassmembers class org.jsoup.** { *; }
-keep class rx.** { *; }
-keep class app.cash.quickjs.** { *; }
-keep class is.xyz.mpv.** { *; }
-keep class com.arthenica.** { *; }

# From extensions-lib
-keep class eu.kanade.tachiyomi.network.interceptor.** { *; }
-keepclassmembers class eu.kanade.tachiyomi.network.interceptor.** { *; }
-keep class com.squareup.zstd.** { *; }
-keepclassmembers class com.squareup.zstd.** { *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.RateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.SpecificHostRateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.NetworkHelper { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.OkHttpExtensionsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.RequestsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.AppInfo { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.torrentutils.** { public protected *; }

##---------------Begin: proguard configuration for RxJava 1.x  ----------
-dontwarn sun.misc.**

-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
   long producerIndex;
   long consumerIndex;
}

-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}

-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}

-dontnote rx.internal.util.PlatformDependent
##---------------End: proguard configuration for RxJava 1.x  ----------

##---------------Begin: proguard configuration for okhttp  ----------
-keepclasseswithmembers class okhttp3.MultipartBody$Builder { *; }
##---------------End: proguard configuration for okhttp  ----------

##---------------Begin: proguard configuration for kotlinx.serialization  ----------
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
-keep class kotlin.reflect.** { *; }
-keepclassmembers class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }
-dontnote kotlinx.serialization.** # core serialization annotations

# kotlinx-serialization-json specific. Add this if you have java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class eu.kanade.**$$serializer { *; }
-keepclassmembers class eu.kanade.** {
    *** Companion;
}
-keepclasseswithmembers class eu.kanade.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.** {
    <methods>;
}
##---------------End: proguard configuration for kotlinx.serialization  ----------

# XmlUtil
-keep public enum nl.adaptivity.xmlutil.EventType { *; }

# CloudStream 3 Plugin Ecosystem & NiceHttp
-keep class com.lagradost.** { *; }
-keepclassmembers class com.lagradost.** { *; }
-keep interface com.lagradost.** { *; }
-keep enum com.lagradost.** { *; }
-keepclassmembers enum com.lagradost.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public int ordinal();
    public java.lang.String name();
    public *;
}
-keep class com.lagradost.cloudstream3.utils.ExtractorLinkType { *; }
-keepclassmembers class com.lagradost.cloudstream3.utils.ExtractorLinkType { *; }
-keep class com.lagradost.cloudstream3.utils.Qualities { *; }
-keepclassmembers class com.lagradost.cloudstream3.utils.Qualities { *; }
-keep @interface com.lagradost.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class com.fasterxml.jackson.** { *; }

# Keep full classes and members for dynamically loaded CloudStream plugins (.cs3)
-keep class org.jsoup.** { *; }
-keepclassmembers class org.jsoup.** { *; }
-keep class kotlin.collections.CollectionsKt** { *; }
-keep class kotlin.text.StringsKt** { *; }
-keep class kotlin.text.CharsKt** { *; }
-keep class kotlin.comparisons.ComparisonsKt** { *; }
-keep class kotlin.coroutines.intrinsics.** { *; }
-keep class kotlin.coroutines.jvm.internal.** { *; }

# org.json (used by CloudStream plugins and NiceHttp; must match Android framework)
-dontwarn org.json.**
-keep class org.json.** { *; }
-keepclassmembers class org.json.** { *; }


