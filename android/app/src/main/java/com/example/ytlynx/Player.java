package com.example.ytlynx;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// Pleyerning yagona markazi. Faqat main thread'da ishlaydi.
//
// Holat YouTube sahifasidan keladi (yt-bridge.js → YtWebView → onPage) va uch joyga tarqaladi:
//   Lynx UI (Listener = YtWebViewUI), ekran qulfi va naushnik (MediaSession),
//   bildirishnoma (PlaybackService).
// Buyruqlar ham uch joydan keladi: Lynx tugmalari (PlayerModule), ekran qulfi/naushnik
// (MediaSession.Callback) va bildirishnoma tugmalari (PlaybackService). Hammasi command() ga tushadi.
final class Player {
    interface Listener {
        void onState(Player player);

        // telefonning "orqaga" tugmasi: true — o'zim hal qildim
        boolean onBack();
    }

    static final String ACTION_EXIT = "exit";
    private static final String TAG = MainActivity.TAG;
    private static final long ACTIONS = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
            | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
            | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO
            | PlaybackState.ACTION_STOP;

    private static Player sInstance;

    static void init(Context context) {
        if (sInstance == null) sInstance = new Player(context.getApplicationContext());
    }

    static Player get() {
        return sInstance;
    }

    // --- holat (PlaybackService bildirishnomani shulardan quradi) ---
    String url = "";
    boolean canGoBack;
    String videoId = "";
    String title = "";
    String channel = "";
    boolean playing;
    double position; // soniya
    double duration; // soniya, jonli efirda 0
    Bitmap art;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private YtWebView web;
    private Listener listener;
    private Runnable exitHandler;
    private String pendingUrl;
    private MediaSession session;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    // uxlash taymeri: SystemClock.elapsedRealtime() bo'yicha, 0 = o'chiq
    private long sleepAt;
    private final Runnable sleepTask = this::checkSleep;

    // ekran qulfiga oxirgi marta nima yuborilgan (har soniya yangilamaslik uchun)
    private String sessionKey = "";
    private double sessionPos;
    private boolean sessionPlaying;
    private long sessionAt;

    private Player(Context app) {
        this.app = app;
    }

    // ---------------------------------------------------------------- ulanish

    void attach(YtWebView view, Listener l) {
        web = view;
        listener = l;
    }

    // Element yo'q qilindi (Activity yopildi) — hammasini to'xtatamiz va holatni tozalaymiz
    void detach(YtWebView view) {
        if (web != view) return;
        web = null;
        listener = null;
        stopAll();
        url = videoId = title = channel = "";
        canGoBack = playing = false;
        position = duration = 0;
        art = null;
        sessionKey = "";
    }

    void setExitHandler(Runnable handler) {
        exitHandler = handler;
    }

    void clearExitHandler(Runnable handler) {
        if (exitHandler == handler) exitHandler = null;
    }

    // ---------------------------------------------------------------- sahifadan keladigan holat

    // yt-bridge.js: {"id","title","channel","playing","pos","dur"}
    void onPage(String json) {
        JSONObject m;
        try {
            m = new JSONObject(json);
        } catch (JSONException e) {
            return;
        }
        String id = m.optString("id", "");
        if (!id.equals(videoId)) {
            art = null;
            if (!id.isEmpty()) loadArt(id);
        }
        videoId = id;
        title = m.optString("title", "");
        channel = m.optString("channel", "");
        playing = m.optBoolean("playing", false);
        position = finite(m.optDouble("pos", 0));
        duration = finite(m.optDouble("dur", 0));
        checkSleep();
        publish();
    }

    void onNavigation(String newUrl, boolean back) {
        url = newUrl;
        canGoBack = back;
        if (listener != null) listener.onState(this);
    }

    private static double finite(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) || v < 0 ? 0 : v;
    }

    // ---------------------------------------------------------------- buyruqlar

    // play, pause, toggle, next, prev, seek (soniya), skip (+/- soniya), sleep (daqiqa), back, exit
    void command(String action, double value) {
        switch (action) {
            case "sleep":
                setSleep(value);
                return;
            case "back":
                if (web != null && web.canGoBack()) web.goBack();
                return;
            case ACTION_EXIT:
                exit();
                return;
            default:
                if (web != null) web.command(action, value);
        }
    }

    void load(String newUrl) {
        if (web != null) web.loadUrl(newUrl);
    }

    // Tashqaridan kelgan havola (Ulashish yoki "YT Lynx'da ochish"). WebView hali yaratilmagan
    // bo'lsa (Lynx endi chizyapti), YtWebViewUI uni birinchi sahifa sifatida oladi.
    void open(String newUrl) {
        if (web != null) {
            web.loadUrl(newUrl);
        } else {
            pendingUrl = newUrl;
        }
    }

    String takePendingUrl(String fallback) {
        String result = pendingUrl != null ? pendingUrl : fallback;
        pendingUrl = null;
        return result;
    }

    boolean handleBack() {
        if (web == null) return false;
        if (web.exitFullscreen()) return true;
        if (listener != null && listener.onBack()) return true;
        if (web.canGoBack()) {
            web.goBack();
            return true;
        }
        return false;
    }

    // Bildirishnomadagi yoki ekran qulfidagi "Chiqish"
    void exit() {
        if (web != null) web.command("pause", 0);
        stopAll();
        if (exitHandler != null) exitHandler.run();
    }

    private void stopAll() {
        main.removeCallbacks(sleepTask);
        sleepAt = 0;
        locks(false);
        if (session != null) session.setActive(false);
        PlaybackService.stop(app);
    }

    // ---------------------------------------------------------------- uxlash taymeri

    private void setSleep(double minutes) {
        main.removeCallbacks(sleepTask);
        sleepAt = minutes > 0 ? SystemClock.elapsedRealtime() + (long) (minutes * 60_000) : 0;
        if (sleepAt > 0) main.postDelayed(sleepTask, (long) (minutes * 60_000));
        if (listener != null) listener.onState(this);
    }

    // Handler protsessor uxlaganda kechikishi mumkin, shuning uchun har holat kelganda ham tekshiramiz
    private void checkSleep() {
        if (sleepAt == 0) return;
        long left = sleepAt - SystemClock.elapsedRealtime();
        if (left > 0) {
            main.removeCallbacks(sleepTask);
            main.postDelayed(sleepTask, left);
            return;
        }
        sleepAt = 0;
        Log.i(TAG, "uxlash taymeri: pauza");
        if (web != null) web.command("pause", 0);
        if (listener != null) listener.onState(this);
    }

    int sleepLeftSeconds() {
        if (sleepAt == 0) return 0;
        return (int) Math.max(0, (sleepAt - SystemClock.elapsedRealtime() + 999) / 1000);
    }

    // ---------------------------------------------------------------- tarqatish

    private void publish() {
        // 1) Lynx UI — har safar (progress chizig'i uchun)
        if (listener != null) listener.onState(this);

        // 2) ekran qulfi va bildirishnoma — faqat muhim o'zgarishda.
        // Pozitsiyani tizim o'zi hisoblab boradi; biz faqat sakrash (seek) bo'lsa yuboramiz.
        String key = videoId + '|' + title + '|' + channel + '|' + (long) duration + '|' + playing
                + '|' + (art != null);
        double expected = sessionPos;
        if (sessionPlaying) expected += (SystemClock.elapsedRealtime() - sessionAt) / 1000.0;
        boolean jumped = Math.abs(position - expected) > 2;
        if (key.equals(sessionKey) && !jumped) return;
        sessionKey = key;
        sessionPos = position;
        sessionPlaying = playing;
        sessionAt = SystemClock.elapsedRealtime();

        if (videoId.isEmpty()) {
            locks(false);
            return;
        }
        updateSession();
        locks(playing);
        // Servisni faqat ijro boshlanganda ishga tushiramiz (bu paytda ilova odatda ochiq turadi).
        // Pauzada ham to'xtatmaymiz — ekran qulfidagi ▶ tugmasi ishlashi uchun.
        if (playing) {
            PlaybackService.show(app);
        } else {
            PlaybackService.refresh();
        }
    }

    MediaSession session() {
        if (session == null) {
            session = new MediaSession(app, "YtLynx");
            // Android 7 (API 24-25) uchun kerak, yangilarida e'tiborga olinmaydi
            session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                    | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            session.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    command("play", 0);
                }

                @Override
                public void onPause() {
                    command("pause", 0);
                }

                @Override
                public void onStop() {
                    command("pause", 0);
                }

                @Override
                public void onSkipToNext() {
                    command("next", 0);
                }

                @Override
                public void onSkipToPrevious() {
                    command("prev", 0);
                }

                @Override
                public void onSeekTo(long pos) {
                    command("seek", pos / 1000.0);
                }

                @Override
                public void onCustomAction(String action, Bundle extras) {
                    if (ACTION_EXIT.equals(action)) exit();
                }
            }, main);
            Intent open = new Intent(app, MainActivity.class);
            session.setSessionActivity(PendingIntent.getActivity(app, 0, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        return session;
    }

    private void updateSession() {
        MediaSession s = session();
        s.setPlaybackState(new PlaybackState.Builder()
                .setActions(ACTIONS)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        (long) (position * 1000), playing ? 1f : 0f)
                // Android 13+ ekran qulfida bildirishnoma tugmalari emas, shular ko'rinadi
                .addCustomAction(new PlaybackState.CustomAction.Builder(
                        ACTION_EXIT, app.getString(R.string.exit), R.drawable.ic_close).build())
                .build());
        MediaMetadata.Builder meta = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title.isEmpty() ? "YouTube" : title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, channel);
        if (duration > 0) meta.putLong(MediaMetadata.METADATA_KEY_DURATION, (long) (duration * 1000));
        if (art != null) meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        s.setMetadata(meta.build());
        if (!s.isActive()) s.setActive(true);
    }

    // Ekran qulfi uchun muqova (16:9)
    private void loadArt(String id) {
        new Thread(() -> {
            Bitmap bitmap = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL("https://i.ytimg.com/vi/" + id + "/mqdefault.jpg")
                        .openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                try (InputStream in = conn.getInputStream()) {
                    bitmap = BitmapFactory.decodeStream(in);
                }
            } catch (Exception e) {
                Log.w(TAG, "muqova yuklanmadi: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
            Bitmap result = bitmap;
            main.post(() -> {
                if (result != null && id.equals(videoId)) {
                    art = result;
                    publish();
                }
            });
        }).start();
    }

    // Ijro paytida protsessor va Wi-Fi uxlamasin; pauzada qo'yib yuboramiz (batareya)
    @SuppressWarnings("deprecation")
    private void locks(boolean on) {
        if (on) {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YtLynx:playback");
                wakeLock.setReferenceCounted(false);
            }
            if (wifiLock == null) {
                WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "YtLynx:wifi");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (!wakeLock.isHeld()) wakeLock.acquire();
            if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        }
    }
}
