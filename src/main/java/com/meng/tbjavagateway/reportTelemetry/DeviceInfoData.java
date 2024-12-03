package com.meng.tbjavagateway.reportTelemetry;

public class DeviceInfoData {

    private String name;
    private String ip;
    private String address;

    public void setName(String name) {
        this.name = name;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public DeviceInfoData(String name, String ip, String address) {
        this.name = name;
        this.ip = ip;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public String getIp() {
        return ip;
    }

    public DeviceInfoData() {
        // 初始化代码（如果有需要的话）
    }

}
