package com.meng.tbjavagateway.lms;

import com.intelligt.modbus.jlibmodbus.Modbus;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;
import com.meng.tbjavagateway.reportTelemetry.JavaGateway;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DeviceForLms {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JavaGateway javaGateway;

    private List<Map<String, Object>> deviceCredentials;
    private List<Map<String, Object>> getDeviceTelemetry;
    private ModbusMaster modbusMaster;
    private int slaveId;
    private int quantity;
    private boolean loading = false;

    @PostConstruct
    public void initializeConnections() {
        deviceCredentials = getDeviceCredentials();
        getDeviceTelemetry = getDeviceTelemetry();
        for (Map<String, Object> deviceInfo : deviceCredentials) {
            String ipAddress = (String) deviceInfo.get("ip");
            int port = (int) deviceInfo.get("port");
            slaveId = (int) deviceInfo.get("slave_id");
            quantity = (int) deviceInfo.get("quantity");

            modbusMaster = getModbusMaster(ipAddress, port);
            System.out.println("连接成功：" + modbusMaster);

            List<Number> values = readHoldingRegisters(modbusMaster, slaveId , 0, quantity);
            if (values.size() != quantity){
                System.out.println("位数设置与读取不一致，请检查设备。");
            } else {
                System.out.println(values);
                JSONObject msg = new JSONObject();
                msg.put("device", deviceInfo.get("name"));
                javaGateway.javaGatewayConnect(String.valueOf(msg));
                loading = true;
            }
        }
    }

    @Scheduled(fixedRate = 5000) // 每隔5秒执行一次
    public void sendMessage() {
        if (loading){
            for (Map<String, Object> deviceInfo : deviceCredentials) {
                String ipAddress = (String) deviceInfo.get("ip");
                int port = (int) deviceInfo.get("port");
                int slaveId = (int) deviceInfo.get("slave_id");
                int quantity = (int) deviceInfo.get("quantity");

                ModbusMaster modbusMaster = getModbusMaster(ipAddress, port);

                List<Number> values = readHoldingRegisters(modbusMaster, slaveId , 0, quantity);
                if (values.size() != quantity || values.size() != getDeviceTelemetry.size()){
                    System.out.println("位数设置与读取不一致，请检查设备。");
                } else {
                    System.out.println(values);

                    JSONObject msg = new JSONObject();
                    JSONArray nameArray = new JSONArray();
                    JSONObject nameObject = new JSONObject();
                    for (int i = 0; i < getDeviceTelemetry.size(); i++) {
                        nameObject.put(String.valueOf(getDeviceTelemetry.get(i).get("key")), values.get(i));
                    }
                    nameArray.put(nameObject);
                    msg.put((String) deviceInfo.get("name"), nameArray);
                    javaGateway.javaGatewaySend(String.valueOf(msg));
                }
            }
        }
    }

    public void kongzhi(int offset, List<Number> values){
        writeHoldingRegisters(modbusMaster,slaveId,offset,values);
    }


    private List<Map<String, Object>> getDeviceCredentials() {
        return jdbcTemplate.queryForList("SELECT * FROM gateway.device_info WHERE name LIKE '%AAA%'");
    }

    private List<Map<String, Object>> getDeviceTelemetry() {
        return jdbcTemplate.queryForList("SELECT * FROM gateway.device_telemetry WHERE device_id = 1");
    }

    //获取连接
    public static ModbusMaster getModbusMaster(String ip, Integer port)  {
        try {
            TcpParameters tcpParameters = new TcpParameters();
            //ip
            InetAddress adress = InetAddress.getByName(ip);
            tcpParameters.setHost(adress);
            // TCP设置长连接
            tcpParameters.setKeepAlive(true);
            // TCP设置端口，这里设置是默认端口502
            tcpParameters.setPort(port);

            ModbusMaster master = ModbusMasterFactory.createModbusMasterTCP(tcpParameters);
            Modbus.setAutoIncrementTransactionId(true);
            return master;
        }catch (Exception e){
            e.printStackTrace();
        }

        return null;

    }

    /**
     * 读04
     * @param master 配置的mater主机
     * @param slaveId 从机id
     * @param offset 寄存器读取开始地址  0059的话则为58
     * @param quantity 读取的寄存器数量
     * @return float类型的值
     */
    public static List<Float> readInputRegisters(ModbusMaster master, int slaveId, int offset, int quantity){
        List<Float> floatList=new ArrayList<>();
        try {

            if (!master.isConnected()) {
                master.connect();// 开启连接
            }

            // 读取对应从机的数据，readInputRegisters读取的写寄存器，功能码04
            int[] registerValues = master.readInputRegisters(slaveId, offset, quantity);

            floatList = ByteArrayConveter.getFloatArray(registerValues);
        }catch (Exception e) {
            e.printStackTrace();
        }
        return floatList;
    }

    /**
     * 读03
     * @param master 配置的mater主机
     * @param slaveId 从机id
     * @param offset 寄存器读取开始地址  0059的话则为58
     * @param quantity 读取的寄存器数量
     * @return float类型的值
     */
    public static List<Number> readHoldingRegisters(ModbusMaster master, int slaveId, int offset, int quantity) {
        List<Number> numberList = new ArrayList<>();
        try {
            if (!master.isConnected()) {
                master.connect(); // 开启连接
            }

            // 读取对应从机的数据，readHoldingRegisters读取的保持寄存器，功能码03
            int[] registerValues = master.readHoldingRegisters(slaveId, offset, quantity);

            for (int value : registerValues) {
                Number numValue;
                if (value == 0) {
                    numValue = 0;
                } else if (value == 1) {
                    numValue = 1;
                } else {
                    numValue = (float) value; // 其他情况下，将整数转换为浮点数
                }
                numberList.add(numValue);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return numberList;
    }
    public static void writeHoldingRegisters(ModbusMaster master, int slaveId, int offset, List<Number> values) {
        try {
            if (!master.isConnected()) {
                master.connect(); // 开启连接
            }

            int[] registerValues = new int[values.size()];
            for (int i = 0; i < values.size(); i++) {
                Number value = values.get(i);
                registerValues[i] = value.intValue();
            }

            // 写入对应从机的数据，writeHoldingRegisters写入的保持寄存器，功能码06
            master.writeMultipleRegisters(slaveId, offset, registerValues);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public static void close(ModbusMaster master){
        try {
            master.disconnect();

        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
