package com.meng.tbjavagateway.websocket.yongneng;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meng.tbjavagateway.config.RedisUtils;
import com.meng.tbjavagateway.reportTelemetry.DeviceInfoData;
import com.meng.tbjavagateway.reportTelemetry.JavaGateway;
import com.meng.tbjavagateway.websocket.WebSocketConfig;
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
public class WebSocketHandlerByYongNeng implements HandlerByYongNeng {

    private static final Logger logger = LogManager.getLogger(WebSocketHandlerByYongNeng.class);

    private final Pattern pattern = Pattern.compile("1000/R/\\d+/(Hour|Day|Month|Year)");

    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private JavaGateway javaGateway;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private ExecutorService executorService = Executors.newFixedThreadPool(300); // 线程池大小，到时候看性能调整

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("jianzhuYongNeng connection opened");
    }

    @Override
    public void onMessage(String message) {
//        logger.info("YongNeng message received: {}", message);
        executorService.submit(() -> {
            ObjectMapper mapper = new ObjectMapper();
            try {
                JsonNode rootNode = mapper.readTree(message);
                String redisYongNeng = redisUtils.get("NewOneStatusByYongNeng");
                if (redisYongNeng == null || !redisYongNeng.equals("true")) {
                    if (message.contains("{\"result\":\"true\",\"serverTime\":0,\"type\":\"Verify\"}")) {
                        redisUtils.set("yongnengWsActive", "true");
                        redisUtils.set("NewOneStatusByYongNeng", "true");
                        logger.info("建筑用能登录鉴权已通过");
                        return;
                    }
                }
                if (!rootNode.has("value")) {
                    if (!rootNode.has("type") || !"Heart".equals(rootNode.get("type").asText())) {
                        logger.warn("建筑用能-ws接收数据格式错误，请检查: {}, 当前状态: {}", message, redisUtils.get("NewOneStatusByYongNeng"));
                        return;
                    }
                    return;
                }

                if (rootNode.has("type") && "SetBack".equals(rootNode.get("type").asText())) {
                    logger.info("建筑用能-控制设备数据: {}", message);
                    return;
                }
                Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    // 空调业务处理
                    next(message);
                } else {
                    logger.warn("建筑用能 ==> 此消息进入了错误的ws接收类消息内容: {}", message);
                }

            } catch (IOException e) {
                logger.error("建筑用能-解析消息时出错: {}", e.getMessage());
                // 处理异常
            }
        });
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        redisUtils.set("yongnengWsActive", "false");
        logger.warn("jianzhuYongNeng - ws connection closed");
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public void next(String message) {
        String valueYongNeng = redisUtils.get("deviceYongNeng");
        if (valueYongNeng == null && valueYongNeng.isEmpty()) {
            ObjectMapper mapper1 = new ObjectMapper();
            try {
                valueYongNeng = mapper1.writeValueAsString(getDeviceCredentialsYongNeng());
                redisUtils.set("deviceYongNeng", valueYongNeng);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        if (valueYongNeng == null && valueYongNeng.isEmpty()) {
            logger.error("建筑用能数据获取失败，请检查");
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rootNode = mapper.readTree(message);
            String jsonNode = rootNode.get("value").asText();
            String topic = rootNode.get("topic").asText();
            List<DeviceInfoData> retrievedDeviceInfoList = mapper.readValue(valueYongNeng, new TypeReference<List<DeviceInfoData>>() {
            });
            String topicData = rootNode.get("topic").asText();
            String[] parts = topicData.split("/");
            for (DeviceInfoData deviceInfoData : retrievedDeviceInfoList) {
                //{"state":0,"time":"2024-05-11 17:49:04.512","topic":"1000/D/K_205_01.XRTemp/Real","type":"SubBack","value":"0"}

                if (topic.contains("1000/R/" + deviceInfoData.getIp()) || topic.contains("1000/R/151") || topic.contains("1000/R/140") || topic.contains("1000/R/141")) {
                    // 对应设备
                    if (parts.length >= 4) {
                        String formularId = parts[2]; // 获取FormularId的值
                        JSONObject msg = new JSONObject();
                        JSONArray nameArray = new JSONArray();
                        JSONObject nameObject = new JSONObject();
                        if (deviceInfoData.getIp().equals(formularId)) {
                            // 市电用能
                            if (topic.contains("Hour")) {
                                nameObject.put("Hour", jsonNode); // 每小时
                            } else if (topic.contains("Day")) {
                                nameObject.put("Day", jsonNode); // 本日
                            } else if (topic.contains("Month")) {
                                nameObject.put("Month", jsonNode); // 本月
                            } else if (topic.contains("Year")) {
                                nameObject.put("Year", jsonNode); // 本年
                            }
                        } else if ("151".equals(formularId) && deviceInfoData.getName().equals("BED0301001")) {
                            // 光伏发电量
                            if (topic.contains("Hour")) {
                                nameObject.put("ppgHour", jsonNode); // 每小时
                            } else if (topic.contains("Day")) {
                                nameObject.put("ppgDay", jsonNode); // 本日
                            } else if (topic.contains("Month")) {
                                nameObject.put("ppgMonth", jsonNode); // 本月
                            } else if (topic.contains("Year")) {
                                nameObject.put("ppgYear", jsonNode); // 本年
                            }
                        } else if ("140".equals(formularId) && deviceInfoData.getName().equals("BED0301001")) {
                            // 储能充电量
                            if (topic.contains("Hour")) {
                                nameObject.put("escHour", jsonNode); // 每小时
                            } else if (topic.contains("Day")) {
                                nameObject.put("escDay", jsonNode); // 本日
                            } else if (topic.contains("Month")) {
                                nameObject.put("escMonth", jsonNode); // 本月
                            } else if (topic.contains("Year")) {
                                nameObject.put("escYear", jsonNode); // 本年
                            }
                        } else if ("141".equals(formularId) && deviceInfoData.getName().equals("BED0301001")) {
                            // 储能放电量
                            if (topic.contains("Hour")) {
                                nameObject.put("esdHour", jsonNode); // 每小时
                            } else if (topic.contains("Day")) {
                                nameObject.put("esdDay", jsonNode); // 本日
                            } else if (topic.contains("Month")) {
                                nameObject.put("esdMonth", jsonNode); // 本月
                            } else if (topic.contains("Year")) {
                                nameObject.put("esdYear", jsonNode); // 本年
                            }
                        }
                        nameArray.put(nameObject);
                        msg.put(deviceInfoData.getName(), nameArray);
                        if (!nameObject.isEmpty()) {
                            javaGateway.javaGatewaySend(String.valueOf(msg));
                        }
                    }
                }
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    private List<DeviceInfoData> getDeviceCredentialsYongNeng() {
        String sql = "SELECT name,label AS ip,type AS address FROM device WHERE name LIKE '%BED%'";
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