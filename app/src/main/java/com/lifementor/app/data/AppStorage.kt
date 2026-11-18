package com.lifementor.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Metadata for one generated document version */
data class PlanMeta(
    val id: Long,
    val model: String,
    val note: String,
    val summary: String,
    val fileName: String,
)

/**
 * Local storage: profile, settings and Markdown documents (with version history) live
 * in the app's private directory only — removed on uninstall, never uploaded.
 */
object AppStorage {
    private lateinit var ctx: Context
    val appCtx: Context get() = ctx

    fun init(c: Context) {
        ctx = c.applicationContext
    }

    private val profileFile get() = File(ctx.filesDir, "profile.json")
    private val settingsFile get() = File(ctx.filesDir, "settings.json")
    private val prefsFile get() = File(ctx.filesDir, "prefs.json")

    /** Doc buckets: plans = career plan, reviews = weekly review */
    private fun bucketDir(bucket: String) = File(ctx.filesDir, bucket).apply { mkdirs() }
    private fun bucketIndex(bucket: String) = File(bucketDir(bucket), "index.json")

    fun prefs(): JSONObject = try {
        if (prefsFile.exists()) JSONObject(prefsFile.readText()) else JSONObject()
    } catch (e: Exception) {
        JSONObject()
    }

    fun putPref(key: String, value: Any) {
        prefsFile.writeText(prefs().put(key, value).toString())
    }

    fun loadProfile(): Profile =
        if (profileFile.exists()) Profile.fromStoredJson(profileFile.readText()) else Profile()

    fun saveProfile(p: Profile) {
        profileFile.writeText(p.toStoredJson())
    }

    fun loadSettings(): Settings =
        if (settingsFile.exists()) Settings.fromJson(settingsFile.readText()) else Settings()

    fun saveSettings(s: Settings) {
        settingsFile.writeText(s.toJson())
    }

    /** All versions in a bucket, ascending by generation time */
    fun versions(bucket: String): List<PlanMeta> {
        val indexFile = bucketIndex(bucket)
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PlanMeta(
                    id = o.getLong("id"),
                    model = o.optString("model"),
                    note = o.optString("note"),
                    summary = o.optString("summary"),
                    fileName = o.getString("file"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun readDoc(bucket: String, meta: PlanMeta): String =
        try {
            File(bucketDir(bucket), meta.fileName).readText()
        } catch (e: Exception) {
            ""
        }

    /** Persist a new/updated document version and return its metadata */
    fun saveDoc(bucket: String, content: String, model: String, note: String): PlanMeta {
        val id = System.currentTimeMillis()
        val fileName = "$bucket-$id.md"
        File(bucketDir(bucket), fileName).writeText(content)
        val summary = content.lines()
            .firstOrNull { it.isNotBlank() && !it.startsWith("```") }
            ?.replace(Regex("^#+\\s*"), "")
            ?.take(30).orEmpty()

        val list = versions(bucket).toMutableList()
        list.add(PlanMeta(id, model, note, summary, fileName))
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id).put("model", m.model).put("note", m.note)
                    .put("summary", m.summary).put("file", m.fileName)
            )
        }
        bucketIndex(bucket).writeText(arr.toString())
        return PlanMeta(id, model, note, summary, fileName)
    }

    /** Copy into the cache dir for the system share sheet */
    fun fileForShare(bucket: String, meta: PlanMeta): File? = try {
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        val prefix = if (bucket == "reviews") "Weekly-Review" else "Life-Career-Plan"
        File(dir, "$prefix-${meta.id}.md").apply { writeText(readDoc(bucket, meta)) }
    } catch (e: Exception) {
        null
    }

    /** Danger zone: wipe profile/settings/doc buckets/prefs (role & goals are cleared by GameStore.clearAll) */
    fun wipeDocsAndProfile() {
        ctx.deleteFile("profile.json")
        ctx.deleteFile("settings.json")
        ctx.deleteFile("prefs.json")
        File(ctx.filesDir, "plans").deleteRecursively()
        File(ctx.filesDir, "reviews").deleteRecursively()
    }
}
