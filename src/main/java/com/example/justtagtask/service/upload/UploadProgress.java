package com.example.justtagtask.service.upload;

import com.example.justtagtask.exception.upload.FileTooLargeException;

public final class UploadProgress {

    private static final byte LINE_BREAK = '\n';

    private final long maxBytes;
    private long bytes;
    private long lineBreaks;
    private boolean endsWithLineBreak;

    UploadProgress(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    void record(byte[] buffer, int length) {
        bytes += length;
        if (bytes > maxBytes) {
            throw new FileTooLargeException(maxBytes);
        }
        for (int i = 0; i < length; i++) {
            if (buffer[i] == LINE_BREAK) {
                lineBreaks++;
            }
        }
        endsWithLineBreak = buffer[length - 1] == LINE_BREAK;
    }

    long bytes() {
        return bytes;
    }

    long rows() {
        long lines = lineBreaks + (bytes > 0 && !endsWithLineBreak ? 1 : 0);
        return Math.max(0, lines - 1);
    }

}
