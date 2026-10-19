package fr.fxextractor;

import android.app.Application;

public final class FxApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        FxDebugLog.initialize(this);
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            FxDebugLog.unexpected(error);
            if(previous!=null)previous.uncaughtException(thread,error);
            else android.os.Process.killProcess(android.os.Process.myPid());
        });
    }
    @Override public void onTrimMemory(int level){
        FxDebugLog.write("APP_MEMORY_TRIM level="+level+" "+FxDebugLog.memory());
        super.onTrimMemory(level);
    }
    @Override public void onLowMemory(){
        FxDebugLog.write("APP_LOW_MEMORY "+FxDebugLog.memory());
        super.onLowMemory();
    }
}
