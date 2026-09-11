package com.mikuissun.knowledgebase.rag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RagChatRequest(
        @NotBlank(message = "question 不能为空")
        @Size(max = 4000, message = "question 不能超过 4000 个字符")
        String question,
        @Min(value = 1, message = "topK 不能小于 1")
        @Max(value = 20, message = "topK 不能大于 20")
        Integer topK) {
}
