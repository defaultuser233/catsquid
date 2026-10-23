package com.maoyou.typingpet

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** キャラ・画像・設定を zip にまとめてバックアップ/復元する */
object Backup {
    const val SCHEMA = 1
    const val MAX_ZIP_BYTES = 250L * 1024 * 1024
    private const val APP_ID = "com.maoyou.typingpet"
    private const val MANIFEST = "backup.json"
    private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
    private const val MAX_TOTAL_IMAGE_BYTES = 200L * 1024 * 1024
    private const val MAX_IMAGES = 2000

    data class ImportResult(val language: String)

    /** out に zip を書き、含めた画像枚数を返す */
    fun write(context: Context, out: OutputStream): Int {
        val root = JSONObject().apply {
            put("app", APP_ID)
            put("schema", SCHEMA)
            put("exportedAt", System.currentTimeMillis())
            put("versionName", versionName(context))
            put("active", Prefs.getActiveCharacter(context))
        }
        val shown = JSONArray()
        Prefs.getShownIds(context).forEach { shown.put(it) }
        root.put("shown", shown)
        root.put("settings", settingsJson(context))

        val images = LinkedHashMap<String, String>()
        val chars = JSONArray()
        Prefs.getCharacters(context).forEach { c ->
            val o = JSONObject().apply {
                put("id", c.id)
                put("n", c.name)
                put("preset", c.preset.coerceIn(0, 3))
                put("fx", c.fx)
                put("fy", c.fy)
                put("idle", imageArray(c.idle, images))
                put("steps", nestedImageArray(c.steps, images))
                put("tr", triggerArray(c.triggers, images))
            }
            chars.put(o)
        }
        root.put("characters", chars)

        val zos = ZipOutputStream(out.buffered())
        try {
            zos.putNextEntry(ZipEntry(MANIFEST))
            zos.write(root.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            images.forEach { (uri, path) ->
                val input = context.contentResolver.openInputStream(Uri.parse(uri))
                    ?: return@forEach
                zos.putNextEntry(ZipEntry(path))
                input.use { it.copyTo(zos) }
                zos.closeEntry()
            }
        } finally {
            zos.close()
        }
        return images.size
    }

    fun read(context: Context, zipFile: File): ImportResult {
        ZipFile(zipFile).use { zip ->
            val manifestEntry = zip.getEntry(MANIFEST) ?: throw IOException("missing manifest")
            val manifest = readLimited(zip.getInputStream(manifestEntry), MAX_MANIFEST_BYTES)
            val root = JSONObject(String(manifest, Charsets.UTF_8))
            if (root.optInt("schema", -1) != SCHEMA || root.optString("app") != APP_ID) {
                throw IOException("unsupported backup")
            }
            val sourceChars = root.optJSONArray("characters") ?: throw IOException("missing characters")
            if (sourceChars.length() == 0) throw IOException("empty characters")

            val imageDir = File(context.filesDir, "images").apply { mkdirs() }
            val extracted = HashMap<String, String>()
            var totalBytes = 0L

            fun imageUri(ref: String): String {
                if (ref.isBlank()) return ""
                extracted[ref]?.let { return it }
                if (extracted.size >= MAX_IMAGES) throw IOException("too many images")
                val entry = zip.getEntry(ref) ?: return ""
                val file = File(imageDir, UUID.randomUUID().toString())
                try {
                    zip.getInputStream(entry).use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(8192)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                totalBytes += n
                                if (totalBytes > MAX_TOTAL_IMAGE_BYTES) throw IOException("backup too large")
                                out.write(buf, 0, n)
                            }
                        }
                    }
                } catch (e: Exception) {
                    file.delete()
                    throw e
                }
                if (file.length() == 0L) {
                    file.delete()
                    return ""
                }
                val uri = Uri.fromFile(file).toString()
                extracted[ref] = uri
                return uri
            }

            val idMap = HashMap<String, String>()
            val usedIds = HashSet<String>()
            val outChars = JSONArray()
            for (i in 0 until sourceChars.length()) {
                val source = sourceChars.optJSONObject(i) ?: continue
                val oldId = source.optString("id")
                var newId = oldId
                if (newId.isBlank() || !usedIds.add(newId)) {
                    newId = UUID.randomUUID().toString()
                    usedIds.add(newId)
                }
                if (oldId.isNotBlank() && !idMap.containsKey(oldId)) idMap[oldId] = newId

                val o = JSONObject().apply {
                    put("id", newId)
                    put(
                        "n",
                        source.optString("n").ifBlank { context.getString(R.string.default_char_name) }
                    )
                    put("preset", source.optInt("preset", 0).coerceIn(0, 3))
                    val fx = source.optDouble("fx", Double.NaN)
                    val fy = source.optDouble("fy", Double.NaN)
                    if (fx.isFinite()) put("fx", fx.coerceIn(0.0, 1.0))
                    if (fy.isFinite()) put("fy", fy.coerceIn(0.0, 1.0))
                    put("idle", mappedImages(source.optJSONArray("idle")) { imageUri(it) })
                    put("steps", mappedSteps(source.optJSONArray("steps")) { imageUri(it) })
                    put("tr", mappedTriggers(source.optJSONArray("tr")) { imageUri(it) })
                }
                outChars.put(o)
            }
            if (outChars.length() == 0) throw IOException("empty characters")

            val active = root.optInt("active", 0).coerceIn(0, outChars.length() - 1)
            val shown = HashSet<String>()
            val sourceShown = root.optJSONArray("shown")
            if (sourceShown != null) {
                for (i in 0 until sourceShown.length()) {
                    idMap[sourceShown.optString(i)]?.let { shown.add(it) }
                }
            }
            if (shown.isEmpty()) {
                outChars.optJSONObject(active)?.optString("id")?.let {
                    if (it.isNotBlank()) shown.add(it)
                }
            }

            Prefs.replaceCharacters(context, outChars, active, shown)
            applySettings(context, root.optJSONObject("settings"))
            return ImportResult(Prefs.getLanguage(context))
        }
    }

    private fun settingsJson(context: Context): JSONObject = JSONObject().apply {
        put("size", Prefs.getSizeKey(context))
        put("shake", Prefs.getShakeLevel(context))
        put("showShadow", Prefs.getShowShadow(context))
        put("tiltEnabled", Prefs.getTiltEnabled(context))
        put("tiltSensitivityPct", Prefs.getTiltSensitivityPct(context))
        put("positionLocked", Prefs.getPositionLocked(context))
        put("alwaysOnTop", Prefs.getAlwaysOnTop(context))
        put("language", Prefs.getLanguage(context))
    }

    private fun applySettings(context: Context, settings: JSONObject?) {
        if (settings == null) return
        if (settings.has("size")) {
            val size = settings.optString("size")
            if (size in setOf("small", "medium", "large", "xlarge")) Prefs.setSizeKey(context, size)
        }
        if (settings.has("shake")) Prefs.setShakeLevel(context, settings.optInt("shake", 3).coerceIn(0, 3))
        if (settings.has("showShadow")) Prefs.setShowShadow(context, settings.optBoolean("showShadow", true))
        if (settings.has("tiltEnabled")) Prefs.setTiltEnabled(context, settings.optBoolean("tiltEnabled", true))
        if (settings.has("tiltSensitivityPct")) {
            Prefs.setTiltSensitivityPct(context, settings.optInt("tiltSensitivityPct", 75).coerceIn(0, 100))
        }
        if (settings.has("positionLocked")) {
            Prefs.setPositionLocked(context, settings.optBoolean("positionLocked", false))
        }
        if (settings.has("alwaysOnTop")) {
            Prefs.setAlwaysOnTop(context, settings.optBoolean("alwaysOnTop", true))
        }
        if (settings.has("language")) {
            val language = settings.optString("language")
            if (language in setOf(Prefs.LANG_SYSTEM, "ja", "en", "ko", "zh")) {
                Prefs.setLanguage(context, language)
            }
        }
    }

    private fun imageArray(uris: List<String>, images: MutableMap<String, String>): JSONArray {
        val arr = JSONArray()
        uris.forEach { uri ->
            if (uri.isNotBlank()) arr.put(images.getOrPut(uri) { "images/${UUID.randomUUID()}" })
        }
        return arr
    }

    private fun nestedImageArray(lists: List<List<String>>, images: MutableMap<String, String>): JSONArray {
        val arr = JSONArray()
        lists.forEach { arr.put(imageArray(it, images)) }
        return arr
    }

    private fun triggerArray(triggers: List<Prefs.Trigger>, images: MutableMap<String, String>): JSONArray {
        val arr = JSONArray()
        triggers.forEach { t ->
            arr.put(JSONObject().apply {
                put("k", stringArray(t.keys))
                put("i", imageArray(t.images, images))
            })
        }
        return arr
    }

    private fun mappedImages(source: JSONArray?, resolve: (String) -> String): JSONArray {
        val out = JSONArray()
        if (source == null) return out
        for (i in 0 until source.length()) {
            val uri = resolve(source.optString(i))
            if (uri.isNotBlank()) out.put(uri)
        }
        return out
    }

    private fun mappedSteps(source: JSONArray?, resolve: (String) -> String): JSONArray {
        val out = JSONArray()
        if (source == null) return out
        for (i in 0 until source.length()) {
            out.put(mappedImages(source.optJSONArray(i), resolve))
        }
        return out
    }

    private fun mappedTriggers(source: JSONArray?, resolve: (String) -> String): JSONArray {
        val out = JSONArray()
        if (source == null) return out
        for (i in 0 until source.length()) {
            val t = source.optJSONObject(i) ?: continue
            out.put(JSONObject().apply {
                put("k", stringArray(parseStrings(t.optJSONArray("k"))))
                put("i", mappedImages(t.optJSONArray("i"), resolve))
            })
        }
        return out
    }

    private fun parseStrings(source: JSONArray?): List<String> {
        if (source == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until source.length()) {
            val s = source.optString(i)
            if (s.isNotBlank()) out.add(s)
        }
        return out
    }

    private fun stringArray(values: List<String>): JSONArray {
        val arr = JSONArray()
        values.forEach { arr.put(it) }
        return arr
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        input.use { stream ->
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > max) throw IOException("manifest too large")
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    private fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }
}
