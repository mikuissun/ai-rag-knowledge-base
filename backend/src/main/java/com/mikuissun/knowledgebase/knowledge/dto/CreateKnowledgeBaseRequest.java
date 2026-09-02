package com.mikuissun.knowledgebase.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKnowledgeBaseRequest(
        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 100, message = "知识库名称不能超过 100 个字符")
        String name,
        @Size(max = 500, message = "知识库描述不能超过 500 个字符")
        String description
) {
}
