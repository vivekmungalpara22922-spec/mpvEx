package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * Manages private (hidden) folders and PIN authentication.
 * Uses SharedPreferences to persist data.
 */
object PrivateFolderManager {

    private const val PREFS_NAME = "private_folder_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PRIVATE_FOLDERS = "private_folder_paths"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── PIN management ──────────────────────────────────────────────

    fun isPinSet(context: Context): Boolean =
        prefs(context).getString(KEY_PIN_HASH, null) != null

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PIN_HASH, hashPin(pin)).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val stored = prefs(context).getString(KEY_PIN_HASH, null) ?: return false
        return stored == hashPin(pin)
    }

    fun resetPin(context: Context) {
        prefs(context).edit().remove(KEY_PIN_HASH).apply()
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── Private folder list ─────────────────────────────────────────

    fun getPrivateFolderPaths(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PRIVATE_FOLDERS, emptySet()) ?: emptySet()

    fun addPrivateFolder(context: Context, folderPath: String) {
        val current = getPrivateFolderPaths(context).toMutableSet()
        current.add(folderPath)
        prefs(context).edit().putStringSet(KEY_PRIVATE_FOLDERS, current).apply()
    }

    fun removePrivateFolder(context: Context, folderPath: String) {
        val current = getPrivateFolderPaths(context).toMutableSet()
        current.remove(folderPath)
        prefs(context).edit().putStringSet(KEY_PRIVATE_FOLDERS, current).apply()
    }

    fun isPrivate(context: Context, folderPath: String): Boolean =
        getPrivateFolderPaths(context).contains(folderPath)
}
