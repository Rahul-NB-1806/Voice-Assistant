package com.example.voicecallapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.Intent;
import android.database.Cursor;
import android.media.*;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.util.*;

public class VoiceService extends Service {

    /* ================= CONSTANTS ================= */

    private static final String TAG = "VoiceService";
    private static final String CHANNEL_ID = "voice_service_channel";

    private static final String[] WAKE_WORDS = {"hitler", "hey hitler"};
    private static final Set<String> COMMANDS =
            new HashSet<>(Collections.singletonList("call"));

    private static final long COMMAND_TIMEOUT = 5000;

    enum Mode { WAKE, COMMAND, CONTACT }

    /* ================= STATE ================= */

    private Mode currentMode = Mode.WAKE;
    private String activeCommand = null;
    private long stageStartTime = 0;

    private boolean isListening = false;
    private boolean isSpeaking = false;
    private boolean shuttingDown = false;

    /* ================= AUDIO ================= */

    private Model model;
    private Recognizer recognizer;
    private AudioRecord audioRecord;
    private TextToSpeech tts;

    /* ================= CONTACT CACHE ================= */

    private final Map<String, String> contactCache = new HashMap<>();
    private final Set<String> favoriteContacts = new HashSet<>();

    /* ================= LIFECYCLE ================= */

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, createNotification());
        initTTS();
        loadContactsIntoCache();
        loadModel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopEverything();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /* ================= INIT ================= */

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                tts.setPitch(0.7f);
                tts.setSpeechRate(0.95f);
            }
        });
    }

    private void loadModel() {
        StorageService.unpack(
                getApplicationContext(),
                "vosk-model",
                "vosk-model",
                m -> {
                    try {
                        model = m;
                        recognizer = new Recognizer(model, 16000.0f);
                        startListening();
                    } catch (IOException e) {
                        Log.e(TAG, "Recognizer init failed", e);
                    }
                },
                e -> Log.e(TAG, "Model load failed", e)
        );
    }

    /* ================= CONTACT CACHE ================= */

    private void loadContactsIntoCache() {
        Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                null,
                null,
                null
        );

        if (cursor == null) return;

        while (cursor.moveToNext()) {
            String name = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    )
            ).toLowerCase();

            String number = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
            );

            contactCache.put(name, number);

            int starred = cursor.getInt(
                    cursor.getColumnIndexOrThrow(
                            ContactsContract.Contacts.STARRED
                    )
            );

            if (starred == 1) {
                favoriteContacts.add(name);
            }
        }
        cursor.close();
    }

    /* ================= LISTEN ================= */

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private void startListening() {

        int bufferSize = AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
        );

        audioRecord.startRecording();
        isListening = true;

        new Thread(() -> {
            byte[] buffer = new byte[bufferSize];

            while (isListening && !isSpeaking) {
                int read = audioRecord.read(buffer, 0, buffer.length);

                if (read > 0 && recognizer.acceptWaveForm(buffer, read)) {

                    String text = extractText(recognizer.getResult());
                    Log.d(TAG, "Speech: " + text);

                    if (isStopCommand(text)) {
                        shutdownWithVoice();
                        return;
                    }

                    switch (currentMode) {

                        case WAKE:
                            if (isWakeWord(text)) {
                                speak("I am listening");
                                currentMode = Mode.COMMAND;
                                stageStartTime = System.currentTimeMillis();
                            }
                            break;

                        case COMMAND:
                            if (System.currentTimeMillis() - stageStartTime > COMMAND_TIMEOUT) {
                                resetToWake();
                                break;
                            }

                            for (String cmd : COMMANDS) {
                                if (text.contains(cmd)) {
                                    activeCommand = cmd;
                                    currentMode = Mode.CONTACT;
                                    stageStartTime = System.currentTimeMillis();
                                    break;
                                }
                            }
                            break;

                        case CONTACT:
                            if (System.currentTimeMillis() - stageStartTime > COMMAND_TIMEOUT) {
                                resetToWake();
                                break;
                            }

                            if ("call".equals(activeCommand)) {
                                handleCall(text);
                            }
                            break;
                    }
                }
            }
        }).start();
    }

    /* ================= LOGIC ================= */

    private void handleCall(String spokenName) {
        spokenName = spokenName.toLowerCase().trim();

        String number = findBestMatch(spokenName);

        if (number != null) {
            openDialer(number);
        } else {
            speak("Contact not found");
        }

        resetToWake();
    }

    private String findBestMatch(String spoken) {

        // 1️⃣ Favorites exact
        for (String fav : favoriteContacts) {
            if (fav.equals(spoken)) return contactCache.get(fav);
        }

        // 2️⃣ Exact match
        if (contactCache.containsKey(spoken)) {
            return contactCache.get(spoken);
        }

        // 3️⃣ Starts with
        for (String name : contactCache.keySet()) {
            if (name.startsWith(spoken)) return contactCache.get(name);
        }

        // 4️⃣ Similarity
        int best = 0;
        String bestNumber = null;

        for (String name : contactCache.keySet()) {
            int score = similarity(spoken, name);
            if (score > best) {
                best = score;
                bestNumber = contactCache.get(name);
            }
        }

        return best >= 3 ? bestNumber : null;
    }

    /* ================= UTIL ================= */

    private void openDialer(String number) {
        Intent i = new Intent(Intent.ACTION_DIAL);
        i.setData(Uri.parse("tel:" + number));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void resetToWake() {
        activeCommand = null;
        currentMode = Mode.WAKE;
        recognizer.reset();
    }

    private boolean isWakeWord(String text) {
        for (String w : WAKE_WORDS)
            if (text.contains(w)) return true;
        return false;
    }

    private boolean isStopCommand(String text) {
        text = text.toLowerCase();
        return text.contains("stop") || text.contains("bye")
                || text.contains("leave") || text.contains("go away");
    }

    private int similarity(String a, String b) {
        int matches = 0;
        for (int i = 0; i < Math.min(a.length(), b.length()); i++)
            if (a.charAt(i) == b.charAt(i)) matches++;
        return matches;
    }

    private String extractText(String json) {
        try {
            int a = json.indexOf("\"", json.indexOf(":"));
            int b = json.lastIndexOf("\"");
            return (a >= 0 && b > a) ? json.substring(a + 1, b) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /* ================= SPEAK ================= */

    private void speak(String msg) {
        if (tts == null) return;

        isSpeaking = true;
        isListening = false;

        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "TTS");

        tts.setOnUtteranceProgressListener(
                new android.speech.tts.UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) {
                        isSpeaking = false;
                        if (shuttingDown) stopEverything();
                        else startListening();
                    }
                    @Override public void onError(String id) {
                        stopEverything();
                    }
                }
        );
    }

    private void shutdownWithVoice() {
        shuttingDown = true;
        speak("Bye, I am leaving");
    }

    private void stopEverything() {
        isListening = false;

        try {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
            }
        } catch (Exception ignored) {}

        if (recognizer != null) recognizer.close();
        if (model != null) model.close();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        stopForeground(true);
        stopSelf();
    }

    /* ================= NOTIFICATION ================= */

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice Call Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(ch);
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Hitler's calling")
                .setContentText("Hitler is listening 🇩🇪")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();
    }
}
