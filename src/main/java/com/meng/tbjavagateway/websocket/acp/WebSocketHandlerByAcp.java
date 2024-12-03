package com.meng.tbjavagateway.websocket.acp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meng.tbjavagateway.config.RedisUtils;
import com.meng.tbjavagateway.reportTelemetry.DeviceAcp;
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
public class WebSocketHandlerByAcp implements HandlerByAcp {

    private static final Logger logger = LogManager.getLogger(WebSocketHandlerByAcp.class);

    private final Pattern pattern = Pattern.compile("1000/D/K_.*?/Real"); //空调检测正则

    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private JavaGateway javaGateway;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private ExecutorService executorService = Executors.newFixedThreadPool(300); // 线程池大小，到时候看性能调整

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("ACP connection opened");
    }

    @Override
    public void onMessage(String message) {
//        logger.info("ACP message received: {}", message);
        executorService.submit(() -> {
            ObjectMapper mapper = new ObjectMapper();
            try {
                JsonNode rootNode = mapper.readTree(message);
                String redisAcp = redisUtils.get("NewOneStatusByAcp");
                if (redisAcp == null || !redisAcp.equals("true")) {
                    if (message.contains("{\"result\":\"true\",\"serverTime\":0,\"type\":\"Verify\"}")) {
                        redisUtils.set("acpWsActive", "true");
                        redisUtils.set("NewOneStatusByAcp", "true");
                        redisUtils.set("exceptionNumber", "0");
                        logger.info("空调登录鉴权已通过");
                        return;
                    }
                }
                if (!rootNode.has("value")) {
                    // 回调类一分钟发送一次无效数据，尝试让线程保持活跃看是否有掉线的情况。
                    // 情况正常后，将此代码解除注释，无需在输出。
                    // 当前保留错误回调输出仅为方便查看日志
//                    if (rootNode.has("message") && "test".equals(rootNode.get("message").asText())) {
//                        return;
//                    }
                    if (!rootNode.has("type") || !"Heart".equals(rootNode.get("type").asText())) {
                        logger.warn("ACP-ws接收数据格式错误，请检查: {}, 当前状态: {}", message, redisUtils.get("NewOneStatusByAcp"));
                        return;
                    }
                    return;
                }

                if (rootNode.has("type") && "SetBack".equals(rootNode.get("type").asText())) {
                    logger.info("ACP-控制设备返回数据: {}", message);
                    return;
                }
                Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    // 业务处理
                    next(message);
                } else {
                    logger.warn("空调 ==> 此消息进入了错误的ws接收类消息内容: {}", message);
                }

            } catch (IOException e) {
                logger.error("ACP-解析消息时出错: {}", e.getMessage());
                // 处理异常
            }
        });
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        redisUtils.set("acpWsActive", "false");
        logger.warn("ACP - ws connection closed");
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public void next(String message) {
        String value = redisUtils.get("deviceCredentials");
        if (value == null && value.isEmpty()) {
            ObjectMapper mapper1 = new ObjectMapper();
            try {
                value = mapper1.writeValueAsString(getDeviceCredentials());
                redisUtils.set("deviceCredentials", value);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        if (value == null && value.isEmpty()) {
            logger.error("空调设备数据获取失败，请检查");
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rootNode = mapper.readTree(message);
            String jsonNode = rootNode.get("value").asText();
            String topic = rootNode.get("topic").asText();
            List<DeviceInfoData> retrievedDeviceInfoList = mapper.readValue(value, new TypeReference<List<DeviceInfoData>>() {
            });
            for (DeviceInfoData deviceInfoData : retrievedDeviceInfoList) {
                //{"state":0,"time":"2024-05-11 17:49:04.512","topic":"1000/D/K_205_01.XRTemp/Real","type":"SubBack","value":"0"}
                if (topic.contains(deviceInfoData.getIp())) {
                    // 对应设备
                    JSONObject msg = new JSONObject();
                    JSONArray nameArray = new JSONArray();
                    JSONObject nameObject = new JSONObject();
                    if (topic.contains("ONOFF")) {
                        // 开关
                        if (Integer.valueOf(jsonNode) % 2 == 0){
                            nameObject.put(deviceInfoData.getName() + "SWS001", 0);
                        } else {
                            nameObject.put(deviceInfoData.getName() + "SWS001", 1);
                        }
                    } else if (topic.contains("Mode")) {
                        // 模式
                        nameObject.put(deviceInfoData.getName() + "MOD001", jsonNode);
                    } else if (topic.contains("Fan")) {
                        // 风量
                        nameObject.put(deviceInfoData.getName() + "WSD001", jsonNode);
                    } else if (topic.contains("XRTemp")) {
                        // 房间温度
                        nameObject.put(deviceInfoData.getName() + "TMS001", jsonNode);
                    } else if (topic.contains("Temp")) {
                        // 设定温度
                        nameObject.put(deviceInfoData.getName() + "TEM001", jsonNode);
                    }
                    nameArray.put(nameObject);
                    msg.put(deviceInfoData.getName(), nameArray);
                    javaGateway.javaGatewaySend(String.valueOf(msg));
                }
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    private List<DeviceInfoData> getDeviceCredentials() {
        String sql = "SELECT name,label AS ip,type AS address FROM device WHERE name LIKE '%ACP%'";
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