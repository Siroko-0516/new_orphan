package dev.giljabi;

import java.io.IOException;

/** Cancellable cloud request shared by the selectable providers. */
interface CloudRequest extends AutoCloseable {
    String execute() throws IOException;
    @Override void close();
}
