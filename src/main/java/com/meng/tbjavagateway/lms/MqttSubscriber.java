package com.meng.tbjavagateway.lms;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttSubscriber {
    public static void main(String[] args) {
        String broker = "tcp://10.67.181.66:1884"; // MQTT Broker的地址
        String clientId = "860bedc0-9e13-11ee-bac9-d7f2318bcc74"; // 客户端ID
        String telemetryTopic = "v1/gateway/telemetry"; // 遥测主题

        try {
            MqttClient mqttClient = new MqttClient(broker, clientId, new MemoryPersistence());
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setUserName("cdOISd2ZSWnePlfgmMxI");

            mqttClient.connect(connOpts);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("连接丢失");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    System.out.println("收到消息：" + new String(message.getPayload()));
                    // 在这里处理接收到的遥测数据
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // 消息发布完成
                }
            });

            mqttClient.subscribe(telemetryTopic);

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
