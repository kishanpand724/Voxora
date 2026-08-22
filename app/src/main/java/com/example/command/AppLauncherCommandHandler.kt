package com.example.command

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log

class AppLauncherCommandHandler : CommandHandler {

    companion object {
        private const val TAG = "AppLauncherHandler"

        private val LAUNCH_PREFIXES = listOf("open ", "launch ", "start ", "run ", "go to ", "open up ")
        private val LAUNCH_SUFFIXES = listOf(
            " kholo", " khol do", " khol", " chalu karo", " chalao", " open karo", " start karo", " chalu"
        )
    }

    private data class AppMetadata(
        val displayName: String,
        val aliases: List<String>,
        val knownPackages: List<String>
    )

    private val knownApps: List<AppMetadata> = listOf(
        AppMetadata(
            displayName = "YouTube",
            aliases = listOf("youtube", "yt", "you tube"),
            knownPackages = listOf("com.google.android.youtube", "com.google.android.youtube.tv")
        ),
        AppMetadata(
            displayName = "Chrome",
            aliases = listOf("chrome", "google chrome", "browser"),
            knownPackages = listOf("com.android.chrome", "com.google.android.apps.chrome")
        ),
        AppMetadata(
            displayName = "WhatsApp",
            aliases = listOf("whatsapp", "whats app", "whatsapp business"),
            knownPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
        ),
        AppMetadata(
            displayName = "Gmail",
            aliases = listOf("gmail", "google mail", "email"),
            knownPackages = listOf("com.google.android.gm")
        ),
        AppMetadata(
            displayName = "Google Maps",
            aliases = listOf("maps", "google maps"),
            knownPackages = listOf("com.google.android.apps.maps")
        ),
        AppMetadata(
            displayName = "Play Store",
            aliases = listOf("play store", "playstore", "google play", "store"),
            knownPackages = listOf("com.android.vending")
        ),
        AppMetadata(
            displayName = "Settings",
            aliases = listOf("settings", "setting"),
            knownPackages = listOf("com.android.settings")
        ),
        AppMetadata(
            displayName = "Camera",
            aliases = listOf("camera"),
            knownPackages = listOf("com.google.android.GoogleCamera", "com.android.camera", "com.sec.android.app.camera")
        ),
        AppMetadata(
            displayName = "Calculator",
            aliases = listOf("calculator"),
            knownPackages = listOf("com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator")
        ),
        AppMetadata(
            displayName = "Photos",
            aliases = listOf("photos", "gallery"),
            knownPackages = listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery")
        )
    )

    override fun canHandle(normalizedText: String): Boolean {
        val clean = normalizedText.trim()
        if (clean.isBlank()) return false

        val hasPrefix = LAUNCH_PREFIXES.any { clean.startsWith(it) }
        val hasSuffix = LAUNCH_SUFFIXES.any { clean.endsWith(it) }

        val isKnownAlias = knownApps.any { meta ->
            meta.aliases.any { alias -> clean == alias || clean.endsWith(" $alias") || clean.startsWith("$alias ") }
        }

        return hasPrefix || hasSuffix || isKnownAlias
    }

    override fun execute(context: Context, rawCommand: String, normalizedText: String): CommandResult {
        val targetQuery = extractTargetAppQuery(normalizedText)
        if (targetQuery.isBlank()) {
            logExecutionDetail(
                recognizedAppName = rawCommand,
                matchedPackageName = null,
                intentFound = false,
                launchResult = "App query is blank"
            )
            return CommandResult.Error(rawCommand, "App is not installed")
        }

        val metadata = findAppMetadata(targetQuery)
        val recognizedAppName = metadata?.displayName ?: targetQuery.capitalizeWords()
        val searchAliases = metadata?.aliases ?: listOf(targetQuery)
        val knownPackages = metadata?.knownPackages ?: emptyList()

        val pm = context.packageManager
        val launchableApps = getInstalledLaunchableApps(pm)

        // 1. Search by application label / name
        for (resolveInfo in launchableApps) {
            val label = resolveInfo.loadLabel(pm).toString().trim().lowercase()
            val packageName = resolveInfo.activityInfo.packageName

            val isMatch = searchAliases.any { alias ->
                label == alias || label.contains(alias) || alias.contains(label)
            }

            if (isMatch) {
                val launchIntent = pm.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    return launchAppAndReturnResult(
                        context = context,
                        rawCommand = rawCommand,
                        recognizedAppName = recognizedAppName,
                        packageName = packageName,
                        launchIntent = launchIntent,
                        matchMethod = "label search ('$label')"
                    )
                }
            }
        }

        // 2. Fallback: Search by known package name
        for (pkg in knownPackages) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                return launchAppAndReturnResult(
                    context = context,
                    rawCommand = rawCommand,
                    recognizedAppName = recognizedAppName,
                    packageName = pkg,
                    launchIntent = launchIntent,
                    matchMethod = "known package fallback"
                )
            }
        }

        // 3. Fallback: Direct package intent check if query is a package
        val directIntent = pm.getLaunchIntentForPackage(targetQuery)
        if (directIntent != null) {
            return launchAppAndReturnResult(
                context = context,
                rawCommand = rawCommand,
                recognizedAppName = recognizedAppName,
                packageName = targetQuery,
                launchIntent = directIntent,
                matchMethod = "direct package intent"
            )
        }

        // 4. App not installed / launch intent not found
        logExecutionDetail(
            recognizedAppName = recognizedAppName,
            matchedPackageName = null,
            intentFound = false,
            launchResult = "App is not installed"
        )
        return CommandResult.Error(rawCommand, "App is not installed")
    }

    private fun launchAppAndReturnResult(
        context: Context,
        rawCommand: String,
        recognizedAppName: String,
        packageName: String,
        launchIntent: Intent,
        matchMethod: String
    ): CommandResult {
        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)

            logExecutionDetail(
                recognizedAppName = recognizedAppName,
                matchedPackageName = packageName,
                intentFound = true,
                launchResult = "Successfully launched via $matchMethod"
            )

            CommandResult.Success(rawCommand, "Opening $recognizedAppName...")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting activity for $packageName", e)

            logExecutionDetail(
                recognizedAppName = recognizedAppName,
                matchedPackageName = packageName,
                intentFound = true,
                launchResult = "Activity launch failed: ${e.message}"
            )

            CommandResult.Error(rawCommand, "App is not installed")
        }
    }

    private fun logExecutionDetail(
        recognizedAppName: String,
        matchedPackageName: String?,
        intentFound: Boolean,
        launchResult: String
    ) {
        Log.d(TAG, "=== App Launch Execution Details ===")
        Log.d(TAG, " - Recognized App Name : $recognizedAppName")
        Log.d(TAG, " - Matched Package Name : ${matchedPackageName ?: "None"}")
        Log.d(TAG, " - Launch Intent Found  : $intentFound")
        Log.d(TAG, " - Launch Result        : $launchResult")
    }

    private fun findAppMetadata(query: String): AppMetadata? {
        return knownApps.firstOrNull { meta ->
            meta.aliases.any { alias -> alias == query || query.contains(alias) || alias.contains(query) }
        }
    }

    private fun extractTargetAppQuery(normalizedText: String): String {
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

        clean = clean.removeSuffix(" app")
            .removePrefix("app ")
            .removeSuffix(" please")
            .trim()

        return clean
    }

    private fun getInstalledLaunchableApps(pm: PackageManager): List<ResolveInfo> {
        return try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying launchable activities", e)
            emptyList()
        }
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
