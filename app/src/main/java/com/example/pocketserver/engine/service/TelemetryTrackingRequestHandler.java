package com.example.pocketserver.engine.service;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.pocketserver.core.server.HttpRequest;
import com.example.pocketserver.core.server.HttpRequestHandler;
import com.example.pocketserver.core.server.HttpResponse;
import com.example.pocketserver.engine.model.DeploymentTelemetry;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Decorates an {@link HttpRequestHandler} with real-time operational telemetry tracking,
 * active stream concurrency counting, data usage tracking, and structured logging.
 */
public class TelemetryTrackingRequestHandler implements HttpRequestHandler {

    private static final String TAG = "HttpHandler";

    public interface TelemetryListener {
        void onTelemetryUpdated(@NonNull DeploymentTelemetry telemetry);
        default void onRequestLogged(@NonNull com.example.pocketserver.engine.model.RequestLogItem logItem) {}
    }

    private final HttpRequestHandler delegate;
    private final DataUsageTracker dataUsageTracker;
    private final TelemetryListener telemetryListener;

    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong bytesTransferred = new AtomicLong(0);
    private final AtomicInteger activeStreams = new AtomicInteger(0);
    private final long startedAtTimestamp;
    private final AtomicLong lastRequestTimestamp = new AtomicLong(0);

    public TelemetryTrackingRequestHandler(
            @NonNull HttpRequestHandler delegate,
            @NonNull DataUsageTracker dataUsageTracker,
            @Nullable TelemetryListener telemetryListener) {
        this.delegate = delegate;
        this.dataUsageTracker = dataUsageTracker;
        this.telemetryListener = telemetryListener;
        this.startedAtTimestamp = System.currentTimeMillis();
    }

    @NonNull
    @Override
    public HttpResponse handle(@NonNull HttpRequest request) {
        long reqNumber = requestCount.incrementAndGet();
        activeStreams.incrementAndGet();
        lastRequestTimestamp.set(System.currentTimeMillis());

        long startTimeMs = System.currentTimeMillis();
        String reqId = "req_" + reqNumber;

        logStructured("DEBUG", "REQUEST_START {reqId: \"" + reqId + "\", method: \"" +
                request.getMethod() + "\", path: \"" + request.getPath() + "\"}");

        HttpResponse response;
        try {
            response = delegate.handle(request);
        } catch (Throwable t) {
            activeStreams.decrementAndGet();
            dispatchTelemetryUpdate();
            logStructured("ERROR", "REQUEST_ERROR {reqId: \"" + reqId + "\", error: \"" +
                    t.getMessage() + "\"}");
            throw t;
        }

        if (response.hasBody() && response.getBodyStream() != null) {
            InputStream originalStream = response.getBodyStream();
            CountingInputStream countingStream = new CountingInputStream(
                    originalStream,
                    dataUsageTracker,
                    bytesTransferred,
                    activeStreams,
                    this::dispatchTelemetryUpdate,
                    bytesRead -> {
                        long durationMs = System.currentTimeMillis() - startTimeMs;
                        logStructured("INFO", "REQUEST_COMPLETE {reqId: \"" + reqId +
                                "\", status: " + response.getStatusCode() +
                                ", bytes: " + bytesRead +
                                ", durationMs: " + durationMs + "}");
                        if (telemetryListener != null) {
                            telemetryListener.onRequestLogged(new com.example.pocketserver.engine.model.RequestLogItem(
                                    reqId, request.getMethod(), request.getPath(),
                                    response.getStatusCode(), bytesRead, durationMs, System.currentTimeMillis()));
                        }
                    }
            );

            dispatchTelemetryUpdate();

            return new HttpResponse(
                    response.getStatusCode(),
                    response.getStatusMessage(),
                    response.getHeaders(),
                    countingStream,
                    response.getContentLength()
            );
        } else {
            // No body (e.g. 304 Not Modified, HEAD, empty)
            activeStreams.decrementAndGet();
            dispatchTelemetryUpdate();

            long durationMs = System.currentTimeMillis() - startTimeMs;
            logStructured("INFO", "REQUEST_COMPLETE {reqId: \"" + reqId +
                    "\", status: " + response.getStatusCode() +
                    ", bytes: 0, durationMs: " + durationMs + "}");
            if (telemetryListener != null) {
                telemetryListener.onRequestLogged(new com.example.pocketserver.engine.model.RequestLogItem(
                        reqId, request.getMethod(), request.getPath(),
                        response.getStatusCode(), 0, durationMs, System.currentTimeMillis()));
            }

            return response;
        }
    }

    public synchronized DeploymentTelemetry getSnapshot() {
        return new DeploymentTelemetry(
                requestCount.get(),
                bytesTransferred.get(),
                activeStreams.get(),
                startedAtTimestamp,
                lastRequestTimestamp.get()
        );
    }

    private void dispatchTelemetryUpdate() {
        if (telemetryListener != null) {
            telemetryListener.onTelemetryUpdated(getSnapshot());
        }
    }

    private void logStructured(String level, String eventMessage) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        String logLine = timestamp + " " + level + " [" + TAG + "] " + eventMessage;
        if ("DEBUG".equals(level)) {
            Log.d(TAG, logLine);
        } else if ("ERROR".equals(level)) {
            Log.e(TAG, logLine);
        } else {
            Log.i(TAG, logLine);
        }
    }

    private interface OnCompleteCallback {
        void onComplete(long totalBytesRead);
    }

    private static class CountingInputStream extends FilterInputStream {
        private final DataUsageTracker dataUsageTracker;
        private final AtomicLong globalBytesTransferred;
        private final AtomicInteger activeStreams;
        private final Runnable onTelemetryChanged;
        private final OnCompleteCallback onCompleteCallback;
        private final AtomicBoolean streamClosed = new AtomicBoolean(false);
        private long bytesReadForStream = 0;

        public CountingInputStream(
                @NonNull InputStream in,
                @NonNull DataUsageTracker dataUsageTracker,
                @NonNull AtomicLong globalBytesTransferred,
                @NonNull AtomicInteger activeStreams,
                @NonNull Runnable onTelemetryChanged,
                @NonNull OnCompleteCallback onCompleteCallback) {
            super(in);
            this.dataUsageTracker = dataUsageTracker;
            this.globalBytesTransferred = globalBytesTransferred;
            this.activeStreams = activeStreams;
            this.onTelemetryChanged = onTelemetryChanged;
            this.onCompleteCallback = onCompleteCallback;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                recordBytes(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int count = super.read(b, off, len);
            if (count > 0) {
                recordBytes(count);
            }
            return count;
        }

        private void recordBytes(int count) {
            bytesReadForStream += count;
            globalBytesTransferred.addAndGet(count);
            dataUsageTracker.addBytes(count);
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                if (streamClosed.compareAndSet(false, true)) {
                    activeStreams.decrementAndGet();
                    onCompleteCallback.onComplete(bytesReadForStream);
                    onTelemetryChanged.run();
                }
            }
        }
    }
}
