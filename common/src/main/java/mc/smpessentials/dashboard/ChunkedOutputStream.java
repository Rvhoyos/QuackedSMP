package mc.smpessentials.dashboard;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Wraps a response body in HTTP/1.1 chunked transfer coding (RFC 9112, section 7.1),
 * for a body whose length is not known before it is produced.
 *
 * Closing writes the terminating zero-length chunk, which is what tells the client
 * the body finished rather than being cut off mid-transfer. The underlying stream is
 * flushed but never closed: the socket it belongs to is owned by the dispatcher.
 */
final class ChunkedOutputStream extends OutputStream {

    private static final byte[] CRLF      = {'\r', '\n'};
    private static final byte[] TERMINATOR = "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    private final OutputStream out;
    private final byte[] buffer;
    private int buffered;
    private boolean closed;

    ChunkedOutputStream(OutputStream out) {
        this(out, 16 * 1024);
    }

    ChunkedOutputStream(OutputStream out, int bufferSize) {
        this.out    = out;
        this.buffer = new byte[bufferSize];
    }

    @Override
    public void write(int b) throws IOException {
        if (buffered == buffer.length) flushChunk();
        buffer[buffered++] = (byte) b;
    }

    @Override
    public void write(byte[] src, int off, int len) throws IOException {
        while (len > 0) {
            int room = buffer.length - buffered;
            if (room == 0) { flushChunk(); continue; }
            int n = Math.min(room, len);
            System.arraycopy(src, off, buffer, buffered, n);
            buffered += n;
            off += n;
            len -= n;
        }
    }

    @Override
    public void flush() throws IOException {
        flushChunk();
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        flushChunk();
        out.write(TERMINATOR);
        out.flush();
    }

    // A chunk must carry at least one octet, so an empty buffer writes nothing:
    // a zero-length chunk would be read as the end of the body.
    private void flushChunk() throws IOException {
        if (buffered == 0) return;
        out.write(Integer.toHexString(buffered).getBytes(StandardCharsets.US_ASCII));
        out.write(CRLF);
        out.write(buffer, 0, buffered);
        out.write(CRLF);
        buffered = 0;
    }
}
