package com.example.ytlynx;

import android.content.Context;

import com.lynx.tasm.provider.AbsTemplateProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

// Bundle'ni APK ichidagi assets papkasidan o'qiydi
public class AssetTemplateProvider extends AbsTemplateProvider {
    private final Context mContext;

    AssetTemplateProvider(Context context) {
        this.mContext = context.getApplicationContext();
    }

    @Override
    public void loadTemplate(String uri, Callback callback) {
        new Thread(() -> {
            try (InputStream in = mContext.getAssets().open(uri);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = in.read(buffer)) != -1) {
                    out.write(buffer, 0, length);
                }
                callback.onSuccess(out.toByteArray());
            } catch (IOException e) {
                callback.onFailed(e.getMessage());
            }
        }).start();
    }
}
