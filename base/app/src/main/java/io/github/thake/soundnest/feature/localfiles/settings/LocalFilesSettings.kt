/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.localfiles.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thake.soundnest.core.preferences.PolicySyncedSetting
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * Settings > Library: the pinned "Local Files" row of the Library and the excluded MediaStore
 * folders (WhatsApp voice notes and audio, recordings…). Backed by `"feature_local_files"`.
 */
object LocalFilesSettings : PolicySyncedSetting {
    private const val PREF_NAME = "local_files"
    private const val KEY_SHOWN = "local_files_shown"
    private const val KEY_FOLDERS_OUT = "folders_excluded"
    private const val KEY_FOLDERS_SEEN = "folders_seen"
    private const val KEY_FOLDERS_RULES = "folders_rules"

    /**
     * Messaging, recorder and system-sound folders, matched on a whole folder name: matched
     * anywhere in the path, "call", "voice" or "signal" also hid music folders ("Calling You",
     * "Voices", "Signal Fire" — tester report after beta.9).
     */
    private val defaultOut = Regex(
        """(?i)whatsapp.*|telegram.*|signal|messenger|recordings?|enregistrements|voice ?(notes|recorder|memos|recordings?)?|notifications|ringtones|alarms|call ?(recordings?|recorder)?|callrecord(ing|ings)?""",
    )
    /** The earlier rule, kept only to take back what it hid on its own. */
    private val oldDefaultOut = Regex(
        """(?i)whatsapp|telegram|signal|messenger|recordings?|enregistrements|voice|notifications|ringtones|alarms|call""",
    )

    private fun autoOut(folder: String) = folder.split('/').any { defaultOut.matches(it.trim()) }

    private fun prefs(context: Context) = context.featurePrefs(PREF_NAME)

    var shown by mutableStateOf(true)
        private set

    fun init(context: Context) {
        shown = prefs(context).getBoolean(KEY_SHOWN, true)
    }

    fun set(context: Context, value: Boolean) {
        shown = value
        prefs(context).edit().putBoolean(KEY_SHOWN, value).apply()
    }

    override fun enforcePolicy(context: Context, policy: ParentalPolicy) {
        set(context, policy.localFiles.enabled)
    }

    /** The folders left out; new messaging / recording folders are added the first time they are seen. */
    fun excludedFolders(context: Context, known: Set<String>): Set<String> {
        val p = prefs(context)
        val out = p.getStringSet(KEY_FOLDERS_OUT, emptySet()).orEmpty().toMutableSet()
        if (p.getInt(KEY_FOLDERS_RULES, 1) < 2) {
            out.removeAll { oldDefaultOut.containsMatchIn(it) && !autoOut(it) }
            p.edit().putStringSet(KEY_FOLDERS_OUT, out).putInt(KEY_FOLDERS_RULES, 2).apply()
        }
        val seen = p.getStringSet(KEY_FOLDERS_SEEN, emptySet()).orEmpty()
        val fresh = known - seen
        if (fresh.isNotEmpty()) {
            out += fresh.filter { autoOut(it) }
            p.edit().putStringSet(KEY_FOLDERS_OUT, out).putStringSet(KEY_FOLDERS_SEEN, seen + fresh).apply()
        }
        return out
    }

    fun isFolderExcluded(context: Context, folder: String): Boolean =
        folder in prefs(context).getStringSet(KEY_FOLDERS_OUT, emptySet()).orEmpty()

    fun setFolderShown(context: Context, folder: String, shown: Boolean) {
        val p = prefs(context)
        val out = p.getStringSet(KEY_FOLDERS_OUT, emptySet()).orEmpty().toMutableSet()
        if (shown) out -= folder else out += folder
        p.edit().putStringSet(KEY_FOLDERS_OUT, out).apply()
    }
}
