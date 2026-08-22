package com.example.command

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

class AppLauncherCommandHandler : CommandHandler {

    companion object {
        private const val TAG = "AppLauncherHandler"

        private val LAUNCH_PREFIXES = listOf("open ", "launch ", "start ", "run ", "go to ")
        private val LAUNCH_SUFFIXES = listOf(
            " kholo", " khol do", " khol", " chalu karo", " chalao", " open karo", " start karo", " chalu"
        )
    }

    private data class AppMetadata(val displayName: String, val packages: List<String>)

    private val knownAppMap: Map<String, AppMetadata> = mapOf(
        "youtube" to AppMetadata("YouTube", listOf("com.google.android.youtube")),
        "yt" to AppMetadata("YouTube", listOf("com.google.android.youtube")),

        "chrome" to AppMetadata("Chrome", listOf("com.android.chrome")),
        "google chrome" to AppMetadata("Chrome", listOf("com.android.chrome")),
        "browser" to AppMetadata("Chrome", listOf("com.android.chrome", "com.sec.android.app.sbrowser")),

        "whatsapp" to AppMetadata("WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b")),
        "whats app" to AppMetadata("WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b")),

        "gmail" to AppMetadata("Gmail", listOf("com.google.android.gm")),
        "google mail" to AppMetadata("Gmail", listOf("com.google.android.gm")),
        "email" to AppMetadata("Gmail", listOf("com.google.android.gm", "com.android.email")),

        "maps" to AppMetadata("Google Maps", listOf("com.google.android.apps.maps")),
        "google maps" to AppMetadata("Google Maps", listOf("com.google.android.apps.maps")),

        "play store" to AppMetadata("Play Store", listOf("com.android.vending")),
        "playstore" to AppMetadata("Play Store", listOf("com.android.vending")),
        "google play" to AppMetadata("Play Store", listOf("com.android.vending")),
        "store" to AppMetadata("Play Store", listOf("com.android.vending")),

        "settings" to AppMetadata("Settings", listOf("com.android.settings")),
        "setting" to AppMetadata("Settings", listOf("com.android.settings")),

        "camera" to AppMetadata("Camera", listOf("com.google.android.GoogleCamera", "com.android.camera", "com.sec.android.app.camera")),

        "calculator" to AppMetadata("Calculator", listOf("com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator")),

        "clock" to AppMetadata("Clock", listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock")),

        "photos" to AppMetadata("Photos", listOf("com.google.android.apps.photos")),
        "gallery" to AppMetadata("Gallery", listOf("com.sec.android.gallery3d", "com.miui.gallery")),

        "instagram" to AppMetadata("Instagram", listOf("com.instagram.android")),
        "insta" to AppMetadata("Instagram", listOf("com.instagram.android")),
        "spotify" to AppMetadata("Spotify", listOf("com.spotify.music")),
        "telegram" to AppMetadata("Telegram", listOf("org.telegram.messenger")),
        "facebook" to AppMetadata("Facebook", listOf("com.facebook.katana")),
        "twitter" to AppMetadata("X", listOf("com.twitter.android")),
        "x" to AppMetadata("X", listOf("com.twitter.android"))
    )

    override fun canHandle(normalizedText: String): Boolean {
        val clean = normalizedText.trim()
        if (clean.isBlank()) return false

        val hasPrefix = LAUNCH_PREFIXES.any { clean.startsWith(it) }
        val hasSuffix = LAUNCH_SUFFIXES.any { clean.endsWith(it) }
        val isDirectKnownApp = knownAppMap.containsKey(clean)

        return hasPrefix || hasSuffix || isDirectKnownApp
    }

    override fun execute(context: Context, rawCommand: String, normalizedText: String): CommandResult {
        val targetAppQuery = extractTargetApp(normalizedText)
        if (targetAppQuery.isBlank()) {
            return CommandResult.Error(rawCommand, "App is not installed")
        }

        Log.d(TAG, "Extracted app query: \"$targetAppQuery\" from raw command: \"$rawCommand\"")

        val knownMetadata = knownAppMap[targetAppQuery]
        val packageCandidates = knownMetadata?.packages ?: emptyList()

        val pm = context.packageManager

        // 1. Try candidates in known package map
        for (pkg in packageCandidates) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                try {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    val appName = knownMetadata?.displayName ?: targetAppQuery.capitalizeWords()
                    Log.d(TAG, "Successfully launched $appName ($pkg)")
                    return CommandResult.Success(rawCommand, "Opening $appName...")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start activity for $pkg", e)
                }
            }
        }

        // 2. Fallback: Query installed launcher applications dynamically
        val dynamicPackage = findInstalledAppPackageByName(context, targetAppQuery)
        if (dynamicPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(dynamicPackage)
            if (launchIntent != null) {
                try {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    val formattedName = targetAppQuery.capitalizeWords()
                    Log.d(TAG, "Successfully launched dynamic app $formattedName ($dynamicPackage)")
                    return CommandResult.Success(rawCommand, "Opening $formattedName...")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start activity for dynamic package $dynamicPackage", e)
                }
            }
        }

        // 3. App not found or launch intent unresolvable
        Log.w(TAG, "Target app \"$targetAppQuery\" is not installed or cannot be opened.")
        return CommandResult.Error(rawCommand, "App is not installed")
    }

    private fun extractTargetApp(normalizedText: String): String {
        var clean = normalizedText.trim()

        for (prefix in LAUNCH_PREFIXES) {
            if (clean.startsWith(prefix)) {
                clean = clean.substring(prefix.length).trim()
                break
            }
        }

        for (suffix in LAUNCH_SUFFIXES) {
            if (clean.endsWith(suffix)) {
                clean = clean.substring(0, clean.length - suffix.length).trim()
                break
            }
        }

        // Clean trailing filler words
        clean = clean.removeSuffix(" app")
            .removePrefix("app ")
            .removeSuffix(" please")
            .trim()

        return clean
    }

    private fun findInstalledAppPackageByName(context: Context, query: String): String? {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            for (info in resolveInfos) {
                val label = info.loadLabel(pm).toString().lowercase()
                if (label == query || label.contains(query)) {
                    return info.activityInfo.packageName
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error querying installed applications", e)
            null
        }
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
