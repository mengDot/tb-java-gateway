package com.meng.tbjavagateway.websocket.alarm;

import org.java_websocket.handshake.ServerHandshake;

public interface HandlerByAlarm {
    void onOpen(ServerHandshake handshakedata);
    void onMessage(String message);
    void onClose(int code, String reason, boolean remote);
    void onError(Exception ex);
}
