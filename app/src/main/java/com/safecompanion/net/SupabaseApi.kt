package com.safecompanion.net

import com.safecompanion.BackendConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin, dependency-light client for Supabase (GoTrue auth + PostgREST).
 * All calls are synchronous — invoke from Dispatchers.IO.
 */
object SupabaseApi {

    private val json = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    class ApiException(val code: Int, message: String) : Exception(message)

    data class Session(
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
        val email: String
    )

    private fun baseUrl() = BackendConfig.SUPABASE_URL.trimEnd('/')

    // ---------------- Auth (GoTrue) ----------------

    fun signUp(email: String, password: String): Session =
        authCall(baseUrl() + "/auth/v1/signup",
            JSONObject().put("email", email).put("password", password))

    fun signIn(email: String, password: String): Session =
        authCall(baseUrl() + "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password))

    fun refresh(refreshToken: String): Session =
        authCall(baseUrl() + "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", refreshToken))

    private fun authCall(url: String, body: JSONObject): Session {
        val req = Request.Builder()
            .url(url)
            .header("apikey", BackendConfig.SUPABASE_ANON_KEY)
            .post(body.toString().toRequestBody(json))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, extractError(text))
            val o = JSONObject(text)
            val access = o.optString("access_token")
            if (access.isBlank()) throw ApiException(resp.code, extractError(text))
            val user = o.optJSONObject("user") ?: JSONObject()
            return Session(
                accessToken = access,
                refreshToken = o.optString("refresh_token"),
                userId = user.optString("id"),
                email = user.optString("email")
            )
        }
    }

    // ---------------- PostgREST: tables ----------------

    /** GET /rest/v1/{table}?{query}  → JSONArray of rows. */
    fun select(table: String, query: String, accessToken: String): JSONArray {
        val req = Request.Builder()
            .url(baseUrl() + "/rest/v1/" + table + "?" + query)
            .header("apikey", BackendConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, extractError(text))
            return JSONArray(text)
        }
    }

    /** POST a single row; returns the created row. */
    fun insert(table: String, row: JSONObject, accessToken: String): JSONObject {
        val req = Request.Builder()
            .url(baseUrl() + "/rest/v1/" + table)
            .header("apikey", BackendConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $accessToken")
            .header("Prefer", "return=representation")
            .post(row.toString().toRequestBody(json))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, extractError(text))
            val arr = JSONArray(text)
            return if (arr.length() > 0) arr.getJSONObject(0) else JSONObject()
        }
    }

    /** PATCH rows matched by {query}. */
    fun update(table: String, query: String, patch: JSONObject, accessToken: String) {
        val req = Request.Builder()
            .url(baseUrl() + "/rest/v1/" + table + "?" + query)
            .header("apikey", BackendConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $accessToken")
            .patch(patch.toString().toRequestBody(json))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw ApiException(resp.code, extractError(resp.body?.string().orEmpty()))
            }
        }
    }

    /** DELETE rows matched by {query}. */
    fun delete(table: String, query: String, accessToken: String) {
        val req = Request.Builder()
            .url(baseUrl() + "/rest/v1/" + table + "?" + query)
            .header("apikey", BackendConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $accessToken")
            .delete()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw ApiException(resp.code, extractError(resp.body?.string().orEmpty()))
            }
        }
    }

    // ---------------- PostgREST: RPC (device-facing, anon role) ----------------

    /** POST /rest/v1/rpc/{fn}. Uses the anon key as bearer unless a user token is given. */
    fun rpc(fn: String, args: JSONObject, accessToken: String? = null): JSONObject {
        val bearer = accessToken ?: BackendConfig.SUPABASE_ANON_KEY
        val req = Request.Builder()
            .url(baseUrl() + "/rest/v1/rpc/" + fn)
            .header("apikey", BackendConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $bearer")
            .post(args.toString().toRequestBody(json))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, extractError(text))
            return if (text.trim().startsWith("{")) JSONObject(text) else JSONObject()
        }
    }

    private fun extractError(body: String): String = try {
        val o = JSONObject(body)
        o.optString("msg").ifBlank { null }
            ?: o.optString("message").ifBlank { null }
            ?: o.optString("error_description").ifBlank { null }
            ?: o.optString("error").ifBlank { null }
            ?: body.take(200)
    } catch (_: Exception) {
        body.take(200)
    }
}
