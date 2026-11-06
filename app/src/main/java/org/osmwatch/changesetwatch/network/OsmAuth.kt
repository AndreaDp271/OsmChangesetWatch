package org.osmwatch.changesetwatch.network

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Login OSM (OAuth2 Authorization Code + PKCE, client pubblico: nessun segreto nell'APK) e invio di
 * commenti. Metodi sincroni.
 */
object OsmAuth {
    /** Pubblico per natura: identifica l'app, non è un segreto. */
    const val CLIENT_ID = "OjX9N-clUyD8y8SknDnWgfg_VXoG8Vmd-vn2OOUaqQw"
    const val SCHEME = "io.github.andreadp271.osmwatch"
    const val REDIRECT_URI = "$SCHEME://oauth"
    private const val SCOPE = "write_changeset_comments"
    private const val SITE = "https://www.openstreetmap.org"

    private val b64 = Base64.getUrlEncoder().withoutPadding()

    /** Stringa casuale URL-safe (43 caratteri), per il code_verifier e lo state. */
    fun randomToken(): String =
        ByteArray(32).also { SecureRandom().nextBytes(it) }.let(b64::encodeToString)

    fun challenge(verifier: String): String =
        b64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        )

    fun authorizeUrl(challenge: String, state: String): String =
        okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("www.openstreetmap.org")
            .addPathSegments("oauth2/authorize")
            .addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", SCOPE)
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("state", state)
            .build()
            .toString()

    /** Scambia il codice di autorizzazione con l'access token. */
    fun exchange(http: OkHttpClient, code: String, verifier: String): String {
        val body =
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("client_id", CLIENT_ID)
                .add("code_verifier", verifier)
                .build()
        val req =
            Request.Builder()
                .url("$SITE/oauth2/token")
                .header("User-Agent", OsmConfig.USER_AGENT)
                .post(body)
                .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw OsmHttpException(r.code, "HTTP ${r.code}: ${text.take(200)}")
            return JSONObject(text).getString("access_token")
        }
    }

    /**
     * `POST /changeset/ID/comment`: il changeset deve essere chiuso. 401 = token non valido, 409 =
     * changeset aperto, 429 = troppi commenti.
     */
    fun postComment(http: OkHttpClient, token: String, changesetId: Long, text: String) {
        val req =
            Request.Builder()
                .url("${OsmConfig.BASE_URL}/changeset/$changesetId/comment")
                .header("User-Agent", OsmConfig.USER_AGENT)
                .header("Authorization", "Bearer $token")
                .post(FormBody.Builder().add("text", text).build())
                .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful)
                throw OsmHttpException(r.code, "HTTP ${r.code}: ${r.body?.string()?.take(200)}")
        }
    }
}
