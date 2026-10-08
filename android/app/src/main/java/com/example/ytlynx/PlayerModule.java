package com.example.ytlynx;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.lynx.jsbridge.LynxMethod;
import com.lynx.jsbridge.LynxModule;

// JS'dagi NativeModules.PlayerModule (src/lib/native.ts).
// Lynx bu metodlarni o'z JS thread'ida chaqiradi, Player esa faqat main thread'da ishlaydi.
public class PlayerModule extends LynxModule {
    private final Handler mMain = new Handler(Looper.getMainLooper());

    public PlayerModule(Context context) {
        super(context);
    }

    // play, pause, toggle, next, prev, skip (+/- soniya), sleep (daqiqa), back
    @LynxMethod
    public void command(String action, double value) {
        if (action == null) return;
        mMain.post(() -> Player.get().command(action, value));
    }

    @LynxMethod
    public void load(String url) {
        if (url == null || !url.startsWith("https://")) return;
        mMain.post(() -> Player.get().load(url));
    }
}
