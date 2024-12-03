package com.meng.tbjavagateway.websocket.acp;

import org.java_websocket.handshake.ServerHandshake;

public interface HandlerByAcp {
    void onOpen(ServerHandshake handshakedata);
    void onMessage(String message);
    void onClose(int code, String reason, boolean remote);
    void onError(Exception ex);
}
