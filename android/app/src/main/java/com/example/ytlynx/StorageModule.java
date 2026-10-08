package com.example.ytlynx;

import android.content.Context;
import android.content.SharedPreferences;

import com.lynx.jsbridge.LynxMethod;
import com.lynx.jsbridge.LynxModule;
import com.lynx.react.bridge.Callback;
import com.lynx.tasm.behavior.LynxContext;

// localStorage o'rniga: saqlanganlar va tarixni SharedPreferences'da saqlaydi.
public class StorageModule extends LynxModule {
    private static final String PREF_NAME = "YtLynxStorage";

    public StorageModule(Context context) {
        super(context);
    }

    private SharedPreferences prefs() {
        Context context = mContext instanceof LynxContext
                ? ((LynxContext) mContext).getContext()
                : mContext;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @LynxMethod
    public void setItem(String key, String value) {
        prefs().edit().putString(key, value).apply();
    }

    @LynxMethod
    public void getItem(String key, Callback callback) {
        callback.invoke(prefs().getString(key, null));
    }
}
