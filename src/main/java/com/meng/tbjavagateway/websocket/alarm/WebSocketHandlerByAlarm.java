package com.meng.tbjavagateway.websocket.alarm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meng.tbjavagateway.config.RedisUtils;
import com.meng.tbjavagateway.reportTelemetry.DeviceInfoData;
import com.meng.tbjavagateway.reportTelemetry.JavaGateway;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSocketHandlerByAlarm implements HandlerByAlarm {

    private static final Logger logger = LogManager.getLogger(WebSocketHandlerByAlarm.class);

    private final Pattern pattern = Pattern.compile("1000/A/Alarm/Real"); // 告警检测正则 ?

    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private JavaGateway javaGateway;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private ExecutorService executorService = Executors.newFixedThreadPool(300); // 线程池大小，到时候看性能调整

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("alarm connection opened");
    }

    @Override
    public void onMessage(String message) {
//        logger.info("alarm message received: {}", message);
        executorService.submit(() -> {
            ObjectMapper mapper = new ObjectMapper();
            try {
                JsonNode rootNode = mapper.readTree(message);
                String redisAlarm = redisUtils.get("NewOneStatusByAlarm");
                if (redisAlarm == null || !redisAlarm.equals("true")) {
                    if (message.contains("{\"result\":\"true\",\"serverTime\":0,\"type\":\"Verify\"}")) {
                        redisUtils.set("alarmWsActive", "true");
                        redisUtils.set("NewOneStatusByAlarm", "true");
                        logger.info("告警登录鉴权已通过");
                        return;
                    }
                }
                if (!rootNode.has("value")) {
                    if (!rootNode.has("type") || !"Heart".equals(rootNode.get("type").asText())) {
                        logger.warn("告警-ws接收数据格式错误，请检查: {}, 当前状态: {}", message, redisUtils.get("NewOneStatusByAlarm"));
                        return;
                    }
                    return;
                }

                if (rootNode.has("type") && "SetBack".equals(rootNode.get("type").asText())) {
                    logger.info("告警-控制设备数据: {}", message);
                    return;
                }
                Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    // 业务处理
                    next(message);
                } else {
                    logger.warn("告警 ==> 此消息进入了错误的ws接收类消息内容: {}", message);
                }

            } catch (IOException e) {
                logger.error("告警-解析消息时出错: {}", e.getMessage());
                // 处理异常
            }
        });
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        redisUtils.set("alarmWsActive", "false");
        logger.warn("alarm - ws connection closed");
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public void next(String message) {
        logger.info("报警数据输出: {}", message);
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rootNode = mapper.readTree(message);
            String jsonNode = rootNode.get("value").asText();
            JSONObject msg = new JSONObject();
            JSONArray nameArray = new JSONArray();
            JSONObject nameObject = new JSONObject();
            nameObject.put("alarm", jsonNode);
            nameArray.put(nameObject);
            msg.put("NEM0101001", nameArray);
            javaGateway.javaGatewaySend(String.valueOf(msg));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    public void destroyWebSocketClient() {
        logger.info("程序停止需要等待连接正确关闭并且线程池处理完毕，请耐心等待，超时时间60秒。");
        // 关闭线程池
        executorService.shutdown(); // 不再接受新任务
        try {
            // 等待一段时间让已提交的任务完成
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                // 如果超时，则强制关闭线程池
                executorService.shutdownNow();
                logger.warn("@PreDestroy -> 超时！强制关闭线程池");
            }
        } catch (InterruptedException e) {
            // 处理中断异常
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("@PreDestroy -> close thread pool");
    }

}