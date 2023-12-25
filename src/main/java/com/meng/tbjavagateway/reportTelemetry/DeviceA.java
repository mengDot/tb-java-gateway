package com.meng.tbjavagateway.reportTelemetry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import java.util.List;

public class DeviceA {
    private List<String> deviceCredentials;
    private Boolean loading = false;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JavaGateway javaGateway;

    @PostConstruct
    public void initializeConnections() {
        deviceCredentials = getDeviceCredentials();
        if (!deviceCredentials.isEmpty()){
            for (String name : deviceCredentials) {
                JSONObject msg = new JSONObject();
                msg.put("device",name);
                javaGateway.javaGatewayConnect(String.valueOf(msg));
            }
            loading = true;
        }
    }


    @Scheduled(fixedRate = 5000) // 每隔5秒执行一次
    public void sendMessage() {
        if (loading){
            for (String name : deviceCredentials) {
                JSONObject msg = new JSONObject();
                JSONArray nameArray = new JSONArray();
                JSONObject nameObject = new JSONObject();
                nameObject.put("online", 1);
                nameArray.put(nameObject);
                msg.put(name, nameArray);
                javaGateway.javaGatewaySend(String.valueOf(msg));
            }
        }
    }

    private List<String> getDeviceCredentials() {
        return jdbcTemplate.queryForList("SELECT name\n" +
                "FROM device\n" +
                "WHERE name LIKE '%deviceA%'", String.class);
    }


}
