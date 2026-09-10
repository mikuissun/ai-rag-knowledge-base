package com.mikuissun.knowledgebase.document.parser;

import java.io.IOException;
import java.io.Writer;

// Bound extracted text independently of compressed upload size.
final class LimitedTextWriter extends Writer {
    static final int MAX_CHARACTERS = 5_000_000;
    private final StringBuilder text = new StringBuilder();
    @Override
    public void write(char[] buffer, int offset, int length) throws IOException {
        if (length > MAX_CHARACTERS - text.length()) {
            throw new IOException("Extracted text exceeds limit");
        }
        text.append(buffer, offset, length);
    }
    @Override public void flush() { }
    @Override public void close() { }
    @Override public String toString() { return text.toString(); }
}
