package dev.giljabi;

interface VideoSource extends AutoCloseable {
    @Override void close();
}
