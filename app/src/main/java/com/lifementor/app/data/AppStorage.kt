package com.lifementor.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 一条规划版本的元数据 */
data class PlanMeta(
    val id: Long,
    val model: String,
    val note: String,
    val summary: String,
    val fileName: String,
)

/**
 * 本地存储：画像、设置、Markdown 规划（含版本历史）全部落在应用私有目录，
 * 卸载应用即清除，不上传任何数据。
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

    /** 通用文档桶：plans=职业规划，reviews=每周复盘 */
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

    /** 按生成时间升序返回某个桶内全部文档版本 */
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

    /** 保存一次新生成/更新的文档版本，返回其元数据 */
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

    /** 复制一份到 cache 目录供系统分享导出 */
    fun fileForShare(bucket: String, meta: PlanMeta): File? = try {
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        val prefix = if (bucket == "reviews") "每周复盘" else "人生职业规划"
        File(dir, "$prefix-${meta.id}.md").apply { writeText(readDoc(bucket, meta)) }
    } catch (e: Exception) {
        null
    }

    /** 危险区：删除画像/设置/文档桶/偏好（角色与目标由 GameStore.clearAll 负责） */
    fun wipeDocsAndProfile() {
        ctx.deleteFile("profile.json")
        ctx.deleteFile("settings.json")
        ctx.deleteFile("prefs.json")
        File(ctx.filesDir, "plans").deleteRecursively()
        File(ctx.filesDir, "reviews").deleteRecursively()
    }
}
