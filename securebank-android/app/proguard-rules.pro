# kotlinx.serialization: mantém os serializadores gerados dos DTOs
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.securebank.mobile.**$$serializer { *; }
-keepclassmembers class com.securebank.mobile.** { *** Companion; }
-keepclasseswithmembers class com.securebank.mobile.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit lê as interfaces por reflexão
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.securebank.mobile.core.network.*Api { *; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Em release nenhuma linha de log útil a um atacante deve sobrar
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
