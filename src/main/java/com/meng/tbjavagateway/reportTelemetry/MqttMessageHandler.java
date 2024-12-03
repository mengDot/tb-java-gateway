package com.meng.tbjavagateway.reportTelemetry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meng.tbjavagateway.config.RedisUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.java_websocket.client.WebSocketClient;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @Date: 2024/5/29
 * @Author: mengGod
 * @Description: tb网关回调类
 * 常见info打印应该使用英文，避免日志文件过大
 */
@Component
public class MqttMessageHandler implements MqttCallback {
    private static final Logger logger = LogManager.getLogger(MqttMessageHandler.class);

    private static RedisUtils redisUtils;
    @Autowired
    public void setRedisTemplate(RedisUtils redisUtils){
        MqttMessageHandler.redisUtils = redisUtils;
    }

    private static JdbcTemplate jdbcTemplate;
    @Autowired
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate){
        MqttMessageHandler.jdbcTemplate = jdbcTemplate;
    }

    private static WebSocketClient webSocketHandlerByAcp;
    @Autowired
    @Qualifier("webSocketClientByAcp")
    @Lazy
    public void setWebSocketClient(WebSocketClient webSocketHandlerByAcp){
        MqttMessageHandler.webSocketHandlerByAcp = webSocketHandlerByAcp;
    }



    @Scheduled(fixedRate = 60000) // 每xx秒执行一次
    public void to() {
        webSocketHandlerByAcp.send("test");
    }

    @Override
    public void connectionLost(Throwable cause) {
        // 处理连接丢失的情况
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        // 处理接收到的订阅消息
//        logger.info("tbMqtt received a message: {}", new String(message.getPayload()));
        String msg = new String(message.getPayload());
        JSONObject jsonObject = new JSONObject(msg);

        String device = jsonObject.getString("device");
        JSONObject data = jsonObject.getJSONObject("data");
        if (device.contains("ACP")) {
            String method = data.getString("method");
            int params = data.getInt("params");
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

            ObjectMapper mapper = new ObjectMapper();
            List<DeviceInfoData> retrievedDeviceInfoList = null;
            try {
                retrievedDeviceInfoList = mapper.readValue(value, new TypeReference<List<DeviceInfoData>>() {
                });
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            for (DeviceInfoData deviceInfoData : retrievedDeviceInfoList) {
                if (deviceInfoData.getName().equals(device)){
                    logger.info("到达控制程序, 数据为: {}", msg);
                    if (method.contains("SWS")){
                        webSocketHandlerByAcp.send("{\"type\":\"Set\",\"topic\":\"1000/D/"+deviceInfoData.getIp()+".ONOFFSet/Real\",\"value\":\""+params+"\"}");
                    } else if (method.contains("MOD")) {
                        // 模式
                        webSocketHandlerByAcp.send("{\"type\":\"Set\",\"topic\":\"1000/D/"+deviceInfoData.getIp()+".ModeSet/Real\",\"value\":\""+params+"\"}");
                    } else if (method.contains("WSD")) {
                        // 风量
                        webSocketHandlerByAcp.send("{\"type\":\"Set\",\"topic\":\"1000/D/"+deviceInfoData.getIp()+".FanSet/Real\",\"value\":\""+params+"\"}");
                    } else if (method.contains("TEM")) {
                        // 设定温度
                        webSocketHandlerByAcp.send("{\"type\":\"Set\",\"topic\":\"1000/D/"+deviceInfoData.getIp()+".TempSet/Real\",\"value\":\""+params+"\"}");
                    }
                }
            }
            logger.info("控制数据发送成功");

        } else if (device.contains("LMS")) {
            String method = data.getString("method");
            int params = data.getInt("params");
            String value = redisUtils.get("deviceLms");
            if (value == null && value.isEmpty()) {
                ObjectMapper mapper1 = new ObjectMapper();
                try {
                    value = mapper1.writeValueAsString(getDeviceCredentialsLms());
                    redisUtils.set("deviceLms", value);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }

            ObjectMapper mapper = new ObjectMapper();
            List<DeviceInfoData> retrievedDeviceInfoList = null;
            try {
                retrievedDeviceInfoList = mapper.readValue(value, new TypeReference<List<DeviceInfoData>>() {
                });
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            for (DeviceInfoData deviceInfoData : retrievedDeviceInfoList) {
                if (deviceInfoData.getName().equals(device)){
                    if (method.contains("SWS001")){
                        webSocketHandlerByAcp.send("{\"type\":\"Set\",\"topic\":\"1000/D/"+deviceInfoData.getIp()+".DO1/Real\",\"value\":\""+params+"\"}");
                    } else if (method.contains("SWS002")) {
                        webSocketHandlerByAcp.send("{\"type\":\"Set\",\"topic\":\"1000/D/"+deviceInfoData.getIp()+".DO2/Real\",\"value\":\""+params+"\"}");
                    } else if (method.contains("SWS003")) {
                        webSocketHandlerByAcp.send("{\"type\":\"Set\",\"topic\":\"1000/D/"+deviceInfoData.getIp()+".DO3/Real\",\"value\":\""+params+"\"}");
                    } else if (method.contains("SWS004")) {
                        webSocketHandlerByAcp.send("{\"type\":\"Set\",\"topic\":\"1000/D/"+deviceInfoData.getIp()+".DO4/Real\",\"value\":\""+params+"\"}");
                    }
                }
            }
        }
        // 在这里添加处理订阅消息的代码
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 处理消息传递完成的情况
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
}
