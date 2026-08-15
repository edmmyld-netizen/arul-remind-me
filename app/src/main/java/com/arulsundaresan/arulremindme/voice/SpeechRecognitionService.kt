package com.arulsundaresan.arulremindme.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Everything the UI needs to know about one listening session. */
sealed interface VoiceRecognitionEvent {

    /** The recogniser is ready and the microphone is live. */
    data object Listening : VoiceRecognitionEvent

    /** Speech ended; the recogniser is working out what was said. */
    data object Processing : VoiceRecognitionEvent

    /** Interim text, shown while the user is still speaking. */
    data class PartialTranscript(val text: String) : VoiceRecognitionEvent

    /** Final text. The microphone is already released by this point. */
    data class Transcript(val text: String) : VoiceRecognitionEvent

    data class Failed(val error: VoiceError) : VoiceRecognitionEvent
}

/** Mapped from SpeechRecognizer's error codes so the UI never shows a raw int. */
enum class VoiceError {
    PERMISSION_DENIED,
    NO_MICROPHONE,
    RECOGNIZER_UNAVAILABLE,
    NETWORK_UNAVAILABLE,
    NO_SPEECH_HEARD,
    TIMEOUT,
    BUSY,
    EMPTY_RESULT,
    UNKNOWN
}

/**
 * Thin wrapper over Android's [SpeechRecognizer].
 *
 * Privacy properties this class is built to guarantee:
 * - the recogniser is created when [listen] is collected and destroyed when collection ends,
 *   so the microphone is live only for the duration of one explicit tap;
 * - no audio is written to disk, buffered or sent anywhere by this app — recognition is
 *   handled by the on-device/Play services recogniser the user already has;
 * - there is no service, no `START_STICKY`, and nothing that outlives the composable.
 */
class SpeechRecognitionService(private val context: Context) {

    fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    fun hasMicrophoneHardware(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    fun isRecognitionAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * True when the recogniser can run without a network connection. Only knowable from
     * Android 13, so older versions report false and the UI simply stays quiet about it.
     */
    fun supportsOnDeviceRecognition(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /**
     * One listening session. Cancelling collection (the user tapping Cancel, or the sheet
     * closing) stops the recogniser and releases the microphone immediately.
     */
    fun listen(language: VoiceLanguage): Flow<VoiceRecognitionEvent> = callbackFlow {
        if (!hasMicrophoneHardware()) {
            trySend(VoiceRecognitionEvent.Failed(VoiceError.NO_MICROPHONE))
            close()
            return@callbackFlow
        }
        if (!hasMicrophonePermission()) {
            trySend(VoiceRecognitionEvent.Failed(VoiceError.PERMISSION_DENIED))
            close()
            return@callbackFlow
        }
        if (!isRecognitionAvailable()) {
            trySend(VoiceRecognitionEvent.Failed(VoiceError.RECOGNIZER_UNAVAILABLE))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(VoiceRecognitionEvent.Listening)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                trySend(VoiceRecognitionEvent.Processing)
            }

            override fun onError(error: Int) {
                trySend(VoiceRecognitionEvent.Failed(error.toVoiceError()))
                close()
            }

            override fun onResults(results: Bundle?) {
                val best = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                if (best.isNullOrBlank()) {
                    trySend(VoiceRecognitionEvent.Failed(VoiceError.EMPTY_RESULT))
                } else {
                    trySend(VoiceRecognitionEvent.Transcript(best))
                }
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                    ?.let { trySend(VoiceRecognitionEvent.PartialTranscript(it)) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        recognizer.setRecognitionListener(listener)
        runCatching { recognizer.startListening(buildIntent(language)) }
            .onFailure {
                Log.e(TAG, "startListening failed", it)
                trySend(VoiceRecognitionEvent.Failed(VoiceError.UNKNOWN))
                close()
            }

        awaitClose {
            // Runs on cancel as well as on completion — this is what releases the mic.
            runCatching {
                recognizer.stopListening()
                recognizer.cancel()
                recognizer.destroy()
            }
        }
    }

    private fun buildIntent(language: VoiceLanguage): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

            // AUTO deliberately sends no language extra: that is how the platform is told
            // to use the device default. There is no bilingual mode to ask for.
            language.languageTag()?.let { tag ->
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
                // Lets the recogniser fall back to a related locale rather than failing.
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            }

            if (supportsOnDeviceRecognition()) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

    private companion object {
        const val TAG = "ArulSpeech"
    }
}

private fun Int.toVoiceError(): VoiceError = when (this) {
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.PERMISSION_DENIED
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        VoiceError.NETWORK_UNAVAILABLE

    SpeechRecognizer.ERROR_NO_MATCH -> VoiceError.NO_SPEECH_HEARD
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.TIMEOUT
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.BUSY
    SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_SERVER -> VoiceError.RECOGNIZER_UNAVAILABLE
    SpeechRecognizer.ERROR_AUDIO -> VoiceError.NO_MICROPHONE
    else -> VoiceError.UNKNOWN
}
