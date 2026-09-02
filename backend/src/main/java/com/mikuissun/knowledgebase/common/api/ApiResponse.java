package com.mikuissun.knowledgebase.common.api;

public record ApiResponse(int code, String message) {

    public static ApiResponse ok() {
        return new ApiResponse(200, "ok");
    }
}
