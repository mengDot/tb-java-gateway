package com.meng.tbjavagateway.websocket.lms;

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
public class WebSocketHandlerByLms implements HandlerByLms {

    private static final Logger logger = LogManager.getLogger(WebSocketHandlerByLms.class);

    private final Pattern pattern = Pattern.compile("1000/D/(.*)/Real");

    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private JavaGateway javaGateway;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private ExecutorService executorService = Executors.newFixedThreadPool(300); // 线程池大小，到时候看性能调整

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("Lms connection opened");
    }

    @Override
    public void onMessage(String message) {
//        logger.info("Lms message received: {}", message);
        executorService.submit(() -> {
            ObjectMapper mapper = new ObjectMapper();
            try {
                JsonNode rootNode = mapper.readTree(message);
                String redisLms = redisUtils.get("NewOneStatusByLms");
                if (redisLms == null || !redisLms.equals("true")) {
                    if (message.contains("{\"result\":\"true\",\"serverTime\":0,\"type\":\"Verify\"}")) {
                        redisUtils.set("lmsWsActive", "true");
                        redisUtils.set("NewOneStatusByLms", "true");
                        logger.info("灯光登录鉴权已通过");
                        return;
                    }
                }
                if (!rootNode.has("value")) {
                    if (!rootNode.has("type") || !"Heart".equals(rootNode.get("type").asText())) {
                        logger.warn("灯光-ws接收数据格式错误，请检查: {}, 当前状态: {}", message, redisUtils.get("NewOneStatusByLms"));
                        return;
                    }
                    return;
                }

                if (rootNode.has("type") && "SetBack".equals(rootNode.get("type").asText())) {
                    logger.info("灯光-控制设备数据: {}", message);
                    return;
                }
                Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    // 业务处理
                    next(message);
                } else {
                    logger.warn("灯光 ==> 此消息进入了错误的ws接收类消息内容: {}", message);
                }

            } catch (IOException e) {
                logger.error("灯光-解析消息时出错: {}", e.getMessage());
                // 处理异常
            }
        });
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        redisUtils.set("lmsWsActive", "false");
        logger.warn("Lms - ws connection closed");
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public void next(String message) {
        String valueLms = redisUtils.get("deviceLms");
        if (valueLms == null && valueLms.isEmpty()) {
            ObjectMapper mapper1 = new ObjectMapper();
            try {
                valueLms = mapper1.writeValueAsString(getDeviceCredentialsLms());
                redisUtils.set("deviceLms", valueLms);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        if (valueLms == null && valueLms.isEmpty()) {
            logger.error("灯光数据获取失败，请检查");
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rootNode = mapper.readTree(message);
            String jsonNode = rootNode.get("value").asText();
            String topic = rootNode.get("topic").asText();
            List<DeviceInfoData> retrievedDeviceInfoList = mapper.readValue(valueLms, new TypeReference<List<DeviceInfoData>>() {
            });
            for (DeviceInfoData deviceInfoData : retrievedDeviceInfoList) {

                if (topic.contains("1000/D/" + deviceInfoData.getIp())) {
                    // 对应设备
                    JSONObject msg = new JSONObject();
                    JSONArray nameArray = new JSONArray();
                    JSONObject nameObject = new JSONObject();
                    if (topic.contains("DOState1")) {
                        nameObject.put(deviceInfoData.getName()+"SWS001", jsonNode);
                    } else if (topic.contains("DOState2")) {
                        nameObject.put(deviceInfoData.getName()+"SWS002", jsonNode);
                    } else if (topic.contains("DOState3")) {
                        nameObject.put(deviceInfoData.getName()+"SWS003", jsonNode);
                    } else if (topic.contains("DOState4")) {
                        nameObject.put(deviceInfoData.getName()+"SWS004", jsonNode);
                    }
                    nameArray.put(nameObject);
                    msg.put(deviceInfoData.getName(), nameArray);
                    if (!nameObject.isEmpty()) {
                        javaGateway.javaGatewaySend(String.valueOf(msg));
                    }
                }
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    private List<DeviceInfoData> getDeviceCredentialsLms() {
        String sql = "SELECT name,label AS ip,type AS address FROM device WHERE name LIKE '%LMS%' and label is not null";
        List<DeviceInfoData> deviceCredentials = jdbcTemplate.query(sql, new RowMapper<DeviceInfoData>() {
            @Override
            public DeviceInfoData mapRow(ResultSet rs, int rowNum) throws SQLException {
                String name = rs.getString("name");
                String ip = rs.getString("ip");
                String address = rs.getString("address");
                return new DeviceInfoData(name, ip, address);
            }
        });

        return deviceCredentials;
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