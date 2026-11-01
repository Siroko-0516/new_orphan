package com.example.minicpm_v_demo;

/** JNI names match the MIT-licensed official MiniCPM bridge. Calls must be serialized. */
public final class LlamaEngine {
    static { System.loadLibrary("giljabi_native"); }
    public native String systemInfo();
    public native void init(String nativeLibraryDir);
    public native int load(String modelPath);
    /** Probes the optional Vulkan driver; call after recording an in-progress GPU attempt. */
    public native boolean hasVisionGpu();
    /** Selects vision-only GPU execution for the next loadMmproj call. */
    public native void setVisionGpuEnabled(boolean enabled);
    /** GPU mode was selected; unsupported graph operations may still use CPU. */
    public native boolean isVisionGpuEnabled();
    public native int loadMmproj(String path, int imageMaxSlices);
    public native int prepare();
    public native int processSystemPrompt(String prompt);
    public native int prefillTurn(String systemPrompt, String userPrompt, byte[] jpeg, int size, int maxTokens);
    public native int prefillImage(byte[] jpeg, int size);
    public native int processUserPrompt(String prompt, int maxTokens);
    public native String generateNextToken();
    public native void fullReset();
    public native void beginTurn(boolean structuredGuidance);
    public native void unload();
    public native void shutdown();
    public native void setEnableThinkingNative(boolean enabled);
    public native void setMinicpmvVersionNative(int version);
}
