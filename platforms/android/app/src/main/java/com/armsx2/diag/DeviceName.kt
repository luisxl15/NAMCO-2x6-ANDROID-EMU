package com.armsx2.diag

import android.os.Build

/**
 * The name of the phone, as its owner would say it.
 *
 * `Build.MODEL` is not that name on a large part of the market. On Xiaomi it is a date-stamped
 * part number -- a Redmi Note reports `23100RN82L` -- and on OPPO, Huawei and vivo it is a
 * codename of the same kind. The performance overlay printed that string and the player could
 * not tell whether the app had recognised the device at all.
 *
 * The readable name is not in the SDK: each vendor puts it in a system property of its own, and
 * the set below is that list. Whichever answers first wins; if none does -- Google, Sony, most
 * Samsungs -- `Build.MODEL` already is the readable name and stays.
 *
 * Read once per process. The properties come from the reflected `SystemProperties`, and only if
 * that is closed off does this pay for one `getprop` subprocess, dumping every property in a
 * single spawn rather than one per key.
 */
object DeviceName {

    /** Vendor properties that carry the retail name, in the order they are worth asking. */
    internal val KEYS = listOf(
        "ro.product.marketname",         // Xiaomi (HyperOS/MIUI), and now several others
        "ro.vendor.product.marketname",
        "ro.product.vendor.marketname",
        "ro.product.odm.marketname",
        "ro.vendor.oplus.market.name",   // OPPO, OnePlus, realme
        "ro.oppo.market.name",
        "ro.config.marketing_name",      // Huawei, Honor
        "ro.vivo.market.name",           // vivo, iQOO
        "ro.semc.product.name",          // Sony
    )

    private val resolved: String by lazy {
        runCatching { pick(Build.MODEL.orEmpty(), ::property) }.getOrNull().orEmpty()
            .ifBlank { Build.MODEL.orEmpty() }
    }

    /** Just the name, for the overlay, where the line is one column wide. */
    fun short(): String = resolved

    /** Name, maker and -- when they differ -- the model code, for reports and the About screen. */
    fun full(): String = describe(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty(), resolved)

    /**
     * The first vendor property that names a device, or "" when none does.
     *
     * @param model what `Build.MODEL` says, so a property that merely repeats it is ignored.
     * @param property reads one system property; "" or null when it is unset.
     */
    internal fun pick(model: String, property: (String) -> String?): String {
        KEYS.forEach { key ->
            val value = property(key)?.trim().orEmpty()
            if (usable(value, model)) return value
        }
        return ""
    }

    /**
     * Whether a property value is a name rather than noise.
     *
     * Vendors leave these fields filled with placeholders as often as with names, and a wrong
     * answer here is worse than no answer: it would replace a part number the owner could at
     * least search for with a word that means nothing.
     */
    internal fun usable(value: String, model: String): Boolean {
        if (value.isBlank() || value.length > 40) return false
        if (value.equals(model, ignoreCase = true)) return false
        if (value.lowercase() in PLACEHOLDERS) return false
        // A name has a letter in it. "2201123G" and "0" do not qualify.
        return value.any { it.isLetter() }
    }

    private val PLACEHOLDERS = setOf("unknown", "none", "null", "default", "generic")

    /**
     * "Xiaomi Redmi Note 13 5G (23100RN82L)" -- the readable name, who made it, and the code the
     * device still reports, because that code is what a compatibility report is matched on later.
     */
    internal fun describe(manufacturer: String, model: String, name: String): String {
        val maker = manufacturer.trim()
        val shown = name.trim().ifBlank { model.trim() }
        // "samsung Galaxy S23" reads badly and "Xiaomi Xiaomi 14" reads worse: drop the maker
        // when the name already opens with it.
        val head = if (maker.isBlank() || shown.startsWith(maker, ignoreCase = true)) {
            shown
        } else {
            "$maker $shown"
        }
        return if (shown.equals(model.trim(), ignoreCase = true)) head else "$head (${model.trim()})"
    }

    // ---- reading the properties ------------------------------------------------------------

    private val dumped: Map<String, String> by lazy { runCatching { dumpProperties() }.getOrElse { emptyMap() } }

    private val reflected: ((String) -> String?)? by lazy {
        runCatching {
            val method = Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
            val read: (String) -> String? = { key -> method.invoke(null, key) as? String }
            read
        }.getOrNull()
    }

    private fun property(key: String): String? {
        reflected?.let { get -> runCatching { get(key) }.getOrNull()?.let { if (it.isNotBlank()) return it } }
        return dumped[key]
    }

    /** One `getprop` with no arguments: lines of the form `[key]: [value]`. */
    private fun dumpProperties(): Map<String, String> {
        if (reflected != null) return emptyMap()
        val process = ProcessBuilder("/system/bin/getprop").redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        return parseGetprop(out)
    }

    /** Lines of the form `[key]: [value]`; anything else in the dump is skipped. */
    internal fun parseGetprop(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            val split = line.indexOf("]: [")
            if (line.startsWith("[") && line.endsWith("]") && split > 1) {
                out[line.substring(1, split)] = line.substring(split + 4, line.length - 1)
            }
        }
        return out
    }
}
