package com.example.ytlynx;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// YouTube sahifasini ochadigan WebView. Uchta vazifasi bor:
//  1) ekran o'chganda ham o'zini "ko'rinib turibman" deb bilish — shunda YouTube pauza qilmaydi;
//  2) assets/yt-bridge.js ni har sahifaga qo'shish va undan holat xabarlarini Player'ga berish;
//  3) YouTube'ning ⛶ (to'liq ekran) tugmasini qo'llab-quvvatlash.
@SuppressLint("ViewConstructor")
public class YtWebView extends WebView {
    private static final String BRIDGE = "yt-bridge.js";
    // ko'prik faqat YouTube sahifalarida ishlaydi (boshqa saytlar YtNative'ni ko'rmaydi)
    private static final Set<String> ORIGINS =
            new HashSet<>(Arrays.asList("https://*.youtube.com", "https://youtube.com"));

    private final String bridgeJs;
    // true: skript har sahifada YouTube skriptlaridan OLDIN o'zi ishlaydi.
    // false (juda eski WebView): sahifa ko'ringanda qo'lda qo'shamiz.
    private final boolean documentStart;

    // true: ustida Lynx paneli ochiq, bosishlarni qabul qilmaymiz
    boolean blocked;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public YtWebView(Context context, Runnable onRendererGone) {
        super(context);
        setBackgroundColor(Color.rgb(15, 15, 18));

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        // keyingi video fonda ham o'zi boshlanishi uchun (ekranga bosish shart emas)
        settings.setMediaPlaybackRequiresUserGesture(false);
        // "; wv" — WebView belgisi. Olib tashlasak, YouTube (va Google'ga kirish) bizni oddiy Chrome deb biladi
        settings.setUserAgentString(settings.getUserAgentString().replace("; wv", ""));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);

        if (Build.VERSION.SDK_INT >= 26) {
            // sahifa jarayoni (renderer) ekran o'chganda ham "muhim" bo'lib qolsin,
            // aks holda tizim uni xotira uchun o'ldirib yuborishi mumkin
            setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, false);
        }

        // JS → Java: YtNative.postMessage(json)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(this, "YtNative", ORIGINS,
                    (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                        String data = message.getData();
                        if (isMainFrame && data != null) post(() -> Player.get().onPage(data));
                    });
        } else {
            addJavascriptInterface(new Object() {
                @JavascriptInterface
                public void postMessage(String data) {
                    post(() -> Player.get().onPage(data));
                }
            }, "YtNative");
        }

        bridgeJs = readAsset(context, BRIDGE);
        documentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        if (documentStart) {
            WebViewCompat.addDocumentStartJavaScript(this, bridgeJs, ORIGINS);
        }

        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                // intent:// va vnd.youtube: — rasmiy YouTube ilovasini ochishga urinish. Bizda qolamiz.
                String scheme = request.getUrl().getScheme();
                return !"http".equals(scheme) && !"https".equals(scheme);
            }

            @Override
            public void onPageCommitVisible(WebView v, String url) {
                if (!documentStart) evaluateJavascript(bridgeJs, null);
            }

            @Override
            public void doUpdateVisitedHistory(WebView v, String url, boolean isReload) {
                // YouTube bitta sahifali ilova: video almashganda ham shu yerga keladi
                Player.get().onNavigation(url, v.canGoBack());
            }

            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detail) {
                // sahifa jarayoni o'ldi — WebView'ni qayta yaratamiz, aks holda butun ilova yiqiladi
                Log.w(MainActivity.TAG, "renderer o'ldi (crash=" + detail.didCrash() + ")");
                onRendererGone.run();
                return true;
            }
        });

        setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                enterFullscreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });
    }

    // Asosiy hiyla. Ekran o'chsa yoki boshqa ilovaga o'tilsa, Android bu yerga INVISIBLE/GONE yuboradi,
    // WebView esa sahifani "hidden" qiladi → YouTube pauza bosadi. Biz unga doim VISIBLE deymiz,
    // GONE'ni esa umuman o'tkazmaymiz.
    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        if (visibility != View.GONE) super.onWindowVisibilityChanged(View.VISIBLE);
    }

    // Ota-view'lar yashirilganda ham (masalan, Lynx tomonidan) sahifa ko'rinib turgandek qolsin
    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, View.VISIBLE);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return !blocked && super.dispatchTouchEvent(event);
    }

    // Java → JS: window.__ytCmd('next', 0)
    void command(String action, double value) {
        evaluateJavascript("window.__ytCmd&&window.__ytCmd(" + JSONObject.quote(action) + "," + value + ")",
                null);
    }

    // ---------------------------------------------------------------- to'liq ekran

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        Activity activity = findActivity(getContext());
        if (activity == null || customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        view.setBackgroundColor(Color.BLACK);
        ViewGroup content = activity.findViewById(android.R.id.content);
        content.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setImmersive(activity, true);
    }

    boolean exitFullscreen() {
        if (customView == null) return false;
        if (customView.getParent() instanceof ViewGroup) {
            ((ViewGroup) customView.getParent()).removeView(customView);
        }
        customView = null;
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
        Activity activity = findActivity(getContext());
        if (activity != null) setImmersive(activity, false);
        return true;
    }

    @SuppressWarnings("deprecation")
    private static void setImmersive(Activity activity, boolean on) {
        View decor = activity.getWindow().getDecorView();
        if (on) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static String readAsset(Context context, String name) {
        try (InputStream in = context.getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) != -1) {
                out.write(buffer, 0, length);
            }
            return out.toString("UTF-8");
        } catch (IOException e) {
            Log.e(MainActivity.TAG, "assets/" + name + " o'qilmadi", e);
            return "";
        }
    }
}
