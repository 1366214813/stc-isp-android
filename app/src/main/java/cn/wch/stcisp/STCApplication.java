package cn.wch.stcisp;

import android.app.Application;
import android.content.Context;

import cn.wch.uartlib.WCHUARTManager;

public class STCApplication extends Application {
    private static Application application;

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;
        WCHUARTManager.getInstance().init(this);
        WCHUARTManager.setDebug(true);
    }

    public static Context getContext() {
        return application;
    }
}
