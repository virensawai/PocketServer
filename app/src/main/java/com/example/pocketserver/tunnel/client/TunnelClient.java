package com.example.pocketserver.tunnel.client;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.pocketserver.core.config.LimitsConfig;
import com.example.pocketserver.core.server.HttpRequestHandler;
import com.example.pocketserver.tunnel.protocol.FrameParser;
import com.example.pocketserver.tunnel.protocol.TunnelControlFrame;
import com.example.pocketserver.tunnel.reconnect.ReconnectionStrategy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Production OkHttp WebSocket client maintaining the persistent outbound multiplexed reverse tunnel.
 * Manages handshakes, heartbeat pings, request dispatching, backpressure, and jittered reconnection.
 */
public class TunnelClient implements WebSocketSender {

    private static final String TAG = "TunnelClient";

    public interface TunnelStateListener {
        void onConnecting();
        void onRegistered(@NonNull String publicUrl, @NonNull String assignedHostname);
        void onDisconnected(int code, @NonNull String reason);
        void onReconnecting(int attempt, long delayMs);
        void onError(@NonNull Throwable error);
    }

    private final String relayUrl;
    private final String deploymentId;
    private final String projectId;
    private final String authToken;
    private final String requestedHostname;
    private final OkHttpClient okHttpClient;
    private final ReconnectionStrategy reconnectionStrategy;
    private final FrameDispatcher frameDispatcher;
    private final TunnelStateListener listener;

    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean isExplicitlyClosed = new AtomicBoolean(false);

    private WebSocket webSocket;
    private volatile boolean isRegistered = false;

    public TunnelClient(
            @NonNull String relayUrl,
            @NonNull String deploymentId,
            @NonNull String projectId,
            @Nullable String authToken,
            @Nullable String requestedHostname,
            @NonNull HttpRequestHandler requestHandler,
            @Nullable TunnelStateListener listener) {
        this(
                relayUrl,
                deploymentId,
                projectId,
                authToken,
                requestedHostname,
                createDefaultOkHttpClient(),
                new ReconnectionStrategy(),
                requestHandler,
                listener
        );
    }

    public TunnelClient(
            @NonNull String relayUrl,
            @NonNull String deploymentId,
            @NonNull String projectId,
            @Nullable String authToken,
            @Nullable String requestedHostname,
            @NonNull OkHttpClient okHttpClient,
            @NonNull ReconnectionStrategy reconnectionStrategy,
            @NonNull HttpRequestHandler requestHandler,
            @Nullable TunnelStateListener listener) {
        this.relayUrl = relayUrl;
        this.deploymentId = deploymentId;
        this.projectId = projectId;
        this.authToken = authToken;
        this.requestedHostname = requestedHostname;
        this.okHttpClient = okHttpClient;
        this.reconnectionStrategy = reconnectionStrategy;
        this.listener = listener;
        this.frameDispatcher = new FrameDispatcher(requestHandler, this);
    }

    private static OkHttpClient createDefaultOkHttpClient() {
        return new OkHttpClient.Builder()
                .pingInterval(LimitsConfig.WEBSOCKET_HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for persistent WS
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Establishes outbound WebSocket connection to the Cloud Gateway relay.
     */
    public synchronized void connect() {
        if (isExplicitlyClosed.get()) {
            return;
        }

        if (webSocket != null) {
            return;
        }

        if (listener != null) {
            listener.onConnecting();
        }

        Request request = new Request.Builder()
                .url(relayUrl)
                .addHeader("X-Deployment-Id", deploymentId)
                .addHeader("X-Project-Id", projectId)
                .build();

        webSocket = okHttpClient.newWebSocket(request, new TunnelWebSocketListener());
    }

    /**
     * Terminates the reverse tunnel and frees all associated thread pools and resources.
     */
    public synchronized void disconnect() {
        isExplicitlyClosed.set(true);

        if (webSocket != null) {
            try {
                webSocket.close(1000, "Client shutdown");
            } catch (Exception ignored) {
            }
            webSocket = null;
        }

        frameDispatcher.shutdown();
        reconnectScheduler.shutdownNow();
    }

    public boolean isConnected() {
        return webSocket != null && isRegistered;
    }

    // --- WebSocketSender Implementation ---

    @Override
    public boolean sendText(@NonNull String text) {
        WebSocket ws = this.webSocket;
        if (ws != null) {
            return ws.send(text);
        }
        return false;
    }

    @Override
    public boolean sendBinary(@NonNull ByteString bytes) {
        WebSocket ws = this.webSocket;
        if (ws != null) {
            return ws.send(bytes);
        }
        return false;
    }

    @Override
    public long getQueueSize() {
        WebSocket ws = this.webSocket;
        return ws != null ? ws.queueSize() : 0;
    }

    @Override
    public void close(int code, @NonNull String reason) {
        WebSocket ws = this.webSocket;
        if (ws != null) {
            ws.close(code, reason);
        }
    }

    private synchronized void scheduleReconnect() {
        if (isExplicitlyClosed.get()) {
            return;
        }

        if (!reconnectionStrategy.canRetry()) {
            Log.e(TAG, "Max reconnection retries exceeded; abandoning tunnel connection");
            if (listener != null) {
                listener.onError(new IllegalStateException("Max reconnection attempts exceeded"));
            }
            return;
        }

        long delayMs = reconnectionStrategy.getNextDelayMs();
        int attempt = reconnectionStrategy.getAttemptCount();

        Log.i(TAG, "Scheduling tunnel reconnect attempt " + attempt + " in " + delayMs + " ms");
        if (listener != null) {
            listener.onReconnecting(attempt, delayMs);
        }

        reconnectScheduler.schedule(() -> {
            synchronized (TunnelClient.this) {
                if (!isExplicitlyClosed.get()) {
                    webSocket = null;
                    connect();
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    // --- OkHttp WebSocket Listener ---

    private class TunnelWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
            Log.i(TAG, "TUNNEL_CONNECTED {deploymentId: \"" + deploymentId + "\"}");

            // Handshake: send REGISTER frame
            TunnelControlFrame registerFrame = TunnelControlFrame.register(
                    deploymentId,
                    projectId,
                    authToken,
                    requestedHostname
            );
            sendText(registerFrame.toJson());
        }

        @Override
        public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
            TunnelControlFrame frame = FrameParser.parseTextFrame(text);
            if (frame == null) {
                Log.w(TAG, "Unrecognized or malformed control frame: " + text);
                return;
            }

            switch (frame.getType()) {
                case REGISTER_OK:
                    isRegistered = true;
                    reconnectionStrategy.reset();
                    String publicUrl = frame.getPublicUrl() != null ? frame.getPublicUrl() : "https://" + frame.getHostname();
                    String hostname = frame.getHostname() != null ? frame.getHostname() : "";
                    Log.i(TAG, "TUNNEL_LIVE {publicUrl: \"" + publicUrl + "\"}");
                    if (listener != null) {
                        listener.onRegistered(publicUrl, hostname);
                    }
                    break;

                case REQUEST_START:
                    frameDispatcher.dispatchRequestStart(frame);
                    break;

                case REQUEST_CANCEL:
                    if (frame.getRequestId() != null) {
                        frameDispatcher.cancelRequest(frame.getRequestId());
                    }
                    break;

                case PING:
                    sendText(TunnelControlFrame.pong().toJson());
                    break;

                case ERROR:
                    Log.w(TAG, "GATEWAY_ERROR {code: " + frame.getErrorCode() + ", message: \"" +
                            frame.getErrorMessage() + "\"}");
                    if (listener != null) {
                        listener.onError(new RuntimeException("Gateway error: " + frame.getErrorMessage()));
                    }
                    break;

                default:
                    Log.d(TAG, "Unhandled frame type: " + frame.getType());
                    break;
            }
        }

        @Override
        public void onClosing(@NonNull WebSocket ws, int code, @NonNull String reason) {
            ws.close(1000, null);
        }

        @Override
        public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
            Log.i(TAG, "TUNNEL_DISCONNECTED {code: " + code + ", reason: \"" + reason + "\"}");
            isRegistered = false;
            frameDispatcher.cancelAll();

            if (listener != null) {
                listener.onDisconnected(code, reason);
            }

            if (!isExplicitlyClosed.get()) {
                scheduleReconnect();
            }
        }

        @Override
        public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, @Nullable Response response) {
            Log.w(TAG, "TUNNEL_FAILURE {error: \"" + t.getMessage() + "\"}");
            isRegistered = false;
            frameDispatcher.cancelAll();

            if (listener != null) {
                listener.onError(t);
            }

            if (!isExplicitlyClosed.get()) {
                scheduleReconnect();
            }
        }
    }
}
