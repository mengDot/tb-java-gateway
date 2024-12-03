package com.meng.tbjavagateway.websocket.fuhe;

import org.java_websocket.handshake.ServerHandshake;

public interface HandlerByFuHe {
    void onOpen(ServerHandshake handshakedata);
    void onMessage(String message);
    void onClose(int code, String reason, boolean remote);
    void onError(Exception ex);
}
