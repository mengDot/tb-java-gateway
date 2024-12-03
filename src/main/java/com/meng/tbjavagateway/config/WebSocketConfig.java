//package com.meng.tbjavagateway.config;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.meng.tbjavagateway.reportTelemetry.DeviceInfoData;
//import com.meng.tbjavagateway.reportTelemetry.JavaGateway;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import org.java_websocket.client.WebSocketClient;
//import org.java_websocket.drafts.Draft_6455;
//import org.java_websocket.handshake.ServerHandshake;
//import org.json.JSONArray;
//import org.json.JSONObject;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.jdbc.core.RowMapper;
//import org.springframework.stereotype.Component;
//
//import javax.annotation.PreDestroy;
//import java.io.IOException;
//import java.net.URI;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.util.List;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//import static org.apache.logging.log4j.message.MapMessage.MapFormat.JSON;
//
//
///**
// * @Date: 2024/5/29
// * @Author: mengGod
// * @Description: 新能源ws接收处理类
// * 常见info打印应该使用英文，避免日志文件过大
// */
//@Component
//public class WebSocketConfig {
//    private static final Logger logger = LogManager.getLogger(WebSocketConfig.class);
//    @Autowired
//    private RedisUtils redisUtils;
//    @Autowired
//    private JavaGateway javaGateway;
//    @Autowired
//    private JdbcTemplate jdbcTemplate;
//
//    @Value("${newenergy.url}")
//    private String host;
//
//    private final Pattern pattern1 = Pattern.compile("1000/D/K_.*?/Real"); //空调检测正则
//    private final Pattern pattern2 = Pattern.compile("1000/R/\\d+/(Hour|Day|Month|Year)"); //建筑用能检测正则
//    private final Pattern pattern3 = Pattern.compile("1000/R/\\d+/Real"); //建筑负荷检测正则
//    private final Pattern pattern4 = Pattern.compile("1000/D/(.*)\\.P/Real"); //电表负荷检测正则
//    private final Pattern pattern5 = Pattern.compile("1000/D/(.*)\\.(nvoEPpos|EPsum|EPrev)/(Hour|Day|Month|Year|Real)"); //电表电量检测正则
//    private final Pattern pattern6 = Pattern.compile("1000/A/Alarm/Real"); // 告警检测正则 ?
//
//    private WebSocketClient webSocketClient;
//
//    private ExecutorService executorService = Executors.newFixedThreadPool(300); // 线程池大小，到时候看性能调整
//
//    @Bean
//    public WebSocketClient webSocketClient() {
//        try {
//            webSocketClient = new WebSocketClient(new URI(host), new Draft_6455()) {
//                @Override
//                public void onOpen(ServerHandshake handshakedata) {
//                    logger.info("websocket Connection successful");
//                }
//
//                @Override
//                public void onMessage(String message) {
////                    message = message.replaceFirst("<b>从服务端返回你发的消息：</b>", ""); // 测试专用
//                    executorService.submit(() -> {
////                        logger.info("websocket receive a message: {}", message);
//                        ObjectMapper mapper = new ObjectMapper();
//                        try {
//                            JsonNode rootNode = mapper.readTree(message);
//                            if (!redisUtils.get("NewOneStatus").equals("true")) {
//                                if (message.contains("{"result":"true","serverTime":0,"type":"Verify"}")) {
//                                    redisUtils.set("NewOneStatus", "true");
//                                    return;
//                                }
//                            }
//                            if (!rootNode.has("value")) {
//                                if (!rootNode.has("type") || !"Heart".equals(rootNode.get("type").asText())) {
//                                    logger.error("ws接收数据格式错误，请检查: {}", message);
//                                    return;
//                                }
//                                return;
//                            }
//
//                            if (rootNode.has("type") && "SetBack".equals(rootNode.get("type").asText())) {
//                                logger.info("控制设备数据: {}", message);
//                                return;
//                            }
//                        } catch (IOException e) {
//                            logger.error("解析消息时出错: {}", e.getMessage());
//                            // 处理异常
//                        }
//                        Matcher kongtiao = pattern1.matcher(message);
//                        Matcher yongneng = pattern2.matcher(message);
//                        Matcher fuhe = pattern3.matcher(message);
//                        Matcher dianbiaoFuhe = pattern4.matcher(message);
//                        Matcher dianbiaoDianliang = pattern5.matcher(message);
//                        Matcher gaojing = pattern6.matcher(message);
//                        if (kongtiao.find()) {
//                            ACP(message);
//                        } else if (yongneng.find()) {
//                            YongNeng(message);
//                        } else if (fuhe.find()) {
//                            FuHe(message);
//                        } else if (dianbiaoFuhe.find() || dianbiaoDianliang.find()) {
//                            DianBiao(message);
//                        } else if (gaojing.find()) {
//                            Alarm(message);
//                        } else {
//                            logger.warn("接收消息无效（如果此条消息输出过多请检测程序配置）消息内容: {}", message);
//                        }
//                    });
//                }
//
//                @Override
//                public void onClose(int code, String reason, boolean remote) {
//                    logger.error("websocket exit connection");
//                }
//
//                @Override
//                public void onError(Exception ex) {
//                    logger.error("websocket connection error: {}", ex.getMessage());
//                }
//            };
//            webSocketClient.connect();
//            return webSocketClient;
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    public void ACP(String message) {
//        String value = redisUtils.get("deviceCredentials");
//        if (value == null && value.isEmpty()) {
//            ObjectMapper mapper1 = new ObjectMapper();
//            try {
//                value = mapper1.writeValueAsString(getDeviceCredentials());
//                redisUtils.set("deviceCredentials", value);
//            } catch (JsonProcessingException e) {
//                throw new RuntimeException(e);
//            }
//        }
//        if (value == null && value.isEmpty()) {
//            logger.error("空调设备数据获取失败，请检查");
//            return;
//        }
//        ObjectMapper mapper = new ObjectMapper();
//        try {
//            JsonNode rootNode = mapper.readTree(message);
//            String jsonNode = rootNode.get("value").asText();
//
//            List<DeviceInfoData> retrievedDeviceInfoList = mapper.readValue(value, new TypeReference<List<DeviceInfoData>>() {
//            });
//            for (DeviceInfoData deviceInfoData : retrievedDeviceInfoList) {
//                //{"state":0,"time":"2024-05-11 17:49:04.512","topic":"1000/D/K_205_01.XRTemp/Real","type":"SubBack","value":"0"}
//                if (message.contains(deviceInfoData.getIp())) {
//                    // 对应设备
//                    JSONObject msg = new JSONObject();
//                    JSONArray nameArray = new JSONArray();
//                    JSONObject nameObject = new JSONObject();
//                    if (message.contains("ONOFF")) {
//                        // 开关
//                        if (Integer.valueOf(jsonNode) % 2 == 0){
//                            nameObject.put(deviceInfoData.getName() + "SWS001", 0);
//                        } else {
//                            nameObject.put(deviceInfoData.getName() + "SWS001", 1);
//                        }
//                    } else if (message.contains("Mode")) {
//                        // 模式
//                        nameObject.put(deviceInfoData.getName() + "MOD001", jsonNode);
//                    } else if (message.contains("Fan")) {
//                        // 风量
//                        nameObject.put(deviceInfoData.getName() + "WSD001", jsonNode);
//                    } else if (message.contains("XRTemp")) {
//                        // 房间温度
//                        nameObject.put(deviceInfoData.getName() + "TMS001", jsonNode);
//                    } else if (message.contains("Temp")) {
//                        // 设定温度
//                        nameObject.put(deviceInfoData.getName() + "TEM001", jsonNode);
//                    }
//                    nameArray.put(nameObject);
//                    msg.put(deviceInfoData.getName(), nameArray);
//                    javaGateway.javaGatewaySend(String.valueOf(msg));
//                }
//            }
//
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException(e);
//        }
//
//    }
//
//    public void YongNeng(String message) {
//        String valueYongNeng = redisUtils.get("deviceYongNeng");
//        if (valueYongNeng == null && valueYongNeng.isEmpty()) {
//            ObjectMapper mapper1 = new ObjectMapper();
//            try {
//                valueYongNeng = mapper1.writeValueAsString(getDeviceCredentialsYongNeng());
//                redisUtils.set("deviceYongNeng", valueYongNeng);
//            } catch (JsonProcessingException e) {
//                throw new RuntimeException(e);
//            }
//        }
//        if (valueYongNeng == null && valueYongNeng.isEmpty()) {
//            logger.error("建筑用能数据获取失败，请检查");
//            return;
//        }
//        ObjectMapper mapper = new ObjectMapper();
//        try {
//            JsonNode rootNode = mapper.readTree(message);
//            String jsonNode = rootNode.get("value").asText();
//            List<DeviceInfoData> retrievedDeviceInfoList = mapper.readValue(valueYongNeng, new TypeReference<List<DeviceInfoData>>() {
//            });
//            String topicData = rootNode.get("topic").asText();
//            String[] parts = topicData.split("/");
//            for (DeviceInfoData deviceInfoData : retrievedDeviceInfoList) {
//                //{"state":0,"time":"2024-05-11 17:49:04.512","topic":"1000/D/K_205_01.XRTemp/Real","type":"SubBack","value":"0"}
//
//                if (message.contains("1000/R/" + deviceInfoData.getIp()) || message.contains("1000/R/151") || message.contains("1000/R/140") || message.contains("1000/R/141")) {
//                    // 对应设备
//                    if (parts.length >= 4) {
//                        String formularId = parts[2]; // 获取FormularId的值
//                        JSONObject msg = new JSONObject();
//                        JSONArray nameArray = new JSONArray();
//                        JSONObject nameObject = new JSONObject();
//                        if (deviceInfoData.getIp().equals(formularId)) {
//                            // 市电用能
//                            if (message.contains("Hour")) {
//                                nameObject.put("Hour", jsonNode); // 每小时
//                            } else if (message.contains("Day")) {
//                                nameObject.put("Day", jsonNode); // 本日
//                            } else if (message.contains("Month")) {
//                                nameObject.put("Month", jsonNode); // 本月
//                            } else if (message.contains("Year")) {
//                                nameObject.put("Year", jsonNode); // 本年
//                            }
//                        } else if ("151".equals(formularId) && deviceInfoData.getName().equals("BED0301001")) {
//                            // 光伏发电量
//                            if (message.contains("Hour")) {
//                                nameObject.put("ppgHour", jsonNode); // 每小时
//                            } else if (message.contains("Day")) {
//                                nameObject.put("ppgDay", jsonNode); // 本日
//                            } else if (message.contains("Month")) {
//                                nameObject.put("ppgMonth", jsonNode); // 本月
//                            } else if (message.contains("Year")) {
//                                nameObject.put("ppgYear", jsonNode); // 本年
//                            }
//                        } else if ("140".equals(formularId) && deviceInfoData.getName().equals("BED0301001")) {
//                            // 储能充电量
//                            if (message.contains("Hour")) {
//                                nameObject.put("escHour", jsonNode); // 每小时
//                            } else if (message.contains("Day")) {
//                                nameObject.put("escDay", jsonNode); // 本日
//                            } else if (message.contains("Month")) {
//                                nameObject.put("escMonth", jsonNode); // 本月
//                            } else if (message.contains("Year")) {
//                                nameObject.put("escYear", jsonNode); // 本年
//                            }
//                        } else if ("141".equals(formularId) && deviceInfoData.getName().equals("BED0301001")) {
//                            // 储能放电量
//                            if (message.contains("Hour")) {
//                                nameObject.put("esdHour", jsonNode); // 每小时
//                            } else if (message.contains("Day")) {
//                                nameObject.put("esdDay", jsonNode); // 本日
//                            } else if (message.contains("Month")) {
//                                nameObject.put("esdMonth", jsonNode); // 本月
//                            } else if (message.contains("Year")) {
//                                nameObject.put("esdYear", jsonNode); // 本年
//                            }
//                        }
//                        nameArray.put(nameObject);
//                        msg.put(deviceInfoData.getName(), nameArray);
//                        if (!nameObject.isEmpty()) {
//                            javaGateway.javaGatewaySend(String.valueOf(msg));
//                        }
//                    }
//                }
//            }
//
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException(e);
//        }
//
//    }
//
//    public void FuHe(String message) {
//        String valueFuHe = redisUtils.get("deviceFuHe");
//        if (valueFuHe == null && valueFuHe.isEmpty()) {
//            ObjectMapper mapper1 = new ObjectMapper();
//            try {
//                valueFuHe = mapper1.writeValueAsString(getDeviceCredentialsFuHe());
//                redisUtils.set("deviceFuHe", valueFuHe);
//            } catch (JsonProcessingException e) {
//                throw new RuntimeException(e);
//            }
//        }
//        if (valueFuHe == null && valueFuHe.isEmpty()) {
//            logger.error("建筑负荷数据获取失败，请检查");
//            return;
//        }
//        ObjectMapper mapper = new ObjectMapper();
//        try {
//            JsonNode rootNode = mapper.readTree(message);
//            String jsonNode = rootNode.get("value").asText();
//            List<DeviceInfoData> retrievedDeviceInfoList = mapper.readValue(valueFuHe, new TypeReference<List<DeviceInfoData>>() {
//            });
//            for (DeviceInfoData deviceInfoData : retrievedDeviceInfoList) {
//                //{"state":0,"time":"2024-05-11 17:49:04.512","topic":"1000/D/K_205_01.XRTemp/Real","type":"SubBack","value":"0"}
//
//                if (message.contains("1000/R/" + deviceInfoData.getIp() + "/Real")) {
//                    // 对应设备
//                    JSONObject msg = new JSONObject();
//                    JSONArray nameArray = new JSONArray();
//                    JSONObject nameObject = new JSONObject();
//                    if (deviceInfoData.getName().equals("BLD0301001")) {
//                        nameObject.put("data", jsonNode);
//                    } else if (deviceInfoData.getName().equals("BLD0301003")) {
//                        nameObject.put("data", jsonNode);
//                    } else if (deviceInfoData.getName().equals("BLD0301004")) {
//                        nameObject.put("data", jsonNode);
//                    }
//                    nameArray.put(nameObject);
//                    msg.put(deviceInfoData.getName(), nameArray);
//                    javaGateway.javaGatewaySend(String.valueOf(msg));
//                }
//            }
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public void DianBiao(String message) {
//        String valueDianBiao = redisUtils.get("deviceDianBiao");
//        if (valueDianBiao == null && valueDianBiao.isEmpty()) {
//            ObjectMapper mapper1 = new ObjectMapper();
//            try {
//                valueDianBiao = mapper1.writeValueAsString(getDeviceCredentialsDianBiao());
//                redisUtils.set("deviceDianBiao", valueDianBiao);
//            } catch (JsonProcessingException e) {
//                throw new RuntimeException(e);
//            }
//        }
//        if (valueDianBiao == null && valueDianBiao.isEmpty()) {
//            logger.error("电表数据获取失败，请检查");
//            return;
//        }
//        ObjectMapper mapper = new ObjectMapper();
//        try {
//            JsonNode rootNode = mapper.readTree(message);
//            List<DeviceInfoData> retrievedDeviceInfoList = mapper.readValue(valueDianBiao, new TypeReference<List<DeviceInfoData>>() {
//            });
//            for (DeviceInfoData deviceInfoData : retrievedDeviceInfoList) {
//                //{"state":0,"time":"2024-05-11 17:49:04.512","topic":"1000/D/K_205_01.XRTemp/Real","type":"SubBack","value":"0"}
//                String jsonNode = rootNode.get("value").asText();
//                JSONObject msg = new JSONObject();
//                JSONArray nameArray = new JSONArray();
//                JSONObject nameObject = new JSONObject();
//                if (message.contains("1000/D/" + deviceInfoData.getIp() + ".P/Real")) {
//                    // 对应设备
//                    // 电表负荷数据
//                    nameObject.put("fhData", jsonNode);
//                    nameArray.put(nameObject);
//                    msg.put(deviceInfoData.getName(), nameArray);
//                    javaGateway.javaGatewaySend(String.valueOf(msg));
//                } else if (message.contains("1000/D/" + deviceInfoData.getIp())) {
//                    // 电表用量数据
//                    if (message.contains("Hour")) {
//                        if (message.contains("EPsum")){
//                            nameObject.put("FHourData", jsonNode);
//                        } else if (message.contains("EPrev")){
//                            nameObject.put("NHourData", jsonNode);
//                        }
//                    } else if (message.contains("Day")) {
//                        if (message.contains("EPsum")){
//                            nameObject.put("FDayData", jsonNode);
//                        } else if (message.contains("EPrev")) {
//                            nameObject.put("NDayData", jsonNode);
//                        }
//                    } else if (message.contains("Month")) {
//                        if (message.contains("EPsum")){
//                            nameObject.put("FMonthData", jsonNode);
//                        } else if (message.contains("EPrev")) {
//                            nameObject.put("NMonthData", jsonNode);
//                        }
//                    } else if (message.contains("Year")) {
//                        if (message.contains("EPsum")){
//                            nameObject.put("FYearData", jsonNode);
//                        } else if (message.contains("EPrev")) {
//                            nameObject.put("NYearData", jsonNode);
//                        }
//                    } else if (message.contains("Real")) {
//                        if (message.contains("EPsum")){
//                            nameObject.put("FData", jsonNode);
//                        } else  if (message.contains("EPrev")){
//                            nameObject.put("NData", jsonNode);
//                        }
//                    }
//                    nameArray.put(nameObject);
//                    msg.put(deviceInfoData.getName(), nameArray);
//                    javaGateway.javaGatewaySend(String.valueOf(msg));
//                }
//            }
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public void Alarm(String message){
//        logger.info("报警数据输出: {}", message);
//        ObjectMapper mapper = new ObjectMapper();
//        try {
//            JsonNode rootNode = mapper.readTree(message);
//            String jsonNode = rootNode.get("value").asText();
//            JSONObject msg = new JSONObject();
//            JSONArray nameArray = new JSONArray();
//            JSONObject nameObject = new JSONObject();
//            nameObject.put("alarm", jsonNode);
//            nameArray.put(nameObject);
//            msg.put("NEM0101001", nameArray);
//            javaGateway.javaGatewaySend(String.valueOf(msg));
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    @PreDestroy
//    public void destroyWebSocketClient() {
//        logger.info("程序停止需要等待连接正确关闭并且线程池处理完毕，请耐心等待，超时时间60秒。");
//        // 关闭WebSocket连接
//        if (webSocketClient != null) {
//            // 这里使用WebSocketClient的适当方法来关闭连接
//            webSocketClient.close(); // 这取决于你使用的WebSocketClient库
//            logger.info("@PreDestroy -> close ws connection");
//        }
//
//        // 关闭线程池
//        executorService.shutdown(); // 不再接受新任务
//        try {
//            // 等待一段时间让已提交的任务完成
//            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
//                // 如果超时，则强制关闭线程池
//                executorService.shutdownNow();
//                logger.warn("@PreDestroy -> 超时！强制关闭线程池");
//            }
//        } catch (InterruptedException e) {
//            // 处理中断异常
//            executorService.shutdownNow();
//            Thread.currentThread().interrupt();
//        }
//        logger.info("@PreDestroy -> close thread pool");
//    }
//
//
//    private List<DeviceInfoData> getDeviceCredentials() {
//        String sql = "SELECT name,label AS ip,type AS address FROM device WHERE name LIKE '%ACP%'";
//        List<DeviceInfoData> deviceCredentials = jdbcTemplate.query(sql, new RowMapper<DeviceInfoData>() {
//            @Override
//            public DeviceInfoData mapRow(ResultSet rs, int rowNum) throws SQLException {
//                String name = rs.getString("name");
//                String ip = rs.getString("ip");
//                String address = rs.getString("address");
//                return new DeviceInfoData(name, ip, address);
//            }
//        });
//
//        return deviceCredentials;
//    }
//
//
//    private List<DeviceInfoData> getDeviceCredentialsYongNeng() {
//        String sql = "SELECT name,label AS ip,type AS address FROM device WHERE name LIKE '%BED%'";
//        List<DeviceInfoData> deviceCredentials = jdbcTemplate.query(sql, new RowMapper<DeviceInfoData>() {
//            @Override
//            public DeviceInfoData mapRow(ResultSet rs, int rowNum) throws SQLException {
//                String name = rs.getString("name");
//                String ip = rs.getString("ip");
//                String address = rs.getString("address");
//                return new DeviceInfoData(name, ip, address);
//            }
//        });
//
//        return deviceCredentials;
//    }
//
//
//    private List<DeviceInfoData> getDeviceCredentialsFuHe() {
//        String sql = "SELECT name,label AS ip,type AS address FROM device WHERE name LIKE '%BLD%'";
//        List<DeviceInfoData> deviceCredentials = jdbcTemplate.query(sql, new RowMapper<DeviceInfoData>() {
//            @Override
//            public DeviceInfoData mapRow(ResultSet rs, int rowNum) throws SQLException {
//                String name = rs.getString("name");
//                String ip = rs.getString("ip");
//                String address = rs.getString("address");
//                return new DeviceInfoData(name, ip, address);
//            }
//        });
//
//        return deviceCredentials;
//    }
//
//    private List<DeviceInfoData> getDeviceCredentialsDianBiao() {
//        String sql = "SELECT name,label AS ip,type AS address FROM device WHERE name LIKE '%NEM%'";
//        List<DeviceInfoData> deviceCredentials = jdbcTemplate.query(sql, new RowMapper<DeviceInfoData>() {
//            @Override
//            public DeviceInfoData mapRow(ResultSet rs, int rowNum) throws SQLException {
//                String name = rs.getString("name");
//                String ip = rs.getString("ip");
//                String address = rs.getString("address");
//                return new DeviceInfoData(name, ip, address);
//            }
//        });
//
//        return deviceCredentials;
//    }
//
//}