package com.meng.tbjavagateway.reportTelemetry;

import com.meng.tbjavagateway.lms.DeviceForLms;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MqttMessageHandler implements MqttCallback {

    @Autowired
    private DeviceForLms deviceForLms;

    @Override
    public void connectionLost(Throwable cause) {
        // 处理连接丢失的情况
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        // 处理接收到的订阅消息
        System.out.println("收到订阅消息：" + new String(message.getPayload()));
        String msg = new String(message.getPayload());
        JSONObject jsonObject = new JSONObject(msg);

        String device = jsonObject.getString("device");
        JSONObject data = jsonObject.getJSONObject("data");

        if (device.contains("AAA")){
            String method = data.getString("method");
            Boolean params = data.getBoolean("params");
            int off = Integer.parseInt(method.substring(method.length() - 1));
            List<Number> values = new ArrayList<>();
            values.add(params ? 1 : 0); // 控制第一个开关的值
            if (deviceForLms != null) {
                deviceForLms.kongzhi(off - 1, values);
            } else {
                // 处理deviceForLms为null的情况
                System.out.println("控制命令下发失败");
            }

        }
        // 在这里添加处理订阅消息的代码
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 处理消息传递完成的情况
    }
}
