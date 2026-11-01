package dev.giljabi;

import android.app.Application;

public final class GiljabiApp extends Application {
    @Override public void onCreate(){super.onCreate();CrashDiagnostics.install(this);}
}
