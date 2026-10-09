package ordinal.third.pogo.data;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class PogoDataFileStore {

    private final PogoDataProperties properties;

    public Path prepare(String relativePath) throws IOException {
        Path relative = safeRelativePath(relativePath);
        Path target = properties.resolve(relative);
        Files.createDirectories(target.getParent());
        if (Files.exists(target)) {
            return target;
        }

        Path legacy = properties.resolve(relative.getFileName());
        if (!legacy.equals(target) && Files.exists(legacy)) {
            Files.copy(legacy, target);
        }
        return target;
    }

    private Path safeRelativePath(String value) {
        Path relative = Path.of(value).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("데이터 경로는 상대 경로여야 합니다: " + value);
        }
        return relative;
    }
}
