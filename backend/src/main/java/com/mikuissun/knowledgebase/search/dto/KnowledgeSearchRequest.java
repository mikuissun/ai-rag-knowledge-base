package com.mikuissun.knowledgebase.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeSearchRequest(
        @NotBlank(message = "query 不能为空")
        @Size(max = 2000, message = "query 不能超过 2000 个字符")
        String query,
        @Min(value = 1, message = "topK 不能小于 1")
        @Max(value = 20, message = "topK 不能大于 20")
        Integer topK) {
}
