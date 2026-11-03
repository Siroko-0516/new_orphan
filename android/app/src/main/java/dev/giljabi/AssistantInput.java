package dev.giljabi;

/** One explicitly started recording. finish sends it; close discards it. */
interface AssistantInput extends AutoCloseable {
    interface Listener {
        void ready(String route);
        void finished(byte[] wav,boolean reachedLimit);
        void error(String message);
    }
    void start();
    void finish();
    @Override void close();
}
