package com.mikuissun.knowledgebase.document.chunk;

import com.mikuissun.knowledgebase.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class TextChunkerTest {
    @Test
    void emptyTextCreatesNoChunksAndShortTextCreatesOne() {
        TextChunker chunker = chunker(1000, 150, 10);
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("  \n\t ")).isEmpty();
        List<TextChunk> chunks = chunker.chunk("企业知识库支持中文。");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).index()).isZero();
        assertThat(chunks.get(0).content()).isEqualTo("企业知识库支持中文。");
        assertThat(chunks.get(0).charCount()).isEqualTo(10);
        assertThat(chunks.get(0).tokenEstimate()).isPositive();
    }

    @Test
    void overlapAndOrderAreStableWithCharacterFallback() {
        TextChunker chunker = chunker(10, 3, 10);
        List<TextChunk> chunks = chunker.chunk("abcdefghijklmnopqrstuvwxyz");
        assertThat(chunks).extracting(TextChunk::index).containsExactly(0, 1, 2, 3);
        assertThat(chunks).extracting(TextChunk::content)
                .containsExactly("abcdefghij", "hijklmnopq", "opqrstuvwx", "vwxyz");
        for (int index = 1; index < chunks.size(); index++) {
            assertThat(chunks.get(index - 1).content())
                    .endsWith(chunks.get(index).content().substring(0, Math.min(3,
                            chunks.get(index).content().length())));
        }
    }

    @Test
    void prefersParagraphThenSentenceBoundariesForChineseText() {
        TextChunker paragraphChunker = chunker(20, 0, 10);
        assertThat(paragraphChunker.chunk("第一段中文内容足够长。\n\n第二段中文内容也足够长。"))
                .extracting(TextChunk::content)
                .containsExactly("第一段中文内容足够长。", "第二段中文内容也足够长。");

        TextChunker sentenceChunker = chunker(12, 0, 10);
        assertThat(sentenceChunker.chunk("这是第一句话。这是第二句话。最后一句。"))
                .extracting(TextChunk::content)
                .containsExactly("这是第一句话。", "这是第二句话。最后一句。");
    }

    @Test
    void normalizesNewlinesAndRejectsExcessiveChunkCount() {
        assertThat(chunker(100, 0, 10).chunk("第一段\r\n\r\n第二段").get(0).content())
                .isEqualTo("第一段\n\n第二段");
        assertThatThrownBy(() -> chunker(5, 1, 2).chunk("abcdefghijklmnopqrstuvwxyz"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过上限");
    }

    private TextChunker chunker(int size, int overlap, int max) {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(size);
        properties.setChunkOverlap(overlap);
        properties.setMaxChunksPerDocument(max);
        return new TextChunker(properties);
    }
}
