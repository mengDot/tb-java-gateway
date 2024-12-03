package com.meng.tbjavagateway;

import com.meng.tbjavagateway.lms.DeviceForLms;
import com.meng.tbjavagateway.reportTelemetry.*;
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

    @Bean
    public DeviceA deviceA() {
        return new DeviceA();
    }

    @Bean
    public DeviceAcp deviceAcp() {
        return new DeviceAcp();
    }

    @Bean
    public DeviceFuHe deviceFuHe() {
        return new DeviceFuHe();
    }

    @Bean
    public DeviceYongNeng deviceYongNeng() {
        return new DeviceYongNeng();
    }

    @Bean
    public DeviceAlarm deviceAlarm() {
        return new DeviceAlarm();
    }

    @Bean
    public DeviceDianBiao deviceDianBiao() {
        return new DeviceDianBiao();
    }

    @Bean
    public DeviceLms deviceLms() {
        return new DeviceLms();
    }

}
