package com.arulsundaresan.arulremindme.voice

import com.arulsundaresan.arulremindme.domain.model.RepeatMode
import com.arulsundaresan.arulremindme.nlp.MissingInfo
import com.arulsundaresan.arulremindme.nlp.ParsedReminderInput
import com.arulsundaresan.arulremindme.nlp.ParserResult
import com.arulsundaresan.arulremindme.nlp.ReminderParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Tests the speech → parser boundary, which is the only place voice touches the rest of the
 * app.
 *
 * There are deliberately **no** fake `SpeechRecognizer` tests here. The recogniser is an
 * Android system service that does not exist on the JVM; a mock of it would only assert
 * that the mock works. What matters — and what is tested — is that a transcript produces
 * exactly the same reminder as typing the same sentence.
 */
class VoiceTranscriptProcessorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    /** Wednesday, 12 August 2026 — same fixed clock as the Session 2 parser tests. */
    private val clock: Clock = Clock.fixed(Instant.parse("2026-08-12T03:30:00Z"), zone)
    private val parser = ReminderParser(clock)
    private val processor = VoiceTranscriptProcessor(parser)

    private fun parsedOf(result: ParserResult): ParsedReminderInput? = when (result) {
        is ParserResult.Complete -> result.parsed
        is ParserResult.Incomplete -> result.parsed
        is ParserResult.Failure -> null
    }

    private fun spoken(transcript: String): ParsedReminderInput {
        val outcome = processor.process(transcript)
        assertTrue("expected a parse, got $outcome", outcome is VoiceParseOutcome.Parsed)
        return parsedOf((outcome as VoiceParseOutcome.Parsed).result)
            ?: error("no parsed input for '$transcript'")
    }

    /** The core guarantee: speech takes the same path as typing, with the same result. */
    private fun assertSameAsTyped(transcript: String) {
        val voice = processor.process(transcript) as VoiceParseOutcome.Parsed
        val typed = parser.parse(transcript)

        assertEquals(typed::class, voice.result::class)
        assertEquals(parsedOf(typed)?.reminderText, parsedOf(voice.result)?.reminderText)
        assertEquals(parsedOf(typed)?.date, parsedOf(voice.result)?.date)
        assertEquals(parsedOf(typed)?.time, parsedOf(voice.result)?.time)
        assertEquals(parsedOf(typed)?.repeatMode, parsedOf(voice.result)?.repeatMode)
    }

    // ---- the four transcripts from the spec ---------------------------------

    @Test
    fun `tamil transcript produces the same reminder as typing it`() {
        val transcript = "நாளைக்கு காலை 8 மணிக்கு EB bill கட்டணும்"
        assertSameAsTyped(transcript)

        val parsed = spoken(transcript)
        assertEquals("EB bill கட்டணும்", parsed.reminderText)
        assertEquals(LocalDate.of(2026, 8, 13), parsed.date)
        assertEquals(LocalTime.of(8, 0), parsed.time)
        assertEquals(transcript, parsed.originalInput)
    }

    @Test
    fun `english transcript produces the same reminder as typing it`() {
        val transcript = "Tomorrow at 8 AM pay EB bill"
        assertSameAsTyped(transcript)

        val parsed = spoken(transcript)
        assertEquals("pay EB bill", parsed.reminderText)
        assertEquals(LocalDate.of(2026, 8, 13), parsed.date)
        assertEquals(LocalTime.of(8, 0), parsed.time)
    }

    @Test
    fun `tamil weekday transcript produces the same reminder as typing it`() {
        val transcript = "வரும் சனிக்கிழமை மாலை 5.30 மணிக்கு meeting"
        assertSameAsTyped(transcript)

        val parsed = spoken(transcript)
        assertEquals("meeting", parsed.reminderText)
        assertEquals(LocalDate.of(2026, 8, 15), parsed.date)
        assertEquals(LocalTime.of(17, 30), parsed.time)
    }

    @Test
    fun `tanglish transcript produces the same reminder as typing it`() {
        val transcript = "Naalaikku morning 8 manikku EB bill katta num"
        assertSameAsTyped(transcript)

        val parsed = spoken(transcript)
        assertEquals("EB bill katta num", parsed.reminderText)
        assertEquals(LocalDate.of(2026, 8, 13), parsed.date)
        assertEquals(LocalTime.of(8, 0), parsed.time)
    }

    // ---- transcript tidying -------------------------------------------------

    @Test
    fun `dictation artefacts are cleaned without changing the words`() {
        assertEquals(
            "Tomorrow at 8 AM pay EB bill",
            processor.normalise("Tomorrow at 8 AM pay EB bill.")
        )
        assertEquals(
            "Tomorrow at 8 AM pay EB bill",
            processor.normalise("  Tomorrow   at  8 AM   pay EB bill ")
        )
        assertEquals("Tomorrow at 8 am pay EB bill", processor.normalise("Tomorrow at 8 a.m. pay EB bill"))
        assertEquals("Meeting at 5 pm", processor.normalise("Meeting at 5 P. M."))
    }

    @Test
    fun `tamil text is never rewritten by normalisation`() {
        val tamil = "நாளைக்கு காலை 8 மணிக்கு EB bill கட்டணும்"
        assertEquals(tamil, processor.normalise(tamil))
    }

    @Test
    fun `a dictated a m still resolves to eight in the morning`() {
        assertEquals(LocalTime.of(8, 0), spoken("Tomorrow at 8 a.m. pay EB bill").time)
    }

    // ---- failure handling ---------------------------------------------------

    @Test
    fun `an empty or blank transcript is reported rather than parsed`() {
        assertEquals(VoiceParseOutcome.EmptyTranscript, processor.process(null))
        assertEquals(VoiceParseOutcome.EmptyTranscript, processor.process("   "))
        assertEquals(VoiceParseOutcome.EmptyTranscript, processor.process("..."))
    }

    @Test
    fun `misheard speech does not crash and does not become a reminder`() {
        val outcome = processor.process("asdfgh qwerty")

        assertTrue(outcome is VoiceParseOutcome.Parsed)
        assertTrue((outcome as VoiceParseOutcome.Parsed).result is ParserResult.Incomplete)
    }

    // ---- voice inherits Session 2 and Session 4 behaviour -------------------

    @Test
    fun `voice never guesses AM or PM either`() {
        val outcome = processor.process("Naalaikku 5 manikku EB bill") as VoiceParseOutcome.Parsed

        assertEquals(
            MissingInfo.TIME_MERIDIEM,
            (outcome.result as ParserResult.Incomplete).missing
        )
    }

    @Test
    fun `spoken recurring phrases still set the repeat mode`() {
        assertEquals(RepeatMode.DAILY, spoken("Every day at 8 AM pay EB bill").repeatMode)
        assertEquals(
            RepeatMode.DAILY,
            spoken("தினமும் காலை 8 மணிக்கு EB bill கட்டணும்").repeatMode
        )
    }

    // ---- language selection -------------------------------------------------

    @Test
    fun `language tags match the requested locales`() {
        assertEquals("ta-IN", VoiceLanguage.TAMIL.languageTag())
        assertEquals("en-IN", VoiceLanguage.ENGLISH.languageTag())
    }

    /**
     * AUTO sends no language extra, which is how the platform is told to use the device
     * default. It is not a bilingual mode — Android recognises one language per session.
     */
    @Test
    fun `auto sends no explicit language tag`() {
        assertNull(VoiceLanguage.AUTO.languageTag())
    }

    @Test
    fun `language survives a settings round trip`() {
        assertEquals(VoiceLanguage.TAMIL, VoiceLanguage.fromSettings("ta"))
        assertEquals(VoiceLanguage.ENGLISH, VoiceLanguage.fromSettings("en"))
        assertEquals(VoiceLanguage.AUTO, VoiceLanguage.fromSettings("klingon"))
        assertEquals(VoiceLanguage.AUTO, VoiceLanguage.fromSettings(null))
    }
}
