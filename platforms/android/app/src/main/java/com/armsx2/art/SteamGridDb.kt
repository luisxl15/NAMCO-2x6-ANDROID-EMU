package com.armsx2.art

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.armsx2.CustomCovers
import com.armsx2.GameInfo
import com.armsx2.runtime.MainActivityRuntime
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * SteamGridDB cover art.
 *
 * The console cover repo the library normally uses is keyed by disc serial, so it has nothing
 * for an arcade gameid (NM00004) and nothing for a disc it doesn't carry. SteamGridDB is keyed
 * by TITLE, which is exactly what those games do have — so it fills the gap, including for the
 * System 246/256 titles this build exists for.
 *
 * Fetched art is written through [CustomCovers], so it lands in the same place a manually
 * picked cover would and every existing cover lookup finds it with no further plumbing.
 *
 * Requires a personal API key (steamgriddb.com → profile → preferences → API).
 */
object SteamGridDb {
    private const val KEY_PREF = "art.sgdbApiKey"
    private const val BASE = "https://www.steamgriddb.com/api/v2"
    private const val TIMEOUT_MS = 12_000

    /** The user's API key. Empty means the feature is simply off. */
    val apiKey = mutableStateOf("")

    fun load() {
        apiKey.value = MainActivityRuntime.prefs.getString(KEY_PREF, "").orEmpty()
    }

    fun setKey(value: String) {
        val trimmed = value.trim()
        apiKey.value = trimmed
        MainActivityRuntime.prefs.edit().putString(KEY_PREF, trimmed).apply()
    }

    val configured: Boolean get() = apiKey.value.isNotBlank()

    /** Why a fetch didn't produce a cover — surfaced to the user rather than swallowed. */
    sealed interface Failure {
        data object NoKey : Failure
        data object InvalidKey : Failure
        data object NotFound : Failure
        data class Network(val message: String) : Failure
    }

    private fun get(path: String): Result<JSONObject> {
        val key = apiKey.value
        if (key.isBlank()) return Result.failure(FailureException(Failure.NoKey))
        return runCatching {
            val conn = (URL("$BASE$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "pcsx2x6-android")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            try {
                when (conn.responseCode) {
                    // A bad key must not read as "no art found" — that sends the user hunting
                    // for the wrong problem.
                    401, 403 -> throw FailureException(Failure.InvalidKey)
                    404 -> JSONObject("""{"success":false,"data":[]}""")
                    else -> JSONObject(conn.inputStream.bufferedReader().readText())
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    private class FailureException(val failure: Failure) : Exception()

    /** The SteamGridDB game id best matching [title], or null. */
    private fun searchId(title: String): Int? {
        val q = URLEncoder.encode(title, "UTF-8")
        val json = get("/search/autocomplete/$q").getOrThrow()
        val arr = json.optJSONArray("data") ?: return null
        if (arr.length() == 0) return null
        return arr.optJSONObject(0)?.optInt("id")?.takeIf { it != 0 }
    }

    /** First cover URL for [gameId]. Prefers portrait box art, then anything the game has. */
    private fun firstGridUrl(gameId: Int): String? {
        // 600x900 is the box-art shape the library grid is laid out for.
        val portrait = get("/grids/game/$gameId?dimensions=600x900&limit=1").getOrThrow()
        portrait.optJSONArray("data")?.optJSONObject(0)?.optString("url")
            ?.takeIf { it.isNotBlank() }?.let { return it }
        val any = get("/grids/game/$gameId?limit=1").getOrThrow()
        return any.optJSONArray("data")?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
    }

    private fun download(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "pcsx2x6-android")
        }
        return try {
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /** One SteamGridDB entry a title matched. */
    data class Match(val id: Int, val name: String)

    /**
     * Every game the term matches, not just the best one.
     *
     * The automatic path takes the first and stops, which is wrong twice over for arcade
     * titles: the exact name often is not there ("Battle Gear 3 Tuned" matches "Battle Gear 3"),
     * and the top match can be a different game entirely that happens to have no art at all
     * ("Dragon Chronicle" -> "Dragon Chronicles: Black Tears", zero grids), which reads to the
     * user as "no cover exists" when the right entry was three rows down.
     */
    suspend fun searchGames(term: String): Result<List<Match>> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(term.trim(), "UTF-8")
            val arr = get("/search/autocomplete/$q").getOrThrow().optJSONArray("data")
                ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optInt("id").takeIf { it != 0 } ?: continue
                    add(Match(id, o.optString("name").ifBlank { "#$id" }))
                }
            }
        }
    }

    /** Every cover a game has, so the caller can show them and let a person choose. */
    suspend fun coverCandidates(gameId: Int): Result<List<String>> =
        candidates("/grids/game/$gameId?limit=40")

    /** Every wide hero a game has. */
    suspend fun heroCandidates(gameId: Int): Result<List<String>> =
        candidates("/heroes/game/$gameId?limit=40")

    private suspend fun candidates(path: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = get(path).getOrThrow().optJSONArray("data") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.optString("url")?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
    }

    /** Store [url] as this game's cover. */
    suspend fun applyCover(context: Context, game: GameInfo, url: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!CustomCovers.setBytes(context, game, download(url))) {
                    throw FailureException(Failure.Network("Não foi possível gravar a capa"))
                }
            }
        }

    /** Store [url] as this game's wide background art. */
    suspend fun applyHero(context: Context, game: GameInfo, url: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!HeroArt.setBytes(context, game, download(url))) {
                    throw FailureException(Failure.Network("Não foi possível gravar a arte de fundo"))
                }
            }
        }

    /** First hero (wide key-art) URL for [gameId]. */
    private fun firstHeroUrl(gameId: Int): String? {
        // 1920x620 is the shape the featured card crops to; fall back to whatever the game has.
        val wide = get("/heroes/game/$gameId?dimensions=1920x620&limit=1").getOrThrow()
        wide.optJSONArray("data")?.optJSONObject(0)?.optString("url")
            ?.takeIf { it.isNotBlank() }?.let { return it }
        val any = get("/heroes/game/$gameId?limit=1").getOrThrow()
        return any.optJSONArray("data")?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
    }

    /**
     * Find and store the wide hero artwork the featured card uses as its background.
     * Same contract as [fetchCover]; stored through [HeroArt] rather than [CustomCovers].
     */
    suspend fun fetchHero(context: Context, game: GameInfo): Result<Unit> = withContext(Dispatchers.IO) {
        if (!configured) return@withContext Result.failure(FailureException(Failure.NoKey))
        runCatching {
            val title = game.displayTitle(com.armsx2.EnglishTitles.enabled.value)
            val id = searchId(title) ?: throw FailureException(Failure.NotFound)
            val url = firstHeroUrl(id) ?: throw FailureException(Failure.NotFound)
            val bytes = download(url)
            if (!HeroArt.setBytes(context, game, bytes)) {
                throw FailureException(Failure.Network("Não foi possível gravar a arte de fundo"))
            }
        }
    }

    /**
     * Find and store a cover for [game]. Returns success, or the reason it didn't.
     * Safe to call for a game that already has one — the caller decides whether to skip.
     */
    suspend fun fetchCover(context: Context, game: GameInfo): Result<Unit> = withContext(Dispatchers.IO) {
        if (!configured) return@withContext Result.failure(FailureException(Failure.NoKey))
        runCatching {
            val title = game.displayTitle(com.armsx2.EnglishTitles.enabled.value)
            val id = searchId(title) ?: throw FailureException(Failure.NotFound)
            val url = firstGridUrl(id) ?: throw FailureException(Failure.NotFound)
            val bytes = download(url)
            if (!CustomCovers.setBytes(context, game, bytes)) {
                throw FailureException(Failure.Network("Não foi possível gravar a capa"))
            }
        }
    }

    /** Human-readable reason for a failed [fetchCover], in the app's language (pt-BR). */
    fun describe(error: Throwable): String = when ((error as? FailureException)?.failure) {
        Failure.NoKey -> "Informe sua chave da API do SteamGridDB."
        Failure.InvalidKey -> "Chave da API inválida — confira em steamgriddb.com."
        Failure.NotFound -> "Nenhuma capa encontrada para este título."
        is Failure.Network -> (error as FailureException).let { (it.failure as Failure.Network).message }
        null -> error.message ?: "Falha ao buscar a capa."
    }
}
