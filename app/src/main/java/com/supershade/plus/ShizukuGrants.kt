package com.supershade.plus

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import com.supershade.shizuku.StatusBarGovernor

/**
 * Grants SuperShade every permission it can use, through Shizuku, in one go: its runtime
 * permissions, drawing over other apps, changing system and secure settings, notification access,
 * its accessibility service, Do Not Disturb access, installing its updates, and no battery limits.
 */
object ShizukuGrants {
    class Result(val granted: List<String>, val failed: List<String>)

    suspend fun grantAll(context: Context, governor: StatusBarGovernor): Result {
        val pkg = context.packageName
        val granted = ArrayList<String>()
        val failed = ArrayList<String>()
        suspend fun step(label: String, vararg cmd: String) {
            if (governor.runShell(*cmd)) granted += label else failed += label
        }
        // Each runtime ("dangerous") permission the app asks for.
        val info: PackageInfo? = runCatching {
            context.packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        }.getOrNull()
        val requested = info?.requestedPermissions.orEmpty()
        for (perm in requested) {
            val protection = runCatching {
                context.packageManager.getPermissionInfo(perm, 0).protection
            }.getOrNull() ?: continue
            if (protection == PermissionInfo.PROTECTION_DANGEROUS) step(perm.substringAfterLast('.'), "pm", "grant", pkg, perm)
        }
        // Development-level permissions shell may grant.
        step("Secure settings", "pm", "grant", pkg, "android.permission.WRITE_SECURE_SETTINGS")
        // App-op permissions.
        step("Draw over other apps", "appops", "set", pkg, "SYSTEM_ALERT_WINDOW", "allow")
        step("Modify system settings", "appops", "set", pkg, "WRITE_SETTINGS", "allow")
        step("Install updates", "appops", "set", pkg, "REQUEST_INSTALL_PACKAGES", "allow")
        step("Run in the background", "cmd", "deviceidle", "whitelist", "+$pkg")
        step("Unrestricted battery", "appops", "set", pkg, "RUN_ANY_IN_BACKGROUND", "allow")
        // Notification access and Do Not Disturb access.
        val listener = ComponentName(pkg, "com.supershade.service.NotificationCollector").flattenToString()
        step("Notification access", "cmd", "notification", "allow_listener", listener)
        step("Do Not Disturb access", "cmd", "notification", "allow_dnd", pkg)
        // The accessibility service, added to whatever's already on.
        val service = ComponentName(pkg, "com.supershade.service.SuperShadeAccessibilityService").flattenToString()
        val current = governor.runShellOutput("settings", "get", "secure", "enabled_accessibility_services").trim()
        val services = current.split(':').filter { it.isNotBlank() && it != "null" }.toMutableList()
        if (service !in services) services += service
        val okA11y = governor.runShell("settings", "put", "secure", "enabled_accessibility_services", services.joinToString(":")) &&
            governor.runShell("settings", "put", "secure", "accessibility_enabled", "1")
        if (okA11y) granted += "Accessibility service" else failed += "Accessibility service"
        return Result(granted, failed)
    }
}
