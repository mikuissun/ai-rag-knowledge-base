package com.mikuissun.knowledgebase.llm;

import java.util.function.Consumer;

public interface ChatModelService {
    String chat(String systemPrompt, String userPrompt);
    void streamChat(String systemPrompt, String userPrompt, Consumer<String> deltaConsumer);
    String modelName();
}
