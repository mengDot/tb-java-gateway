package com.meng.tbjavagateway.reportTelemetry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;


/**
 * @Date: 2024/5/29
 * @Author: mengGod
 * @Description: 摄像头ping检测类
 * 常见info打印应该使用英文，避免日志文件过大
 */
public class DeviceA {
    private static final Logger logger = LogManager.getLogger(DeviceA.class);
    private List<DeviceInfoData> deviceCredentials;
    private Boolean loading = false;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JavaGateway javaGateway;



    private String serverAddress = "172.16.0.9";
    private int serverPort = 223;
    private int bufferSize = 1000;
    private volatile boolean running = false;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @PostConstruct
    public void initializeConnections() {
        System.out.println(1);
//        deviceCredentials = getDeviceCredentials();
//        if (!deviceCredentials.isEmpty()){
//            // 消防主机单独注入 后加 催得要死 就直接在这写了
//            JSONObject msg1 = new JSONObject();
//            msg1.put("device", "FDC0101001");
//            javaGateway.javaGatewayConnect(String.valueOf(msg1));
//            for (DeviceInfoData deviceInfoData : deviceCredentials) {
//                JSONObject msg = new JSONObject();
//                msg.put("device",deviceInfoData.getName());
//                javaGateway.javaGatewayConnect(String.valueOf(msg));
//            }
//            startListening();
//            loading = true;
//        }
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


    public void sendMessage() {for (DeviceInfoData deviceInfoData : deviceCredentials) {
        executorService.submit(() -> {
            JSONObject msg = new JSONObject();
            JSONArray nameArray = new JSONArray();
            JSONObject nameObject = new JSONObject();

            if (deviceInfoData.getIp() != null) {
                boolean isReachable = isHostReachable(deviceInfoData.getIp());
                if (isReachable) {
                    nameObject.put("online", 1);
                } else {
                    nameObject.put("online", 0);
                    // 无法ping的摄像头 打开即输出
//                    logger.warn("Unable to ping: {}, deviceName: {}", deviceInfoData.getIp(), deviceInfoData.getName());
                    // 不能访问的摄像头输出到文件（自动去重），需要解开
//                    out(deviceInfoData.getName(), deviceInfoData.getIp(), deviceInfoData.getAddress());
//                    logError(deviceInfoData.getName() + "无法访问！");
                }
            } else {
                nameObject.put("online", 0);
                logError(deviceInfoData.getName() + "的IP为空！");
            }

            nameArray.put(nameObject);
            msg.put(deviceInfoData.getName(), nameArray);
            sendJavaGatewayMessage(String.valueOf(msg));
        });
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

    public void startListening() {
        if (!running) {
            running = true;
            Thread listenThread = new Thread(() -> {
                try (Socket socket = new Socket(serverAddress, serverPort)) {
                    InputStream inputStream = socket.getInputStream();
                    byte[] buffer = new byte[bufferSize];
                    while (running) {
                        int bytesRead = inputStream.read(buffer);
                        if (bytesRead != -1) {
                            String hexString = bytesToHex(buffer, bytesRead);
                            String[] parts = hexString.split(" ");

                            // 检查数组长度是否足够
                            if (parts.length > 2) {
                                // 判断第二个和第三个元素是否为"38"和"30"
                                boolean isCorrect = "38".equals(parts[1]) && "30".equals(parts[2]);

                                JSONObject msg = new JSONObject();
                                JSONArray nameArray = new JSONArray();
                                JSONObject nameObject = new JSONObject();
                                if (isCorrect) {
//                                    System.out.println("火警");
                                    System.out.println("消防数据接收(十六进制): " + hexString);
                                    // 第4位到第5位
                                    String machineHex = extractIndividualDigits(parts[3]) + extractIndividualDigits(parts[4]);
                                    int machineNumber = Integer.parseInt(machineHex, 16);
//                                System.out.println("这是" + machineNumber + "号机，取的是" + machineHex.toUpperCase() + "十六进制");

                                    // 第6位到第7位
                                    String loopHex = extractIndividualDigits(parts[5]) + extractIndividualDigits(parts[6]);
                                    int loopNumber = Integer.parseInt(loopHex, 16);
//                                System.out.println("这是" + loopNumber + "号回路，取的是" + loopHex.toUpperCase() + "十六进制");

                                    // 第8位到第9位
                                    String addressHex = extractIndividualDigits(parts[7]) + extractIndividualDigits(parts[8]);
                                    int addressNumber = Integer.parseInt(addressHex, 16);
//                                System.out.println("这是" + addressNumber + "地址，取的是" + addressHex.toUpperCase() + "十六进制");
                                    System.out.println(machineNumber + "号机" + loopNumber + "号回路" + addressNumber + "地址触发火警!");
                                    JSONObject xfMsg = new JSONObject();
                                    JSONObject xfMsg1 = new JSONObject();
                                    xfMsg.put("alarmHistoryId", "null");
                                    xfMsg.put("deviceName", "FDC0101001");
                                    xfMsg.put("class", "fireAlarm");
                                    xfMsg.put("info", machineNumber + "号机" + loopNumber + "号回路" + addressNumber + "地址触发火警!");
                                    xfMsg.put("machineNumber", machineNumber);
                                    xfMsg.put("loopNumber", loopNumber);
                                    xfMsg.put("addressNumber", addressNumber);
                                    xfMsg.put("level", 3);
                                    xfMsg.put("alarmLevel", 3);
                                    xfMsg.put("ts", System.currentTimeMillis() / 1000);
                                    xfMsg.put("eventTs", System.currentTimeMillis() / 1000);
                                    xfMsg.put("alarmClass", "fireAlarm");
                                    xfMsg.put("clabel", "消防火警");
                                    xfMsg1.put("imagePath", "http://172.16.0.4:81/xiaofang/xuni/tupian/"+ UUID.randomUUID() + System.currentTimeMillis() / 1000+".jpg");
                                    xfMsg.put("additionalInfo", xfMsg1);

                                    nameObject.put("alarm", xfMsg);
                                } else {
                                    nameObject.put("online", 1);
                                }
                                nameArray.put(nameObject);
                                msg.put("FDC0101001", nameArray);
                                sendJavaGatewayMessage(String.valueOf(msg));
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            listenThread.start();
        }
    }

    public void stopListening() {
        running = false;
    }

    private static String bytesToHex(byte[] bytes, int length) {
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex).append(' ');
        }
        return hexString.toString().trim(); // 去掉末尾的空格
    }

    private static String extractIndividualDigits(String hexValue) {
        // 提取十六进制值的个位数
        return hexValue.substring(hexValue.length() - 1);
    }

    // 其他方法省略...

    // 确保在应用程序关闭时关闭线程池
    @PreDestroy
    public void shutdownExecutor() {
        executorService.shutdown();
    }

    private List<DeviceInfoData> getDeviceCredentials() {
        String sql = "SELECT name,properties->>'deviceIp' AS ip,device_address AS address FROM device WHERE name LIKE '%MC%' AND name != 'CMC0101001'";
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

    public static boolean isHostReachable(String ipAddress) {
        try {
            InetAddress inetAddress = InetAddress.getByName(ipAddress);
            // 设置超时时间和尝试次数
            return inetAddress.isReachable(2000); // 5秒超时
        } catch (Exception e) {
            return false;
        }
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
            logger.error("读取文件时发生错误: {}", e);
            e.printStackTrace();
            return false;
        }

        // 检查内容是否已存在于文件中
        if (fileContent.toString().contains(content)) {
            logger.info("数据已存在于文件中，不再追加: {}", content);
            return false; // 数据已存在，不追加
        }

        // 追加内容到文件
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME, true))) {
            writer.write(content);
            writer.newLine(); // 写入换行符
            logger.info("字符串已追加到文件: {}", content);
            return true; // 数据已追加
        } catch (IOException e) {
            logger.error("写入文件时发生错误: {}", e);
            e.printStackTrace();
            return false;
        }
    }


}
