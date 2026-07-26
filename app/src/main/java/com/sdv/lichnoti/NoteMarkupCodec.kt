package com.sdv.lichnoti

import android.text.Spanned
import androidx.core.text.HtmlCompat

object NoteMarkupCodec {
    fun encode(text: Spanned): String {
        return HtmlCompat.toHtml(text, HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
    }

    fun decode(html: String): Spanned {
        if (html.isBlank()) return HtmlCompat.fromHtml("", HtmlCompat.FROM_HTML_MODE_LEGACY)
        return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}
