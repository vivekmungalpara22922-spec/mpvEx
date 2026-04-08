package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Manages private (hidden) folders and PIN authentication.
 * Creates .nomedia files to hide folders from Gallery and other apps.
 */
object PrivateFolderManager {

    private const val TAG = "PrivateFolderManager"
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

    /**
     * Makes a folder private:
     * 1. Adds it to the private list
     * 2. Creates a .nomedia file so Gallery/other apps can't see it
     * 3. Triggers a media scan so changes take effect immediately
     */
    fun addPrivateFolder(context: Context, folderPath: String) {
        val current = getPrivateFolderPaths(context).toMutableSet()
        current.add(folderPath)
        prefs(context).edit().putStringSet(KEY_PRIVATE_FOLDERS, current).apply()

        createNoMediaFile(folderPath)
        triggerMediaScan(context, folderPath)
    }

    /**
     * Removes a folder from private:
     * 1. Removes it from the private list
     * 2. Deletes the .nomedia file so Gallery/other apps can see it again
     * 3. Triggers a media scan so files reappear everywhere
     */
    fun removePrivateFolder(context: Context, folderPath: String) {
        val current = getPrivateFolderPaths(context).toMutableSet()
        current.remove(folderPath)
        prefs(context).edit().putStringSet(KEY_PRIVATE_FOLDERS, current).apply()

        deleteNoMediaFile(folderPath)
        triggerMediaScan(context, folderPath)
    }

    fun isPrivate(context: Context, folderPath: String): Boolean =
        getPrivateFolderPaths(context).contains(folderPath)

    // ── .nomedia file management ────────────────────────────────────

    /**
     * Creates a .nomedia file inside the folder.
     * Android's MediaStore ignores any folder containing .nomedia,
     * so videos/images won't show in Gallery, Google Photos, etc.
     */
    private fun createNoMediaFile(folderPath: String) {
        try {
            val noMediaFile = File(folderPath, ".nomedia")
            if (!noMediaFile.exists()) {
                val created = noMediaFile.createNewFile()
                if (created) {
                    Log.d(TAG, "Created .nomedia in: $folderPath")
                } else {
                    Log.e(TAG, "Failed to create .nomedia in: $folderPath")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating .nomedia file in: $folderPath", e)
        }
    }

    /**
     * Deletes the .nomedia file so media becomes visible again.
     */
    private fun deleteNoMediaFile(folderPath: String) {
        try {
            val noMediaFile = File(folderPath, ".nomedia")
            if (noMediaFile.exists()) {
                val deleted = noMediaFile.delete()
                if (deleted) {
                    Log.d(TAG, "Deleted .nomedia from: $folderPath")
                } else {
                    Log.e(TAG, "Failed to delete .nomedia from: $folderPath")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting .nomedia file from: $folderPath", e)
        }
    }

    /**
     * Triggers Android's MediaScanner to rescan the folder.
     * Makes changes take effect immediately in Gallery and other apps.
     */
    private fun triggerMediaScan(context: Context, folderPath: String) {
        try {
            val folder = File(folderPath)
            if (!folder.exists()) return

            val filePaths = mutableListOf<String>()
            filePaths.add(File(folderPath, ".nomedia").absolutePath)

            folder.listFiles()?.forEach { file ->
                if (file.isFile) {
                    filePaths.add(file.absolutePath)
                }
            }

            if (filePaths.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    context,
                    filePaths.toTypedArray(),
                    null
                ) { path, uri ->
                    Log.d(TAG, "Scanned: $path -> $uri")
                }
            }

            Log.d(TAG, "Triggered media scan for: $folderPath (${filePaths.size} files)")
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering media scan for: $folderPath", e)
        }
    }
}
