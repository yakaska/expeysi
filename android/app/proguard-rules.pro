# Дополнительные правила ProGuard/R8 для release-сборки.

# play-services-code-scanner 16.1.0 не содержит consumer-правил: R8 в full mode
# (по умолчанию с AGP 8) вырезает внутренние классы, и getClient падает
# (https://github.com/googlesamples/mlkit/issues/1018).
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_code_scanner.** { *; }
