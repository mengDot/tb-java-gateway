package com.meng.tbjavagateway.controller;
import com.meng.tbjavagateway.config.RedisUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/restart")
public class RestartController {

    private RedisUtils redisUtils;
    @PostMapping
    public ResponseEntity<String> restartApp() {
        redisUtils.set("exceptionNumber", "0");
        try {
            Runtime.getRuntime().exec("sudo systemctl restart javaGateway.service");
            System.exit(0); // 退出当前进程
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Failed to restart the service.");
        }
        return ResponseEntity.ok("Restart initiated.");
    }
}