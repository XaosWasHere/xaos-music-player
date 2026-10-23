package com.example.xaosmusicplayer.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Le lingue dell'app. Il nome si mostra sempre nella lingua stessa. */
enum class Language(val code: String, val nativeName: String, val locale: Locale) {
    IT("it", "Italiano", Locale.ITALIAN),
    EN("en", "English", Locale.ENGLISH),
    JA("ja", "日本語", Locale.JAPANESE);

    companion object {
        fun of(code: String?): Language? = entries.firstOrNull { it.code == code }

        /** La lingua del telefono, se è una di queste; altrimenti l'inglese. */
        fun system(): Language = of(Locale.getDefault().language) ?: EN
    }
}

/** La traduzione di una frase italiana. */
class Tr(val en: String, val ja: String)

/**
 * Le traduzioni dell'app.
 *
 * La frase italiana resta nel codice ed è la chiave: si cerca nei dizionari
 * (un file per zona dell'app, in questa cartella) e, se manca, resta
 * l'italiano invece di sparire. La lingua è uno stato di Compose: cambiandola,
 * tutto quello che la legge si ridisegna subito, senza riavviare.
 *
 * La scelta sta in SharedPreferences, come il tema: va letta prima del primo
 * frame, altrimenti l'app partirebbe in italiano e poi cambierebbe.
 */
object I18n {
    /** La lingua scelta; null = quella del telefono. */
    var choice: Language? by mutableStateOf(null)
        private set

    var language: Language by mutableStateOf(Language.system())
        private set

    @Volatile private var loaded = false

    /** Da chiamare all'avvio di activity, servizi e widget: legge la scelta salvata. */
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val saved = prefs(context).getString(KEY, null)
        choice = Language.of(saved)
        language = choice ?: Language.system()
    }

    fun setChoice(context: Context, lang: Language?) {
        choice = lang
        language = lang ?: Language.system()
        prefs(context).edit().putString(KEY, lang?.code).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val table: Map<String, Tr> by lazy {
        HashMap<String, Tr>().apply {
            ALL_STRINGS.forEach { putAll(it) }
        }
    }

    /** Come [translate], ma prima cerca "frase@contesto": la stessa frase italiana può avere due traduzioni. */
    fun translateIn(it: String, context: String, lang: Language = language): String {
        if (lang == Language.IT) return it
        val entry = table["$it@$context"] ?: return translate(it, lang)
        return if (lang == Language.EN) entry.en else entry.ja
    }

    fun translate(it: String, lang: Language = language): String = when (lang) {
        Language.IT -> it
        Language.EN -> table[it]?.en ?: it
        Language.JA -> table[it]?.ja ?: table[it]?.en ?: it
    }

    private const val PREFS_NAME = "xaos_language"
    private const val KEY = "language"
}

/** La frase nella lingua dell'app. */
fun t(it: String): String = I18n.translate(it)

/**
 * La frase con dei valori: `t("%d BRANI", n)`. Nelle traduzioni l'ordine si
 * può cambiare con `%1$s`, `%2$s`.
 */
fun t(it: String, vararg args: Any?): String = String.format(I18n.language.locale, I18n.translate(it), *args)

/**
 * Quando la stessa frase italiana vuol dire due cose: "ALBUM" come nome di una
 * sezione (in inglese ALBUMS) o come etichetta di un album solo. Nei dizionari
 * la voce con il contesto si scrive "ALBUM@sezione".
 */
fun tc(it: String, context: String): String = I18n.translateIn(it, context)

/**
 * Per le scritte con il carattere a matrice di punti (NDot): non ha kana né
 * kanji, quindi in giapponese restano in inglese.
 */
fun tDot(it: String): String =
    I18n.translate(it, if (I18n.language == Language.JA) Language.EN else I18n.language)

/** Come [tDot], con dei valori. */
fun tDot(it: String, vararg args: Any?): String = String.format(Locale.ENGLISH, tDot(it), *args)
