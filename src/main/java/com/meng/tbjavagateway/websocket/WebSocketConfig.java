package com.meng.tbjavagateway.websocket;

import com.meng.tbjavagateway.config.RedisUtils;
import com.meng.tbjavagateway.websocket.acp.HandlerByAcp;
import com.meng.tbjavagateway.websocket.acp.WebSocketClientUpdateByAcp;
import com.meng.tbjavagateway.websocket.acp.WebSocketHandlerByAcp;
import com.meng.tbjavagateway.websocket.alarm.HandlerByAlarm;
import com.meng.tbjavagateway.websocket.alarm.WebSocketClientUpdateByAlarm;
import com.meng.tbjavagateway.websocket.alarm.WebSocketHandlerByAlarm;
import com.meng.tbjavagateway.websocket.dianBiao.HandlerByDianBiao;
import com.meng.tbjavagateway.websocket.dianBiao.WebSocketClientUpdateByDianBiao;
import com.meng.tbjavagateway.websocket.dianBiao.WebSocketHandlerByDianBiao;
import com.meng.tbjavagateway.websocket.fuhe.HandlerByFuHe;
import com.meng.tbjavagateway.websocket.fuhe.WebSocketClientUpdateByFuHe;
import com.meng.tbjavagateway.websocket.fuhe.WebSocketHandlerByFuHe;
import com.meng.tbjavagateway.websocket.lms.HandlerByLms;
import com.meng.tbjavagateway.websocket.lms.WebSocketClientUpdateByLms;
import com.meng.tbjavagateway.websocket.lms.WebSocketHandlerByLms;
import com.meng.tbjavagateway.websocket.yongneng.HandlerByYongNeng;
import com.meng.tbjavagateway.websocket.yongneng.WebSocketClientUpdateByYongNeng;
import com.meng.tbjavagateway.websocket.yongneng.WebSocketHandlerByYongNeng;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class WebSocketConfig {

    @Value("${newenergy.url}")
    private String host;

    private static final Logger logger = LogManager.getLogger(WebSocketConfig.class);

    private Map<String, WebSocketClient> webSocketClients = new HashMap<>();

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private WebSocketHandlerByAcp webSocketHandlerByAcp;

    @Autowired
    private WebSocketHandlerByDianBiao webSocketHandlerByDianBiao;

    @Autowired
    private WebSocketHandlerByYongNeng webSocketHandlerByYongNeng;

    @Autowired
    private WebSocketHandlerByFuHe webSocketHandlerByFuHe;

    @Autowired
    private WebSocketHandlerByAlarm webSocketHandlerByAlarm;

    @Autowired
    private WebSocketHandlerByLms webSocketHandlerByLms;

    @Autowired
    private WebSocketClientUpdateByAcp clientUpdateByAcp;

    @Autowired
    private WebSocketClientUpdateByAlarm clientUpdateByAlarm;

    @Autowired
    private WebSocketClientUpdateByDianBiao clientUpdateByDianBiao;

    @Autowired
    private WebSocketClientUpdateByFuHe clientUpdateByFuHe;

    @Autowired
    private WebSocketClientUpdateByLms clientUpdateByLms;

    @Autowired
    private WebSocketClientUpdateByYongNeng clientUpdateByYongNeng;

    @Bean
    public WebSocketClient webSocketClientByAcp() {
        WebSocketClient client = createWebSocketClientByAcp(host, webSocketHandlerByAcp);
        webSocketClients.put("acp", client);
        return client;
    }

    @Bean
    public WebSocketClient webSocketClientByDianBiao() {
        WebSocketClient client = createWebSocketClientByDianBiao(host, webSocketHandlerByDianBiao);
        webSocketClients.put("dianbiao", client);
        return client;
    }

    @Bean
    public WebSocketClient webSocketClientByYongNeng() {
        WebSocketClient client = createWebSocketClientByYongNeng(host, webSocketHandlerByYongNeng);
        webSocketClients.put("yongneng", client);
        return client;
    }

    @Bean
    public WebSocketClient webSocketClientByFuHe() {
        WebSocketClient client = createWebSocketClientByFuHe(host, webSocketHandlerByFuHe);
        webSocketClients.put("fuhe", client);
        return client;
    }

    @Bean
    public WebSocketClient webSocketClientByAlarm() {
        WebSocketClient client = createWebSocketClientByAlarm(host, webSocketHandlerByAlarm);
        webSocketClients.put("alarm", client);
        return client;
    }

    @Bean
    public WebSocketClient webSocketClientByLms() {
        WebSocketClient client = createWebSocketClientByLms(host, webSocketHandlerByLms);
        webSocketClients.put("lms", client);
        return client;
    }

    private WebSocketClient createWebSocketClientByAcp(String uri, HandlerByAcp handler) {
        try {
            WebSocketClient tempClient = new WebSocketClient(new URI(uri), new Draft_6455()) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    handler.onOpen(handshakedata);
                    send("{\"type\":\"Verify\",\"token\":\"1000:GFKD-ZHGLPT\",\"mode\":\"CSharp\"}");
                }

                @Override
                public void onMessage(String message) {
//                    logger.info("config -> {}", message);
                    handler.onMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    handler.onClose(code, reason, remote);
                }

                @Override
                public void onError(Exception ex) {
                    handler.onError(ex);
                }
            };
            tempClient.connect();
            return tempClient;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private WebSocketClient createWebSocketClientByDianBiao(String uri, HandlerByDianBiao handler) {
        try {
            WebSocketClient tempClient = new WebSocketClient(new URI(uri), new Draft_6455()) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    handler.onOpen(handshakedata);
                    send("{\"type\":\"Verify\",\"token\":\"1000:GFKD-ZHGLPT\",\"mode\":\"CSharp\"}");
                }

                @Override
                public void onMessage(String message) {
                    handler.onMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    handler.onClose(code, reason, remote);
                }

                @Override
                public void onError(Exception ex) {
                    handler.onError(ex);
                }
            };
            tempClient.connect();
            return tempClient;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private WebSocketClient createWebSocketClientByYongNeng(String uri, HandlerByYongNeng handler) {
        try {
            WebSocketClient tempClient = new WebSocketClient(new URI(uri), new Draft_6455()) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    handler.onOpen(handshakedata);
                    send("{\"type\":\"Verify\",\"token\":\"1000:GFKD-ZHGLPT\",\"mode\":\"CSharp\"}");
                }

                @Override
                public void onMessage(String message) {
                    handler.onMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    handler.onClose(code, reason, remote);
                }

                @Override
                public void onError(Exception ex) {
                    handler.onError(ex);
                }
            };
            tempClient.connect();
            return tempClient;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private WebSocketClient createWebSocketClientByFuHe(String uri, HandlerByFuHe handler) {
        try {
            WebSocketClient tempClient = new WebSocketClient(new URI(uri), new Draft_6455()) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    handler.onOpen(handshakedata);
                    send("{\"type\":\"Verify\",\"token\":\"1000:GFKD-ZHGLPT\",\"mode\":\"CSharp\"}");
                }

                @Override
                public void onMessage(String message) {
                    handler.onMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    handler.onClose(code, reason, remote);
                }

                @Override
                public void onError(Exception ex) {
                    handler.onError(ex);
                }
            };
            tempClient.connect();
            return tempClient;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private WebSocketClient createWebSocketClientByAlarm(String uri, HandlerByAlarm handler) {
        try {
            WebSocketClient tempClient = new WebSocketClient(new URI(uri), new Draft_6455()) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    handler.onOpen(handshakedata);
                    send("{\"type\":\"Verify\",\"token\":\"1000:GFKD-ZHGLPT\",\"mode\":\"CSharp\"}");
                }

                @Override
                public void onMessage(String message) {
                    handler.onMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    handler.onClose(code, reason, remote);
                    close();
                }

                @Override
                public void onError(Exception ex) {
                    handler.onError(ex);
                }
            };
            tempClient.connect();
            return tempClient;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private WebSocketClient createWebSocketClientByLms(String uri, HandlerByLms handler) {
        try {
            WebSocketClient tempClient = new WebSocketClient(new URI(uri), new Draft_6455()) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    handler.onOpen(handshakedata);
                    send("{\"type\":\"Verify\",\"token\":\"1000:GFKD-ZHGLPT\",\"mode\":\"CSharp\"}");
                }

                @Override
                public void onMessage(String message) {
                    handler.onMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    handler.onClose(code, reason, remote);
                }

                @Override
                public void onError(Exception ex) {
                    handler.onError(ex);
                }
            };
            tempClient.connect();
            return tempClient;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    @PreDestroy
    public void closeWebSocketClients() {
        for (WebSocketClient client : webSocketClients.values()) {
            client.close();
            logger.info(webSocketClients.values() + "已释放");
        }
    }

    @Scheduled(fixedDelay = 20000) // Execute every 20 seconds
    public void reconnectIfInactive() {
        List<String> clientsToRemove = new ArrayList<>();
        for (Map.Entry<String, WebSocketClient> entry : webSocketClients.entrySet()) {
            String connectionName = entry.getKey();
            String connectionActive = redisUtils.get(connectionName + "WsActive");
            if (connectionActive != null && connectionActive.equals("false")) {
                logger.warn(connectionName + "正在重连");
                clientsToRemove.add(connectionName);
                switch (connectionName) {
                    case "acp":
                        WebSocketClient newAcpClient = createWebSocketClientByAcp(host, webSocketHandlerByAcp);
                        webSocketClients.put("acp", newAcpClient);
                        redisUtils.set("NewOneStatusByAcp", "false");
                        clientUpdateByAcp.updateWebSocketClient(newAcpClient);
                        logger.warn(connectionName + "创建连接成功");
                        break;
                    case "dianbiao":
                        WebSocketClient newDianBiaoClient = createWebSocketClientByDianBiao(host, webSocketHandlerByDianBiao);
                        webSocketClients.put("dianbiao", newDianBiaoClient);
                        redisUtils.set("NewOneStatusByDianBiao", "false");
                        clientUpdateByDianBiao.updateWebSocketClient(newDianBiaoClient);
                        logger.warn(connectionName + "创建连接成功");
                        break;
                    case "yongneng":
                        WebSocketClient newYongNengClient = createWebSocketClientByYongNeng(host, webSocketHandlerByYongNeng);
                        webSocketClients.put("yongneng", newYongNengClient);
                        redisUtils.set("NewOneStatusByYongNeng", "false");
                        clientUpdateByYongNeng.updateWebSocketClient(newYongNengClient);
                        logger.warn(connectionName + "创建连接成功");
                        break;
                    case "fuhe":
                        WebSocketClient newFuHeClient = createWebSocketClientByFuHe(host, webSocketHandlerByFuHe);
                        webSocketClients.put("fuhe", newFuHeClient);
                        redisUtils.set("NewOneStatusByFuHe", "false");
                        clientUpdateByFuHe.updateWebSocketClient(newFuHeClient);
                        logger.warn(connectionName + "创建连接成功");
                        break;
                    case "alarm":
                        WebSocketClient newAlarmClient = createWebSocketClientByAlarm(host, webSocketHandlerByAlarm);
                        webSocketClients.put("alarm", newAlarmClient);
                        redisUtils.set("NewOneStatusByAlarm", "false");
                        clientUpdateByAlarm.updateWebSocketClient(newAlarmClient);
                        logger.warn(connectionName + "创建连接成功");
                        break;
                    case "lms":
                        WebSocketClient newLmsClient = createWebSocketClientByLms(host, webSocketHandlerByLms);
                        webSocketClients.put("lms", newLmsClient);
                        redisUtils.set("NewOneStatusByLms", "false");
                        clientUpdateByLms.updateWebSocketClient(newLmsClient);
                        logger.warn(connectionName + "创建连接成功");
                        break;
                    default:
                        break;
                }
            }
        }
    }
}
