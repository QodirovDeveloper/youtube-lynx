package com.example.ytlynx;

import android.app.Application;
import android.util.Log;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.core.ImagePipelineConfig;
import com.facebook.imagepipeline.memory.PoolConfig;
import com.facebook.imagepipeline.memory.PoolFactory;
import com.lynx.service.image.LynxImageService;
import com.lynx.service.log.LynxLogService;
import com.lynx.tasm.LynxEnv;
import com.lynx.tasm.service.LynxServiceCenter;

public class MainApplication extends Application {
    // Ishga tushirishdagi xato (bo'lsa) — MainActivity ekranda ko'rsatadi
    static String initError;

    @Override
    public void onCreate() {
        super.onCreate();
        Player.init(this);
        try {
            initLynxService();
            LynxEnv.inst().init(this, null, new AssetTemplateProvider(this), null);
            // JS'dagi NativeModules.* nomlari bilan bir xil bo'lishi shart
            LynxEnv.inst().registerModule("PlayerModule", PlayerModule.class);
            LynxEnv.inst().registerModule("StorageModule", StorageModule.class);
        } catch (Throwable t) {
            initError = Log.getStackTraceString(t);
            Log.e(MainActivity.TAG, "Lynx init failed", t);
        }
    }

    private void initLynxService() {
        // LynxImageService (<image>, video muqovalari) uchun Fresco kerak
        final PoolFactory factory = new PoolFactory(PoolConfig.newBuilder().build());
        ImagePipelineConfig.Builder builder =
                ImagePipelineConfig.newBuilder(getApplicationContext()).setPoolFactory(factory);
        Fresco.initialize(getApplicationContext(), builder.build());

        LynxServiceCenter.inst().registerService(LynxImageService.getInstance());
        LynxServiceCenter.inst().registerService(LynxLogService.INSTANCE);
    }
}
