package com.meng.tbjavagateway;

import com.meng.tbjavagateway.lms.DeviceForLms;
import com.meng.tbjavagateway.reportTelemetry.DeviceA;
import com.meng.tbjavagateway.reportTelemetry.JavaGateway;
import com.meng.tbjavagateway.reportTelemetry.MqttMessageHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
@ComponentScan("com.meng.tbjavagateway.*")
public class DeviceConfiguration {
    @Bean
    public JavaGateway javaGateway() {
        return new JavaGateway();
    }

//    @Bean
//    public DeviceA deviceA() {
//        return new DeviceA();
//    }

}
