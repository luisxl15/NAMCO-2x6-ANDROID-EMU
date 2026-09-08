package com.armsx2.diag

/**
 * One read of an Android system property.
 *
 * These are the questions the SDK does not answer: the retail name of a phone whose `Build.MODEL`
 * is a part number ([DeviceName]), and whether the graphics stack under us is a real driver or an
 * emulator's translation layer ([GraphicsQuirks]). Both live in vendor properties and nowhere else.
 *
 * Read through the reflected `SystemProperties` when that is open, and otherwise through a single
 * `getprop` subprocess that dumps everything at once -- one spawn for the life of the process,
 * rather than one per key.
 */
internal object SysProp {

    fun read(key: String): String? {
        reflected?.let { get -> runCatching { get(key) }.getOrNull()?.let { if (it.isNotBlank()) return it } }
        return dumped[key]
    }

    private val reflected: ((String) -> String?)? by lazy {
        runCatching {
            val method = Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
            val get: (String) -> String? = { key -> method.invoke(null, key) as? String }
            get
        }.getOrNull()
    }

    private val dumped: Map<String, String> by lazy {
        runCatching { dump() }.getOrElse { emptyMap() }
    }

    private fun dump(): Map<String, String> {
        if (reflected != null) return emptyMap()
        val process = ProcessBuilder("/system/bin/getprop").redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        return parse(out)
    }

    /** Lines of the form `[key]: [value]`; anything else in the dump is skipped. */
    internal fun parse(text: String): Map<String, String> {
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
