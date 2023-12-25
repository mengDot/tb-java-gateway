package com.meng.tbjavagateway.reportTelemetry;

import org.eclipse.paho.client.mqttv3.*;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class JavaGateway {
    private List<MqttClient> mqttConnections;
    private CountDownLatch connectionLatch;
    private MqttCallback mqttCallback;
    private MqttClient mqttClient;
    private String topic = "v1/gateway/telemetry";
    private String topicDing = "v1/gateway/rpc";
    private String topicConnect = "v1/gateway/connect";

    @PostConstruct
    public void initializeConnections() {
        mqttConnections = new ArrayList<>();
        connectionLatch = new CountDownLatch(1);
        mqttCallback = new MqttMessageHandler(); // 创建 MqttMessageHandler 实例
        System.out.println("Java Gateway网关开始创建连接");
        try {

            MqttClient mqttClient = new MqttClient("tcp://10.67.181.66:1884", "java");

            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setUserName("java");
            connectOptions.setPassword("java".toCharArray());
            mqttClient.connect(connectOptions);
            System.out.println("Java Gateway网关连接创建完成");
            // 设置回调接口
            mqttClient.setCallback(mqttCallback);

            // 订阅主题
            mqttClient.subscribe(topicDing);

            mqttConnections.add(mqttClient);
            connectionLatch.countDown(); // 连接创建完成，计数减一
        } catch (MqttException e) {
            // 处理创建连接的异常
            e.printStackTrace();
            System.out.println("创建 MQTT 连接时发生错误" + e);
        }

        try {
            connectionLatch.await(); // 所有连接创建完成
            mqttClient = mqttConnections.get(0);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void javaGatewaySend(String message){
        try {
            mqttClient.publish(topic, message.getBytes(), 0, false);
            System.out.println("遥测上报成功：" + message);
        } catch (MqttException e) {
            // 处理发送消息的异常
            System.out.println("线程出现异常：" + mqttClient + "/n 异常原因：" + e);
            handleMqttException(mqttClient, e);
        }
    }

    public void javaGatewayConnect(String message){
        try {
            mqttClient.publish(topicConnect, message.getBytes(), 0, false);
            System.out.println("初始化成功：" + message);
        } catch (MqttException e) {
            // 处理发送消息的异常
            System.out.println("线程出现异常：" + mqttClient + "/n 异常原因：" + e);
            handleMqttException(mqttClient, e);
        }
    }

    private void handleMqttException(MqttClient mqttClient, MqttException e) {
        if (e.getReasonCode() == MqttException.REASON_CODE_CLIENT_NOT_CONNECTED) {
            System.out.println(mqttClient.getClientId() + " 连接丢失，进行重新连接");
            try {
                mqttClient.reconnect();
                System.out.println(mqttClient.getClientId() + " 重新连接成功");
            } catch (MqttException ex) {
                ex.printStackTrace();
                System.out.println("重新连接 MQTT 连接时发生错误：" + ex);
            }
        } else {
            e.printStackTrace();
            System.out.println("发送消息时发生其他 MQTT 异常：" + e);
        }
    }
}
