package com.example.ytlynx;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.lynx.tasm.behavior.LynxContext;
import com.lynx.tasm.behavior.LynxProp;
import com.lynx.tasm.behavior.ui.LynxUI;
import com.lynx.tasm.event.LynxDetailEvent;

import java.util.HashMap;
import java.util.Map;

// Lynx'dagi <yt-webview> tegi (MainActivity'da ro'yxatdan o'tgan).
//
//   <yt-webview blocked={...} bindstate={(e) => e.detail} bindback={...} />
//
// Ichida YtWebView turadi. Uni FrameLayout'ga o'raymiz: sahifa jarayoni o'lsa, eski WebView'ni
// yangisiga almashtirish mumkin bo'lsin (Lynx'ning o'z view'i esa o'zgarmaydi).
public class YtWebViewUI extends LynxUI<FrameLayout> implements Player.Listener {
    private static final String HOME = "https://m.youtube.com/";

    // createView() LynxUI konstruktori ichida chaqiriladi, shuning uchun bu maydonlarga
    // boshlang'ich qiymat yozmaymiz (aks holda createView'dan keyin qayta nolga tushadi)
    private YtWebView web;
    private boolean blocked;

    public YtWebViewUI(LynxContext context) {
        super(context);
    }

    @Override
    protected FrameLayout createView(Context context) {
        FrameLayout box = new FrameLayout(context);
        box.setBackgroundColor(Color.rgb(15, 15, 18));
        showWebView(box, Player.get().takePendingUrl(HOME));
        return box;
    }

    private void showWebView(FrameLayout box, String url) {
        // WebView'ga Activity'ning o'zini beramiz (dialoglar va to'liq ekran uchun)
        Activity activity = YtWebView.findActivity(box.getContext());
        Context context = activity != null ? activity : box.getContext();
        YtWebView view = new YtWebView(context, this::onRendererGone);
        view.blocked = blocked;
        box.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Lynx o'lchamni faqat o'z layout'ida beradi; qayta yaratilgan WebView'ni o'zimiz joylaymiz
        int w = box.getWidth();
        int h = box.getHeight();
        if (w > 0 && h > 0) {
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, w, h);
        }
        web = view;
        Player.get().attach(view, this);
        view.loadUrl(url);
    }

    private void onRendererGone() {
        YtWebView dead = web;
        String url = Player.get().url;
        FrameLayout box = getView();
        if (dead == null || box == null) return;
        dead.exitFullscreen();
        box.removeView(dead);
        showWebView(box, url.isEmpty() ? HOME : url);
        dead.destroy();
    }

    @LynxProp(name = "blocked", defaultBoolean = false)
    public void setBlocked(boolean value) {
        blocked = value;
        if (web != null) web.blocked = value;
    }

    // ---------------------------------------------------------------- Player.Listener

    @Override
    public void onState(Player p) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("url", p.url);
        detail.put("canGoBack", p.canGoBack);
        detail.put("videoId", p.videoId);
        detail.put("title", p.title);
        detail.put("channel", p.channel);
        detail.put("playing", p.playing);
        detail.put("position", p.position);
        detail.put("duration", p.duration);
        detail.put("sleepLeft", p.sleepLeftSeconds());
        emit("state", detail);
    }

    @Override
    public boolean onBack() {
        if (!blocked) return false;
        // ustida panel ochiq — orqaga tugmasi uni yopsin
        emit("back", new HashMap<>());
        return true;
    }

    private void emit(String name, Map<String, Object> detail) {
        // LynxDetailEvent: JS'da e.detail bo'lib keladi (LynxCustomEvent'da e.params bo'lardi)
        getLynxContext().getEventEmitter().sendCustomEvent(new LynxDetailEvent(getSign(), name, detail));
    }

    @Override
    public void destroy() {
        if (web != null) {
            web.exitFullscreen();
            Player.get().detach(web);
            web.destroy();
            web = null;
        }
        super.destroy();
    }
}
