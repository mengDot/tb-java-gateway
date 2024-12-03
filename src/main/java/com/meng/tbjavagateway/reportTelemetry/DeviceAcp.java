package com.meng.tbjavagateway.reportTelemetry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meng.tbjavagateway.config.RedisUtils;
import com.meng.tbjavagateway.websocket.acp.WebSocketClientUpdateByAcp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.client.WebSocketClient;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Date: 2024/5/29
 * @Author: mengGod
 * @Description: 新能源空调类
 * 常见info打印应该使用英文，避免日志文件过大
 */
public class DeviceAcp implements WebSocketClientUpdateByAcp {
    private static final Logger logger = LogManager.getLogger(DeviceAcp.class);
    private List<DeviceInfoData> deviceCredentials;
    private Boolean loading = false;

    @Value("${newenergy.url}")
    private String host;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JavaGateway javaGateway;
    @Autowired
    @Qualifier("webSocketClientByAcp")
    @Lazy
    private WebSocketClient webSocketHandlerByAcp;
    @Autowired
    private RedisUtils redisUtils;

    private Integer start = 0;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @PostConstruct
    public void initializeConnections() {
        deviceCredentials = getDeviceCredentials();
        redisUtils.set("NewOneStatusByAcp", "false");
        ObjectMapper mapper = new ObjectMapper();
        try {
            String jsonString = mapper.writeValueAsString(deviceCredentials);
            redisUtils.set("deviceCredentials", jsonString);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        if (!deviceCredentials.isEmpty()){
            for (DeviceInfoData deviceInfoData : deviceCredentials) {
                JSONObject msg = new JSONObject();
                msg.put("device",deviceInfoData.getName());
                javaGateway.javaGatewayConnect(String.valueOf(msg));
            }
            loading = true;
        }
    }

    @Scheduled(fixedRate = 60000) // 每xx秒执行一次
    public void to() {
        if (loading){
            Runnable messageSenderTask = () -> {
                this.sendMessage();
            };
            // 提交任务到线程池
            executorService.submit(messageSenderTask);
        }
    }


    public void sendMessage() {
        Boolean value = Boolean.valueOf(redisUtils.get("NewOneStatusByAcp"));
        if (value && start != 0) {
            WebSocketClient currentAcpClient = webSocketHandlerByAcp;
            for (DeviceInfoData deviceInfoData : deviceCredentials) {
                currentAcpClient.send("{\"type\":\"Sub\",\"topic\":\"1000/D/"+deviceInfoData.getIp()+".ONOFF/Real\"}");
                currentAcpClient.send("{\"type\":\"Sub\",\"topic\":\"1000/D/"+deviceInfoData.getIp()+".Mode/Real\"}");
                currentAcpClient.send("{\"type\":\"Sub\",\"topic\":\"1000/D/"+deviceInfoData.getIp()+".Fan/Real\"}");
                currentAcpClient.send("{\"type\":\"Sub\",\"topic\":\"1000/D/"+deviceInfoData.getIp()+".Temp/Real\"}");
                currentAcpClient.send("{\"type\":\"Sub\",\"topic\":\"1000/D/"+deviceInfoData.getIp()+".XRTemp/Real\"}");
            }
        } else {
//            webSocketHandlerByAcp.send("{\"type\":\"Verify\",\"token\":\"1000:GFKD-ZHGLPT\",\"mode\":\"CSharp\"}");
            start = 1;
        }
    }

    private void logError(String errorMessage) {
        // 使用日志记录框架记录错误，例如SLF4J或Log4j
        logger.error(errorMessage);
    }

    private void sendJavaGatewayMessage(String message) {
        // 发送消息到JavaGateway，可能需要异常处理
        javaGateway.javaGatewaySend(message);
    }

    // 其他方法省略...

    // 确保在应用程序关闭时关闭线程池
    @PreDestroy
    public void shutdownExecutor() {
        executorService.shutdown();
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

    private static final String FILE_NAME = "output.txt";
    public static boolean out(String name,String ip,String add) {
        String content = ip + " --> " + add;
        if (name.contains("AMC")) {
            content = ip + " --> " + add + "(门禁上摄像头)";
        }
        // 读取文件内容到字符串中
        StringBuilder fileContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(FILE_NAME))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line).append(System.lineSeparator()); // 使用系统相关的换行符
            }
        } catch (IOException e) {
            logger.error("读取文件时发生错误:{}", e);
            e.printStackTrace();
            return false;
        }

        // 检查内容是否已存在于文件中
        if (fileContent.toString().contains(content)) {
            logger.info("数据已存在于文件中，不再追加:{}", content);
            return false; // 数据已存在，不追加
        }

        // 追加内容到文件
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME, true))) {
            writer.write(content);
            writer.newLine(); // 写入换行符
            logger.info("字符串已追加到文件:{}", content);
            return true; // 数据已追加
        } catch (IOException e) {
            logger.error("写入文件时发生错误:{}", e);
            e.printStackTrace();
            return false;
        }
    }


    @Override
    public void updateWebSocketClient(WebSocketClient newClient) {
        this.webSocketHandlerByAcp = newClient;
    }
}
