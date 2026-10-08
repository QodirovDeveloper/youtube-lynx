package com.example.ytlynx;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.lynx.tasm.LynxError;
import com.lynx.tasm.LynxView;
import com.lynx.tasm.LynxViewBuilder;
import com.lynx.tasm.LynxViewClient;
import com.lynx.tasm.behavior.Behavior;
import com.lynx.tasm.behavior.LynxContext;
import com.lynx.tasm.behavior.ui.LynxUI;
import com.lynx.xelement.XElementBehaviors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    static final String TAG = "YtLynx";
    private static final String BUNDLE = "main.lynx.bundle";

    private LynxView mLynxView;
    private TextView mErrorView;
    private View mErrorPanel;
    private final Runnable mExit = this::finishAndRemoveTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(24, 24, 29));
        getWindow().setNavigationBarColor(Color.rgb(24, 24, 29));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(15, 15, 18));
        setContentView(root);

        // Faqat jiddiy xato bo'lganda ko'rinadi: sababini ko'rsatadi, nusxa olish va yopish mumkin
        mErrorView = new TextView(this);
        mErrorView.setTextColor(Color.RED);
        mErrorView.setTextSize(12);
        mErrorView.setPadding(32, 16, 32, 32);
        mErrorView.setTextIsSelectable(true);
        ScrollView errorText = new ScrollView(this);
        errorText.addView(mErrorView);
        Button copy = new Button(this);
        copy.setText("Nusxa olish");
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("YT Lynx xato", mErrorView.getText()));
            Toast.makeText(this, "Nusxa olindi", Toast.LENGTH_SHORT).show();
        });
        Button close = new Button(this);
        close.setText("Yopish");
        LinearLayout buttons = new LinearLayout(this);
        buttons.setPadding(24, 72, 24, 0);
        buttons.addView(copy);
        buttons.addView(close);
        LinearLayout errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setBackgroundColor(Color.WHITE);
        errorPanel.addView(buttons);
        errorPanel.addView(errorText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        errorPanel.setVisibility(View.GONE);
        close.setOnClickListener(v -> errorPanel.setVisibility(View.GONE));
        mErrorPanel = errorPanel;

        if (MainApplication.initError != null) {
            showError("Lynx ishga tushmadi:\n" + MainApplication.initError);
        }

        try {
            byte[] template = readAsset(BUNDLE);
            if (template == null) {
                showError("assets/" + BUNDLE + " topilmadi");
            } else {
                LynxViewBuilder builder = new LynxViewBuilder();
                builder.addBehaviors(new ImageBehavior().create());
                // <input> va boshqa XElement teglari
                builder.addBehaviors(new XElementBehaviors().create());
                // bizning <yt-webview> tegimiz
                builder.addBehavior(new Behavior("yt-webview") {
                    @Override
                    public LynxUI createUI(LynxContext context) {
                        return new YtWebViewUI(context);
                    }
                });
                builder.setTemplateProvider(new AssetTemplateProvider(this));
                mLynxView = builder.build(this);
                mLynxView.addLynxViewClient(new LynxViewClient() {
                    @Override
                    public void onLoadFailed(String message) {
                        showError("Yuklashda xato: " + message);
                    }

                    @Override
                    public void onReceivedError(LynxError error) {
                        if (error == null) return;
                        Log.w(TAG, "Lynx error [" + error.getErrorCode() + "/" + error.getLevel()
                                + "]: " + error.getMsg());
                        if (error.isFatal()) {
                            showError("Lynx xatosi [" + error.getErrorCode() + "]:\n" + error.getMsg());
                        }
                    }
                });
                root.addView(mLynxView, matchParent());
                mLynxView.renderTemplateWithBaseUrl(template, new HashMap<>(), BUNDLE);
            }
        } catch (Throwable t) {
            showError(Log.getStackTraceString(t));
        }

        root.addView(mErrorPanel, matchParent());
        Player.get().setExitHandler(mExit);
        openLink(getIntent());
        askPermissions();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        openLink(intent);
    }

    // Boshqa ilovadan "Ulashish → YT Lynx" yoki YouTube havolasini "YT Lynx'da ochish"
    private void openLink(Intent intent) {
        if (intent == null) return;
        String text = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            text = intent.getData().toString();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            text = intent.getStringExtra(Intent.EXTRA_TEXT);
        }
        String url = youtubeUrl(text);
        if (url != null) Player.get().open(url);
    }

    // Matndan birinchi YouTube havolasini topib, mobil sayt manziliga aylantiradi
    static String youtubeUrl(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("https?://\\S+").matcher(text);
        while (m.find()) {
            Uri uri = Uri.parse(m.group());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("youtu.be")) {
                String id = uri.getLastPathSegment();
                if (id != null) return "https://m.youtube.com/watch?v=" + Uri.encode(id);
            } else if (host.equals("youtube.com") || host.endsWith(".youtube.com")) {
                return uri.buildUpon().scheme("https").authority("m.youtube.com").build().toString();
            }
        }
        return null;
    }

    // onPause/onStop'da hech narsani to'xtatmaymiz — aks holda ovoz to'xtaydi.

    @Override
    protected void onDestroy() {
        Player.get().clearExitHandler(mExit);
        if (mLynxView != null) {
            // <yt-webview> ham shu yerda yo'q qilinadi → Player servisni to'xtatadi
            mLynxView.destroy();
            mLynxView = null;
        }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!Player.get().handleBack()) {
            // ilovani yopmaymiz — orqa fonga o'tkazamiz, ovoz davom etadi
            moveTaskToBack(true);
        }
    }

    // Android 13+: bildirishnomaga ruxsat. Keyin bir marta: batareya tejashdan chiqarish
    // (Xiaomi va boshqalarda fonda ijro uzilmasligi uchun).
    private void askPermissions() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else {
            askBatteryOnce();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        askBatteryOnce();
    }

    @SuppressLint("BatteryLife")
    private void askBatteryOnce() {
        SharedPreferences prefs = getSharedPreferences("app", MODE_PRIVATE);
        if (prefs.getBoolean("batteryAsked", false)) return;
        prefs.edit().putBoolean("batteryAsked", true).apply();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            Log.w(TAG, "batareya sozlamasi ochilmadi: " + e);
        }
    }

    private void showError(String text) {
        Log.e(TAG, text);
        runOnUiThread(() -> {
            CharSequence old = mErrorView.getText();
            mErrorView.setText(old.length() == 0 ? text : old + "\n\n" + text);
            mErrorPanel.setVisibility(View.VISIBLE);
        });
    }

    private byte[] readAsset(String name) {
        try (InputStream in = getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) != -1) {
                out.write(buffer, 0, length);
            }
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
