package com.morningdigest.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant

/** Public Norwegian Police operational log. Uses the official Politiloggen API. */
class PoliceIncidentFetcher(private val client: OkHttpClient) {
    data class Incident(
        val id: String,
        val categoryNo: String,
        val categoryEn: String,
        val municipality: String,
        val area: String,
        val createdMillis: Long,
        val norwegianText: String,
        val englishText: String,
        val isActive: Boolean,
        val sourceUrl: String = "https://www.politiet.no/politiloggen"
    )

    companion object {
        const val BASE_URL = "https://api.politiloggen.politiet.no"
        val CATEGORY_TRANSLATIONS = linkedMapOf(
            "Arrangement" to "Events",
            "Brann" to "Fire",
            "Dyr" to "Animals",
            "Innbrudd" to "Burglary",
            "Redning" to "Rescue",
            "Ro og orden" to "Public order",
            "Savnet" to "Missing person",
            "Sjø" to "Maritime incident",
            "Skadeverk" to "Vandalism / property damage",
            "Trafikk" to "Traffic",
            "Tyveri" to "Theft",
            "Ulykke" to "Accident",
            "Voldshendelse" to "Violence",
            "Vær" to "Weather",
            "Andre hendelser" to "Other incidents"
        )
    }

    suspend fun fetch(municipality: String, enabledCategories: Set<String>, limit: Int = 30): List<Incident> = withContext(Dispatchers.IO) {
        val query = buildString {
            append("$BASE_URL/messages?take=")
            append(limit.coerceIn(1, 100))
            if (municipality.isNotBlank()) {
                append("&municipality=")
                append(URLEncoder.encode(municipality.trim(), "UTF-8"))
            }
        }
        val body = runCatching { request(query) }.getOrElse {
            // The API is still evolving; if municipality filtering changes, fall back to a national pull and filter locally.
            request("$BASE_URL/messages?take=${limit.coerceIn(1, 100)}")
        }
        val root = JSONObject(body)
        val items = when {
            root.has("messages") -> root.optJSONArray("messages")
            root.has("messageThreads") -> root.optJSONArray("messageThreads")
            root.has("data") -> root.optJSONArray("data")
            root.has("items") -> root.optJSONArray("items")
            else -> null
        } ?: JSONArray()

        val raw = mutableListOf<Incident>()
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            val category = o.optString("category").ifBlank { o.optString("categoryName") }
            if (enabledCategories.isNotEmpty() && category !in enabledCategories) continue
            val itemMunicipality = o.optString("municipality")
            if (municipality.isNotBlank() && itemMunicipality.isNotBlank() && !itemMunicipality.equals(municipality.trim(), ignoreCase = true)) continue
            val text = o.optString("text").ifBlank { o.optString("description") }
            val id = o.optString("id").trim()
            if (id.isBlank()) continue
            val incidentUrl = sequenceOf(
                o.optString("url"),
                o.optString("messageUrl"),
                o.optString("link"),
                o.optString("href")
            ).firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: "https://www.politiet.no/politiloggen"
            raw += Incident(
                id = id,
                categoryNo = category,
                categoryEn = CATEGORY_TRANSLATIONS[category] ?: category,
                municipality = o.optString("municipality").ifBlank { municipality },
                area = o.optString("area"),
                createdMillis = parseMillis(o.optString("createdOn").ifBlank { o.optString("createdAt") }),
                norwegianText = text,
                englishText = text,
                isActive = o.optBoolean("isActive", true)
            )
        }
        translate(raw)
    }

    private suspend fun translate(items: List<Incident>): List<Incident> = coroutineScope {
        items.map { item ->
            async(Dispatchers.IO) {
                item.copy(englishText = translateNoToEn(item.norwegianText) ?: item.norwegianText)
            }
        }.awaitAll()
    }

    /** Free online translation fallback. The original Norwegian is retained if translation fails. */
    private fun translateNoToEn(text: String): String? {
        if (text.isBlank()) return null
        val q = URLEncoder.encode(text.take(4500), "UTF-8")
        runCatching {
            val json = JSONObject(request("https://api.mymemory.translated.net/get?q=$q&langpair=no|en"))
            json.optJSONObject("responseData")?.optString("translatedText")?.takeIf { it.isNotBlank() }
        }.getOrNull()?.let { return it }
        // Secondary online fallback; if both services fail the Norwegian source text is retained.
        return runCatching {
            val json = JSONArray(request("https://translate.googleapis.com/translate_a/single?client=gtx&sl=no&tl=en&dt=t&q=$q"))
            val first = json.optJSONArray(0)?.optJSONArray(0)?.optString(0)
            first?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun request(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "TheBrief/1.2 Android")
            .header("Accept", "application/json")
            .build()
        return client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("empty response")
        }
    }

    private fun parseMillis(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrElse { System.currentTimeMillis() }
}
