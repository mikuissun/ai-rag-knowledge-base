package com.mikuissun.knowledgebase.document;

import com.mikuissun.knowledgebase.common.exception.BusinessException;
import com.mikuissun.knowledgebase.document.entity.Document;
import com.mikuissun.knowledgebase.document.mapper.DocumentMapper;
import com.mikuissun.knowledgebase.document.parser.*;
import com.mikuissun.knowledgebase.document.service.*;
import com.mikuissun.knowledgebase.knowledge.service.KnowledgeBaseService;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentServiceTest {
    @TempDir Path root;
    DocumentMapper mapper;
    KnowledgeBaseService knowledgeBases;
    PlatformTransactionManager manager;
    DocumentService service;
    Document saved;

    @BeforeEach
    void setup() {
        mapper = mock(DocumentMapper.class);
        knowledgeBases = mock(KnowledgeBaseService.class);
        manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        service = new DocumentServiceImpl(mapper, knowledgeBases, new DocumentParserFactory(List.of(
                new PdfDocumentParser(), new WordDocumentParser(), new MarkdownDocumentParser(), new TextDocumentParser())),
                new LocalDocumentStorage(root.toString()), manager);
        when(mapper.insert(any(Document.class))).thenAnswer(call -> {
            saved = call.getArgument(0);
            saved.setId(7L);
            return 1;
        });
        when(mapper.selectOne(any())).thenAnswer(call -> saved);
        when(mapper.delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
    }

    static byte[] fixture(String extension) throws Exception {
        var out = new ByteArrayOutputStream();
        if (extension.equals("pdf")) {
            try (var pdf = new PDDocument()) {
                var page = new PDPage();
                pdf.addPage(page);
                try (var content = new PDPageContentStream(pdf, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(30, 700);
                    content.showText("Stage four document");
                    content.endText();
                }
                pdf.save(out);
            }
        } else if (extension.equals("docx")) {
            try (var word = new XWPFDocument()) {
                word.createParagraph().createRun().setText("Stage four document");
                word.createTable(1, 1).getRow(0).getCell(0).setText("Table text");
                word.write(out);
            }
        } else {
            out.write("Stage four document 中文".getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "docx", "md", "txt"})
    void parsesAndDeletesRealFormats(String extension) throws Exception {
        var response = service.upload(10L, 1L, new MockMultipartFile("file",
                "../../sample." + extension, "application/octet-stream", fixture(extension)));
        assertThat(response.contentText()).contains("Stage four document");
        assertThat(response.originalName()).isEqualTo("sample." + extension);
        Path file = root.resolve(saved.getFilePath());
        assertThat(file).exists();
        assertThat(saved.getFilePath()).startsWith("1/10/");
        service.delete(10L, 7L, 1L);
        assertThat(file).doesNotExist();
        verify(mapper).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        assertNoFiles();
    }

    @Test
    void rejectsBadInputsWithoutFiles() throws Exception {
        for (var file : List.of(
                new MockMultipartFile("file", "a.exe", "application/octet-stream", new byte[]{1}),
                new MockMultipartFile("file", "txt", "text/plain", new byte[]{65}),
                new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]),
                new MockMultipartFile("file", "a.txt", "text/plain", new byte[20 * 1024 * 1024 + 1]),
                new MockMultipartFile("file", "a.pdf", "text/plain", new byte[]{1}),
                new MockMultipartFile("file", "../", "text/plain", new byte[]{1}))) {
            assertThatThrownBy(() -> service.upload(10L, 1L, file)).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(mapper);
        assertNoFiles();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "docx", "txt", "md"})
    void cleansUpParsingFailure(String extension) throws Exception {
        assertThatThrownBy(() -> service.upload(10L, 1L,
                new MockMultipartFile("file", "broken." + extension, "application/octet-stream",
                        new byte[]{(byte) 0xff, 0, 1}))).isInstanceOf(BusinessException.class);
        assertNoFiles();
        verifyNoInteractions(mapper);
    }

    @Test
    void cleansUpInsertAndCommitFailures() throws Exception {
        when(mapper.insert(any(Document.class))).thenThrow(new IllegalStateException("insert failed"));
        assertThatThrownBy(() -> uploadText()).isInstanceOf(IllegalStateException.class);
        assertNoFiles();
        when(mapper.insert(any(Document.class))).thenReturn(1);
        saved = new Document();
        doThrow(new IllegalStateException("commit failed")).when(manager).commit(any());
        assertThatThrownBy(() -> uploadText()).isInstanceOf(IllegalStateException.class);
        assertNoFiles();
    }

    @Test
    void restoresFileWhenDatabaseDeleteFails() throws Exception {
        uploadText();
        Path original = root.resolve(saved.getFilePath());
        when(mapper.delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenThrow(new IllegalStateException("delete failed"));
        assertThatThrownBy(() -> service.delete(10L, 7L, 1L)).isInstanceOf(IllegalStateException.class);
        assertThat(original).exists();
        assertThat(Files.readString(original)).isEqualTo("hello");
    }

    @Test
    void missingFileDoesNotBlockDeletion() throws Exception {
        uploadText();
        Files.delete(root.resolve(saved.getFilePath()));
        service.delete(10L, 7L, 1L);
        verify(mapper).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    void restoresFileWhenDeleteCommitFails() throws Exception {
        uploadText();
        Path original = root.resolve(saved.getFilePath());
        doThrow(new IllegalStateException("commit failed")).when(manager).commit(any());
        assertThatThrownBy(() -> service.delete(10L, 7L, 1L)).isInstanceOf(IllegalStateException.class);
        assertThat(original).exists();
        try (var files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile).toList()).containsExactly(original);
        }
    }

    @Test
    void ownershipIsCheckedBeforeAllOperations() throws Exception {
        when(knowledgeBases.getByIdAndUserId(10L, 1L)).thenThrow(new BusinessException(404, "知识库不存在"));
        assertThatThrownBy(() -> uploadText()).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list(10L, 1L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.get(10L, 7L, 1L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.delete(10L, 7L, 1L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapper);
        assertNoFiles();
    }

    @Test
    void rejectsEscapingStoragePaths() {
        var storage = new LocalDocumentStorage(root.toString());
        assertThatThrownBy(() -> storage.resolve("../outside.txt")).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> storage.resolve(root.resolve("absolute.txt").toString()))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void limitsExtractedText() throws Exception {
        assertThatThrownBy(() -> service.upload(10L, 1L, new MockMultipartFile("file", "huge.txt",
                "text/plain", "a".repeat(5_000_001).getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class);
        assertNoFiles();
    }

    private void uploadText() {
        service.upload(10L, 1L, new MockMultipartFile("file", "test.txt", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)));
    }
    private void assertNoFiles() throws Exception {
        try (var files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }
}
