package com.mikuissun.knowledgebase.document.service;

import com.mikuissun.knowledgebase.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Component
public class LocalDocumentStorage {
    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private final Path base;

    public LocalDocumentStorage(@Value("${app.storage.base-path}") String basePath) {
        base = Path.of(basePath).toAbsolutePath().normalize();
    }

    public String newRelativePath(Long userId, Long knowledgeBaseId, String extension) {
        if (userId == null || knowledgeBaseId == null || userId <= 0 || knowledgeBaseId <= 0
                || !extension.matches("pdf|docx|md|txt")) {
            throw new BusinessException(400, "无效的存储参数");
        }
        return userId + "/" + knowledgeBaseId + "/" + UUID.randomUUID() + "." + extension;
    }

    public Path resolve(String relative) throws IOException {
        Path path = base.resolve(relative).normalize();
        if (Path.of(relative).isAbsolute() || !path.startsWith(base) || path.equals(base)) {
            throw new IOException("Unsafe storage path");
        }
        // Refuse symbolic links/junctions including ancestors of the configured root.
        Path cursor = path.getRoot();
        for (Path part : path) {
            cursor = cursor.resolve(part);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(cursor) || !cursor.toRealPath().equals(cursor.toAbsolutePath()))) {
                throw new IOException("Storage path contains a link");
            }
        }
        return path;
    }

    public Path save(String relative, MultipartFile file) throws IOException {
        Path path = resolve(relative);
        Files.createDirectories(path.getParent());
        resolve(relative);
        try (var input = file.getInputStream();
             var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_FILE_SIZE) throw new BusinessException(413, "文件不能超过 20MB");
                output.write(buffer, 0, count);
            }
            if (total == 0) throw new BusinessException(400, "文件不能为空");
        }
        return path;
    }
}
