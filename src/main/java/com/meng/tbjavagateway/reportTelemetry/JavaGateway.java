package com.meng.tbjavagateway.reportTelemetry;

import com.meng.tbjavagateway.config.RedisUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import java.io.IOException;

/**
 * @Date: 2024/5/29
 * @Author: mengGod
 * @Description: tb网关类
 * 常见info打印应该使用英文，避免日志文件过大
 */
public class JavaGateway {
    private static final Logger logger = LogManager.getLogger(JavaGateway.class);
    private MqttClient mqttClient;
    private RedisUtils redisUtils;
    private MqttCallback mqttCallback;
    private String topic = "v1/gateway/telemetry";
    private String topicDing = "v1/gateway/rpc";
    private String topicConnect = "v1/gateway/connect";

    @Value("${mqtt.host}")
    private String host;

    @Value("${mqtt.port}")
    private String port;

    @Value("${mqtt.username}")
    private String username;

    @PostConstruct
    public void initializeConnections() {
        try {
            mqttClient = new MqttClient("tcp://" + host + ":" + port, username);

            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setUserName(username);
            connectOptions.setPassword(username.toCharArray());

            // 设置回调接口
            mqttCallback = new MqttMessageHandler();
            mqttClient.setCallback(mqttCallback);

            // 连接
            mqttClient.connect(connectOptions);

            // 订阅主题
            mqttClient.subscribe(topicDing);

            logger.info("Java Gateway connection creation complete");
        } catch (MqttException e) {
            logger.error("创建 MQTT 连接时发生错误: {}", e);
            // 处理创建连接的异常
            e.printStackTrace();
        }
    }

    public void dailyRestart() {
        redisUtils.set("exceptionNumber", "0");
        try {
            Runtime.getRuntime().exec("sudo systemctl restart javaGateway.service");
            System.exit(0); // 退出当前进程
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void javaGatewaySend(String message) {
        try {
            if (!mqttClient.isConnected()) {
                logger.warn("Client is not connected, attempting to reconnect: {}", mqttClient.getClientId());
                handleMqttException(new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED));
            } else {
                mqttClient.publish(topic, message.getBytes(), 0, false);
            }
        } catch (MqttException e) {
            logger.error("send线程出现异常: {} /n 异常原因: {}", mqttClient, e);
            handleMqttException(e);
        }
    }

    public void javaGatewayConnect(String message) {
        try {
            mqttClient.publish(topicConnect, message.getBytes(), 0, false);
            logger.info("Successful initialization: {}", message);
        } catch (MqttException e) {
            logger.error("connect线程出现异常: {} /n 异常原因: {}", mqttClient, e);
            handleMqttException(e);
        }
    }

    private void handleMqttException(MqttException e) {
        if (e.getReasonCode() == MqttException.REASON_CODE_CLIENT_NOT_CONNECTED) {
            logger.warn("Connection lost, attempting to reconnect: {}", mqttClient.getClientId());
            try {
                mqttClient.reconnect();
                int s = Integer.parseInt(redisUtils.get("exceptionNumber"));
                if (s == 10){
                    logger.error(">>> 系统检测到连接异常，即将自动重启程序! <<<");
                    dailyRestart();
                }
                redisUtils.set("exceptionNumber", String.valueOf(s+1));
                logger.info("Reconnection successful: {}", mqttClient.getClientId());
            } catch (MqttException ex) {
                logger.error("Error during MQTT reconnection: {}", ex);
            }
        } else {
            logger.error("Other MQTT exception occurred during message sending: {}", e);
        }
    }

    private void restartApplication() {
        try {
            logger.error("Restarting the application due to MQTT connection issues.");
            Runtime.getRuntime().exec("sudo systemctl restart javaGateway.service");
            System.exit(0); // 退出当前进程
        } catch (IOException e) {
            logger.error("Failed to restart the application: {}", e);
        }
    }
}