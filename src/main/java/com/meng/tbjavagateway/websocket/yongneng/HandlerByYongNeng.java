package com.meng.tbjavagateway.websocket.yongneng;

import org.java_websocket.handshake.ServerHandshake;

public interface HandlerByYongNeng {
    void onOpen(ServerHandshake handshakedata);
    void onMessage(String message);
    void onClose(int code, String reason, boolean remote);
    void onError(Exception ex);
}
