package com.mikuissun.knowledgebase.controller;

import com.mikuissun.knowledgebase.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Void> health() {
        return ApiResponse.ok();
    }
}
