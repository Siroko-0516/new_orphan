package com.example.minicpm_v_demo;

/** JNI names match the MIT-licensed official MiniCPM bridge. Calls must be serialized. */
public final class LlamaEngine {
    static { System.loadLibrary("giljabi_native"); }
    public native void init(String nativeLibraryDir);
    public native int load(String modelPath);
    public native int loadMmproj(String path, int imageMaxSlices);
    public native int prepare();
    public native int processSystemPrompt(String prompt);
    public native int prefillImage(byte[] jpeg, int size);
    public native int processUserPrompt(String prompt, int maxTokens);
    public native String generateNextToken();
    public native void fullReset();
    public native void unload();
    public native void shutdown();
    public native void setEnableThinkingNative(boolean enabled);
    public native void setMinicpmvVersionNative(int version);
}
