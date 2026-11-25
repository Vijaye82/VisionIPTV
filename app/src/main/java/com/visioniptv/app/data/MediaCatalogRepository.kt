package com.visioniptv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class CatalogTitle(
    val id: String,
    val title: String,
    val streamUrl: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val category: String = "",
    val year: String = "",
    val overview: String = "",
    val kind: String = "movie",
    val tmdbId: Int? = null,
    val rating: Double? = null,
    val voteCount: Int = 0,
    val genres: String = "",
    val runtime: String = "",
    val cast: String = "",
    val director: String = "",
    val imdbId: String = ""
)

class MediaCatalogRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private suspend fun get(url: String, bearer: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
            if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
                JSONObject(response.body?.string() ?: "{}")
            }
        }

    private fun xtreamUrl(base: String, account: PlaylistAccount, action: String, extra: String = ""): String {
        val user = URLEncoder.encode(account.username, "UTF-8")
        val pass = URLEncoder.encode(account.password, "UTF-8")
        return "${base.trimEnd('/')}/player_api.php?username=$user&password=$pass&action=$action$extra"
    }

    suspend fun loadMovies(account: PlaylistAccount, base: String): List<CatalogTitle> {
        val json = get(xtreamUrl(base, account, "get_vod_streams"))
        // Xtream list endpoints return arrays; parse through JSONArray to preserve provider ordering.
        val array = org.json.JSONArray(json.toString())
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("stream_id")
                val ext = item.optString("container_extension", "mp4").ifBlank { "mp4" }
                add(CatalogTitle(
                    id = id, title = item.optString("name", "Movie"),
                    streamUrl = "${base.trimEnd('/')}/movie/${account.username}/${account.password}/$id.$ext",
                    posterUrl = item.optString("stream_icon"),
                    backdropUrl = item.optJSONArray("backdrop_path")?.optString(0).orEmpty(),
                    category = item.optString("category_name"),
                    year = item.optString("year"), overview = item.optString("plot"), kind = "movie",
                    imdbId = item.optString("imdb")
                ))
            }
        }
    }

    suspend fun loadSeries(account: PlaylistAccount, base: String): List<CatalogTitle> {
        val array = org.json.JSONArray(withContext(Dispatchers.IO) {
            val request = Request.Builder().url(xtreamUrl(base, account, "get_series")).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
                response.body?.string() ?: "[]"
            }
        })
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(CatalogTitle(
                    id = item.optString("series_id"), title = item.optString("name", "Series"),
                    streamUrl = "", posterUrl = item.optString("cover"),
                    backdropUrl = item.optJSONArray("backdrop_path")?.optString(0).orEmpty(),
                    category = item.optString("category_name"), year = item.optString("releaseDate"),
                    overview = item.optString("plot"), kind = "tv",
                    rating = item.optString("rating").toDoubleOrNull(),
                    imdbId = item.optString("imdb")
                ))
            }
        }
    }

    suspend fun enrich(title: CatalogTitle, bearer: String): CatalogTitle? {
        if (bearer.isBlank()) return null
        val query = URLEncoder.encode(title.title, "UTF-8")
        val searchPath = if (title.kind == "tv") "tv" else "movie"
        val search = get("https://api.themoviedb.org/3/search/$searchPath?query=$query&include_adult=false", bearer)
        val results = search.optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val wantedYear = title.year.take(4)
        var match: JSONObject? = null
        var best = -1
        for (i in 0 until results.length()) {
            val candidate = results.optJSONObject(i) ?: continue
            val name = candidate.optString(if (title.kind == "tv") "name" else "title")
            val date = candidate.optString(if (title.kind == "tv") "first_air_date" else "release_date").take(4)
            val score = (if (name.equals(title.title, true)) 4 else if (name.contains(title.title, true) || title.title.contains(name, true)) 2 else 0) +
                (if (wantedYear.isNotBlank() && wantedYear == date) 2 else 0)
            if (score > best) { best = score; match = candidate }
        }
        val found = match ?: return null
        if (best < 2) return null
        val id = found.optInt("id")
        val detailPath = if (title.kind == "tv") "tv" else "movie"
        val details = get("https://api.themoviedb.org/3/$detailPath/$id?append_to_response=credits,videos", bearer)
        val genres = details.optJSONArray("genres")
        val genreText = (0 until (genres?.length() ?: 0)).mapNotNull { genres?.optJSONObject(it)?.optString("name") }.joinToString(" • ")
        val credits = details.optJSONObject("credits")
        val castArray = credits?.optJSONArray("cast")
        val castText = (0 until minOf(castArray?.length() ?: 0, 6)).mapNotNull { castArray?.optJSONObject(it)?.optString("name") }.joinToString(", ")
        val crew = credits?.optJSONArray("crew")
        val director = (0 until (crew?.length() ?: 0)).mapNotNull { crew?.optJSONObject(it) }
            .firstOrNull { it.optString("job") == "Director" }?.optString("name").orEmpty()
        val posterPath = details.optString("poster_path", found.optString("poster_path"))
        val backdropPath = details.optString("backdrop_path", found.optString("backdrop_path"))
        val date = details.optString(if (title.kind == "tv") "first_air_date" else "release_date")
        val runtime = if (title.kind == "tv") {
            val arr = details.optJSONArray("episode_run_time")
            if (arr != null && arr.length() > 0) "${arr.optInt(0)} min/episode" else ""
        } else if (details.optInt("runtime") > 0) "${details.optInt("runtime")} min" else ""
        return title.copy(
            posterUrl = posterPath.takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/w500$it" } ?: title.posterUrl,
            backdropUrl = backdropPath.takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/w1280$it" } ?: title.backdropUrl,
            year = date.take(4).ifBlank { title.year },
            overview = details.optString("overview").ifBlank { title.overview },
            tmdbId = id, rating = details.optDouble("vote_average").takeIf { details.has("vote_average") },
            voteCount = details.optInt("vote_count"), genres = genreText, runtime = runtime,
            cast = castText, director = director
        )
    }
}
