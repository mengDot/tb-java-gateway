package com.meng.tbjavagateway.websocket.dianBiao;

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
public class WebSocketHandlerByDianBiao implements HandlerByDianBiao {

    private static final Logger logger = LogManager.getLogger(WebSocketHandlerByDianBiao.class);

    private final Pattern dianbiaoFuHe = Pattern.compile("1000/D/(.*)\\.P/Real"); //电表负荷检测正则
    private final Pattern dianbiaoYongLiang = Pattern.compile("1000/D/(.*)\\.(nvoEPpos|EPsum|EPrev)/(Hour|Day|Month|Year|Real)"); //电表电量检测正则

    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private JavaGateway javaGateway;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executorService = Executors.newFixedThreadPool(300); // 线程池大小，到时候看性能调整

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("Dianbiao connection opened");
    }

    @Override
    public void onMessage(String message) {
//        logger.info("Dianbiao message received: {}", message);
        executorService.submit(() -> {
            ObjectMapper mapper = new ObjectMapper();
            try {
                JsonNode rootNode = mapper.readTree(message);
                String redisDianBiao = redisUtils.get("NewOneStatusByDianBiao");
                if (redisDianBiao == null || !redisDianBiao.equals("true")) {
                    if (message.contains("{\"result\":\"true\",\"serverTime\":0,\"type\":\"Verify\"}")) {
                        redisUtils.set("dianbiaoWsActive", "true");
                        redisUtils.set("NewOneStatusByDianBiao", "true");
                        logger.info("电表登录鉴权已通过");
                        return;
                    }
                }
                if (!rootNode.has("value")) {
                    if (!rootNode.has("type") || !"Heart".equals(rootNode.get("type").asText())) {
                        logger.warn("电表-ws接收数据格式错误，请检查: {}, 当前状态: {}", message, redisUtils.get("NewOneStatusByDianBiao"));
                        return;
                    }
                    return;
                }

                if (rootNode.has("type") && "SetBack".equals(rootNode.get("type").asText())) {
                    logger.info("电表-控制设备数据: {}", message);
                    return;
                }
                Matcher fuhe = dianbiaoFuHe.matcher(message);
                Matcher yongliang = dianbiaoYongLiang.matcher(message);
                if (fuhe.find() || yongliang.find()) {
                    // 业务处理
                    DianBiao(message);
                } else {
                    logger.warn("电表 ==> 此消息进入了错误的ws接收类消息内容: {}", message);
                }
            } catch (IOException e) {
                logger.error("电表-解析消息时出错: {}", e.getMessage());
                // 处理异常
            }
        });

    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        redisUtils.set("dianbiaoWsActive", "false");
        logger.warn("电表- ws connection closed");
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public void DianBiao(String message) {
        String valueDianBiao = redisUtils.get("deviceDianBiao");
        if (valueDianBiao == null && valueDianBiao.isEmpty()) {
            ObjectMapper mapper1 = new ObjectMapper();
            try {
                valueDianBiao = mapper1.writeValueAsString(getDeviceCredentialsDianBiao());
                redisUtils.set("deviceDianBiao", valueDianBiao);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        if (valueDianBiao == null && valueDianBiao.isEmpty()) {
            logger.error("电表数据获取失败，请检查");
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rootNode = mapper.readTree(message);
            List<DeviceInfoData> retrievedDeviceInfoList = mapper.readValue(valueDianBiao, new TypeReference<List<DeviceInfoData>>() {
            });
            for (DeviceInfoData deviceInfoData : retrievedDeviceInfoList) {
                //{"state":0,"time":"2024-05-11 17:49:04.512","topic":"1000/D/K_205_01.XRTemp/Real","type":"SubBack","value":"0"}
                String jsonNode = rootNode.get("value").asText();
                String topic = rootNode.get("topic").asText();
                JSONObject msg = new JSONObject();
                JSONArray nameArray = new JSONArray();
                JSONObject nameObject = new JSONObject();
                if (topic.contains("1000/D/" + deviceInfoData.getIp() + ".P/Real")) {
                    // 对应设备
                    // 电表负荷数据
                    nameObject.put("fhData", jsonNode);
                    nameArray.put(nameObject);
                    msg.put(deviceInfoData.getName(), nameArray);
                    javaGateway.javaGatewaySend(String.valueOf(msg));
                } else if (topic.contains("1000/D/" + deviceInfoData.getIp())) {
                    // 电表用量数据
                    if (topic.contains("Hour")) {
                        if (topic.contains("EPsum") || topic.contains("nvoEPpos")){
                            nameObject.put("FHourData", jsonNode);
                        } else if (topic.contains("EPrev")){
                            nameObject.put("NHourData", jsonNode);
                        }
                    } else if (topic.contains("Day")) {
                        if (topic.contains("EPsum") || topic.contains("nvoEPpos")){
                            nameObject.put("FDayData", jsonNode);
                        } else if (topic.contains("EPrev")) {
                            nameObject.put("NDayData", jsonNode);
                        }
                    } else if (topic.contains("Month")) {
                        if (topic.contains("EPsum") || topic.contains("nvoEPpos")){
                            nameObject.put("FMonthData", jsonNode);
                        } else if (topic.contains("EPrev")) {
                            nameObject.put("NMonthData", jsonNode);
                        }
                    } else if (topic.contains("Year")) {
                        if (topic.contains("EPsum") || topic.contains("nvoEPpos")){
                            nameObject.put("FYearData", jsonNode);
                        } else if (topic.contains("EPrev")) {
                            nameObject.put("NYearData", jsonNode);
                        }
                    } else if (topic.contains("Real")) {
                        if (topic.contains("EPsum") || topic.contains("nvoEPpos")){
                            nameObject.put("FData", jsonNode);
                        } else if (topic.contains("EPrev")){
                            nameObject.put("NData", jsonNode);
                        }
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

    private List<DeviceInfoData> getDeviceCredentialsDianBiao() {
        String sql = "SELECT name,label AS ip,type AS address FROM device WHERE name LIKE '%NEM%' and name!='NEM0101001'";
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