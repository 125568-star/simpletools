package com.myapps.mydictation;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Uses the speech provider's normal live microphone input.
 * This deliberately does NOT inject previously recorded PCM: Android
 * permits recognition providers to ignore EXTRA_AUDIO_SOURCE.
 */
final class LiveTranscriber {
    interface Callback {
        void partial(String text);
        void success(String text);
        void failure(String reason);
    }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private Callback callback;
    private final StringBuilder completed = new StringBuilder();
    private String partial = "";
    private boolean started, sessionActive, finishing, delivered, closed;
    private int consecutiveFailures;
    private final Runnable restart = this::startSession;
    private final Runnable finishDeadline = this::deliver;

    void start(Context context, Callback cb) {
        if (Looper.myLooper() != Looper.getMainLooper())
            throw new IllegalStateException("Speech recognizer must run on the main thread");
        callback = cb;
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            delivered = true;
            callback.failure("No Android speech service is enabled. Enable Google or Samsung speech recognition.");
            return;
        }
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle b) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rms) {}
                @Override public void onBufferReceived(byte[] data) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onEvent(int type, Bundle b) {}
                @Override public void onPartialResults(Bundle b) {
                    if (closed || delivered) return;
                    partial = best(b);
                    if (!partial.isEmpty()) callback.partial(partial);
                }
                @Override public void onResults(Bundle b) {
                    if (closed || delivered) return;
                    sessionActive = false;
                    String finalText = best(b);
                    if (!finalText.isEmpty()) {
                        append(finalText);
                        consecutiveFailures = 0;
                    }
                    partial = "";
                    if (finishing) deliver();
                    else scheduleRestart(350);
                }
                @Override public void onError(int error) {
                    if (closed || delivered) return;
                    sessionActive = false;
                    if (finishing) {
                        // If the provider did not supply final results, retain
                        // the latest visible partial result rather than lose it.
                        deliver();
                        return;
                    }
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        fail("Microphone permission is missing.");
                        return;
                    }
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        consecutiveFailures = 0;
                        partial = "";
                        scheduleRestart(450);
                        return;
                    }
                    if (++consecutiveFailures <= 3) {
                        partial = "";
                        scheduleRestart(900);
                    } else {
                        fail("Speech recognition service error " + error +
                             ". Check that the phone's speech recognition service is enabled.");
                    }
                }
            });
            started = true;
            startSession();
        } catch (Exception e) {
            fail("Cannot start live speech recognition: " + e.getMessage());
        }
    }
    private static String best(Bundle b) {
        if (b == null) return "";
        ArrayList<String> words = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return words == null || words.isEmpty() || words.get(0) == null ? "" : words.get(0).trim();
    }
    private void append(String s) {
        if (s == null || s.trim().isEmpty()) return;
        if (completed.length() > 0) completed.append(" ");
        completed.append(s.trim());
    }
    private void startSession() {
        if (!started || closed || finishing || delivered) return;
        try {
            partial = "";
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            // No EXTRA_AUDIO_SOURCE: the provider opens the live microphone.
            sessionActive = true;
            recognizer.startListening(i);
        } catch (Exception e) {
            sessionActive = false;
            if (++consecutiveFailures > 3) fail("Cannot restart microphone recognition: " + e.getMessage());
            else scheduleRestart(1000);
        }
    }
    private void scheduleRestart(long delay) {
        handler.removeCallbacks(restart);
        if (!closed && !finishing && !delivered) handler.postDelayed(restart, delay);
    }
    void stop() {
        if (closed || delivered || finishing) return;
        finishing = true;
        handler.removeCallbacks(restart);
        if (sessionActive && recognizer != null) {
            try {
                recognizer.stopListening();
                handler.postDelayed(finishDeadline, 6000);
            } catch (Exception e) {
                deliver();
            }
        } else deliver();
    }
    String snapshot() {
        if (completed.length() == 0) return partial.trim();
        if (partial.isEmpty()) return completed.toString().trim();
        return completed + " " + partial.trim();
    }
    private void deliver() {
        if (closed || delivered) return;
        delivered = true;
        handler.removeCallbacks(finishDeadline);
        if (!partial.isEmpty()) append(partial);
        String text = completed.toString().trim();
        close();
        if (callback != null) {
            if (text.isEmpty()) callback.failure("No speech was recognised. Try again and speak clearly.");
            else callback.success(text);
        }
    }
    private void fail(String reason) {
        if (closed || delivered) return;
        delivered = true;
        String captured = snapshot();
        close();
        if (callback != null) {
            if (!captured.isEmpty()) callback.success(captured);
            else callback.failure(reason);
        }
    }
    void close() {
        if (closed) return;
        closed = true;
        handler.removeCallbacks(restart);
        handler.removeCallbacks(finishDeadline);
        try { if (recognizer != null) { recognizer.cancel(); recognizer.destroy(); } }
        catch (Exception ignored) {}
        recognizer = null;
    }
}
