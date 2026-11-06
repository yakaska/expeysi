Copy res/ into app/src/main/res/ (merge folders).

AndroidManifest.xml:
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"

Notification small icon: R.drawable.ic_stat_notification (white on transparent).
Google Play Console: upload ic_launcher-playstore.png (512x512).
Adaptive icon (API 26+): background gradient + foreground + monochrome (themed icons, API 33+).
svg-sources/ holds the vector sources for each layer.
