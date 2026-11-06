package org.osmwatch.changesetwatch.network

import java.io.InputStream
import java.time.Instant
import org.osmwatch.changesetwatch.domain.UserBlock
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Risposta di `GET /user_blocks/ID`: date, chi ha bloccato e motivo (testo in Markdown). */
object BlockParser {
    private val ID = Regex("""/user_blocks/(\d+)""")

    fun parse(input: InputStream): UserBlock? {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        var block: UserBlock? = null
        var inReason = false
        val reason = StringBuilder()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG ->
                    when (p.name) {
                        "user_block" ->
                            block =
                                UserBlock(
                                    id = p.getAttributeValue(null, "id")?.toLongOrNull() ?: 0,
                                    createdAt = instant(p.getAttributeValue(null, "created_at")),
                                    endsAt = instant(p.getAttributeValue(null, "ends_at")),
                                    creator = null,
                                    reason = "",
                                )
                        "creator" ->
                            block = block?.copy(creator = p.getAttributeValue(null, "user"))
                        "reason" -> inReason = true
                    }
                XmlPullParser.TEXT -> if (inReason) reason.append(p.text)
                XmlPullParser.END_TAG -> if (p.name == "reason") inReason = false
            }
        }
        return block?.copy(reason = reason.toString().trim())
    }

    /**
     * Id dei blocchi elencati nella pagina web `/user/NOME/blocks`, dal più recente: l'API 0.6 non
     * ha un elenco dei blocchi ricevuti da un altro utente.
     */
    fun idsFromPage(html: String): List<Long> =
        ID.findAll(html).map { it.groupValues[1].toLong() }.distinct().toList()

    private fun instant(s: String?) = s?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
