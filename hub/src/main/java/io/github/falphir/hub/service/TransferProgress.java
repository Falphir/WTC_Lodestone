package io.github.falphir.hub.service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Hears how far a download or upload has got: bytes so far, and the total (-1 when it isn't known). */
@FunctionalInterface
public interface TransferProgress {

    TransferProgress NONE = (done, total) -> {};

    void update(long done, long total);

    /** Reports every byte read through it, so a copy loop needs no counting of its own. */
    final class Counting extends FilterInputStream {
        private final long total;
        private final TransferProgress progress;
        private long done;

        Counting(InputStream in, long total, TransferProgress progress) {
            super(in);
            this.total = total;
            this.progress = progress;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) progress.update(++done, total);
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) progress.update(done += n, total);
            return n;
        }
    }
}
