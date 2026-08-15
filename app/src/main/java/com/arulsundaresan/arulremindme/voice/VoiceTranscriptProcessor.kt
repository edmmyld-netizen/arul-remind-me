package com.arulsundaresan.arulremindme.voice

import com.arulsundaresan.arulremindme.nlp.ParserResult
import com.arulsundaresan.arulremindme.nlp.ReminderParser

/**
 * The seam between speech recognition and the Session 2 parser.
 *
 * This is the *only* thing that sits between a transcript and [ReminderParser]. There is no
 * second date/time parser and no voice-specific NLP: a spoken reminder and a typed one take
 * exactly the same path from here on, which is what makes the voice tests meaningful.
 *
 * Pure Kotlin, so the whole speech-to-parser boundary is covered by ordinary JVM tests.
 */
class VoiceTranscriptProcessor(private val parser: ReminderParser) {

    /**
     * Normalises a raw transcript, then parses it.
     *
     * @return null when the transcript carries nothing usable, so the caller can show the
     *   "couldn't understand" state instead of a confusing empty parse.
     */
    fun process(rawTranscript: String?): VoiceParseOutcome {
        val cleaned = normalise(rawTranscript)
        if (cleaned.isBlank()) return VoiceParseOutcome.EmptyTranscript
        return VoiceParseOutcome.Parsed(cleaned, parser.parse(cleaned))
    }

    /**
     * Speech recognisers return unpunctuated text with inconsistent spacing, and some
     * dictate "a.m." / "p. m." rather than "am". The existing parser already copes with
     * most of it; this only tidies what would otherwise break a regex boundary.
     *
     * Deliberately conservative: it never reorders or rewrites words, so what the user sees
     * on the "நீங்கள் சொன்னது" screen is what the parser actually received.
     */
    fun normalise(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var text = raw.trim()
        text = MERIDIEM_DOTS.replace(text) { match ->
            match.groupValues[1].lowercase() + match.groupValues[2].lowercase()
        }
        text = TRAILING_PUNCTUATION.replace(text, "")
        text = WHITESPACE.replace(text, " ")
        return text.trim()
    }

    private companion object {
        /** "a.m." / "P. M." -> "am" / "pm" */
        val MERIDIEM_DOTS = Regex("\\b([ap])\\.\\s?(m)\\.?", RegexOption.IGNORE_CASE)

        /** Recognisers sometimes append a full stop or question mark. */
        val TRAILING_PUNCTUATION = Regex("[.!?。]+\\s*$")

        val WHITESPACE = Regex("\\s+")
    }
}

/** What came back from one voice attempt. */
sealed interface VoiceParseOutcome {

    /** The recogniser returned nothing usable. */
    data object EmptyTranscript : VoiceParseOutcome

    /**
     * @param transcript the cleaned text — exactly what was shown to the user and handed
     *   to the parser.
     */
    data class Parsed(val transcript: String, val result: ParserResult) : VoiceParseOutcome
}
