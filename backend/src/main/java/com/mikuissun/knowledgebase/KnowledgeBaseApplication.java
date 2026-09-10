package com.mikuissun.knowledgebase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan({"com.mikuissun.knowledgebase.user.mapper", "com.mikuissun.knowledgebase.knowledge.mapper",
        "com.mikuissun.knowledgebase.document.mapper"})
public class KnowledgeBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeBaseApplication.class, args);
    }
}
