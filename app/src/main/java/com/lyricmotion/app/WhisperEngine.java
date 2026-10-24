package com.lyricmotion.app;
public final class WhisperEngine {
    static { System.loadLibrary("lyricwhisper"); }
    public static native String transcribe(String modelPath,float[] audio,String language);
}
