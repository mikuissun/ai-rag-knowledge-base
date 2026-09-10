package com.mikuissun.knowledgebase.document.chunk;

import com.mikuissun.knowledgebase.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {
    private static final int MIN_BOUNDARY_RATIO_PERCENT = 55;
    private final RagProperties properties;

    public TextChunker(RagProperties properties) {
        this.properties = properties;
    }

    public List<TextChunk> chunk(String source) {
        if (source == null || source.isBlank()) return List.of();
        String text = source.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (text.isEmpty()) return List.of();

        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int hardEnd = Math.min(start + properties.getChunkSize(), text.length());
            int end = hardEnd == text.length() ? hardEnd : naturalBoundary(text, start, hardEnd);
            String content = text.substring(start, end).strip();
            if (!content.isEmpty()) {
                if (chunks.size() >= properties.getMaxChunksPerDocument()) {
                    throw new BusinessException(400, "文档切分数量超过上限 "
                            + properties.getMaxChunksPerDocument());
                }
                chunks.add(new TextChunk(chunks.size(), content, content.length(), estimateTokens(content)));
            }
            if (end >= text.length()) break;
            int next = Math.max(start + 1, end - properties.getChunkOverlap());
            while (next < end && Character.isWhitespace(text.charAt(next))) next++;
            start = next;
        }
        return List.copyOf(chunks);
    }

    private int naturalBoundary(String text, int start, int hardEnd) {
        int minimum = start + properties.getChunkSize() * MIN_BOUNDARY_RATIO_PERCENT / 100;
        int paragraph = text.lastIndexOf("\n\n", hardEnd - 1);
        if (paragraph >= minimum) return paragraph + 2;
        int newline = text.lastIndexOf('\n', hardEnd - 1);
        if (newline >= minimum) return newline + 1;
        for (int index = hardEnd - 1; index >= minimum; index--) {
            if (isSentenceEnd(text.charAt(index))) return index + 1;
        }
        return hardEnd;
    }

    private boolean isSentenceEnd(char character) {
        return character == '。' || character == '！' || character == '？'
                || character == '；' || character == '.' || character == '!'
                || character == '?' || character == ';';
    }

    private int estimateTokens(String text) {
        int cjk = 0;
        int other = 0;
        for (int offset = 0; offset < text.length();) {
            int point = text.codePointAt(offset);
            offset += Character.charCount(point);
            if (Character.isWhitespace(point)) continue;
            Character.UnicodeScript script = Character.UnicodeScript.of(point);
            if (script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                cjk++;
            } else {
                other++;
            }
        }
        return Math.max(1, cjk + (other + 3) / 4);
    }
}
