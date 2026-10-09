package ordinal.third.pogo.pokedex.service;

import lombok.RequiredArgsConstructor;
import ordinal.third.pogo.data.PogoDataFileStore;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PokedexSourceClient {

    private static final String POKEDEX_URL =
            "https://pokemon-go-api.github.io/pokemon-go-api/api/pokedex.json";
    private static final String SPECIES_URL =
            "https://raw.githubusercontent.com/PokeAPI/pokeapi/master/data/v2/csv/pokemon_species.csv";

    private final PogoDataFileStore dataFileStore;
    private final PokedexHttpConnectionFactory connectionFactory;

    public SourceFile refreshPokedex(List<String> details) throws Exception {
        return refresh(
                "pokedex/pokedex.json", POKEDEX_URL, "pokedex.json", details);
    }

    public SourceFile refreshSpecies(List<String> details) throws Exception {
        return refresh(
                "pokedex/pokemon_species.csv", SPECIES_URL,
                "pokemon_species.csv", details);
    }

    private SourceFile refresh(
            String relativePath,
            String sourceUrl,
            String displayName,
            List<String> details) throws Exception {
        Path localFile = dataFileStore.prepare(relativePath);
        Path metadataFile = dataFileStore.prepare(relativePath + ".http.properties");
        boolean localAvailable = Files.isRegularFile(localFile) && Files.size(localFile) > 0;
        HttpMetadata metadata = localAvailable
                ? HttpMetadata.read(metadataFile)
                : HttpMetadata.empty();

        details.add("로컬 " + displayName + " 확인: "
                + (localAvailable ? Files.size(localFile) + " bytes" : "파일 없음"));
        details.add("원격 최신 상태 확인 중 (" + sourceUrl + ")...");

        if (localAvailable && metadata.isEmpty()
                && registerExistingIfCurrent(
                        localFile, metadataFile, sourceUrl, displayName, details)) {
            return new SourceFile(localFile.toFile(), false);
        }

        HttpURLConnection connection = null;
        try {
            connection = connectionFactory.open(URI.create(sourceUrl).toURL());
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            if (!metadata.etag().isBlank()) {
                connection.setRequestProperty("If-None-Match", metadata.etag());
            }
            if (!metadata.lastModified().isBlank()) {
                connection.setRequestProperty("If-Modified-Since", metadata.lastModified());
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED && localAvailable) {
                details.add("로컬 " + displayName + "이 최신입니다. 다운로드를 건너뜁니다.");
                return new SourceFile(localFile.toFile(), false);
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return fallbackOrThrow(localFile, localAvailable, displayName,
                        "HTTP " + responseCode, details);
            }

            Path temporary = localFile.resolveSibling(localFile.getFileName() + ".tmp");
            try {
                try (InputStream input = connection.getInputStream()) {
                    Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
                if (!Files.isRegularFile(temporary) || Files.size(temporary) == 0) {
                    return fallbackOrThrow(localFile, localAvailable, displayName,
                            "다운로드된 파일이 비어 있음", details);
                }
                Files.move(temporary, localFile, StandardCopyOption.REPLACE_EXISTING);
                new HttpMetadata(
                        header(connection, "ETag"),
                        header(connection, "Last-Modified"))
                        .write(metadataFile);
                details.add("최신 " + displayName + " 다운로드 완료");
                return new SourceFile(localFile.toFile(), true);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            if (localAvailable) {
                details.add("원격 확인 실패. 기존 로컬 " + displayName + "을 사용합니다: "
                        + exception.getMessage());
                return new SourceFile(localFile.toFile(), false);
            }
            throw new IOException("로컬 " + displayName
                    + "이 없고 원격 다운로드에도 실패했습니다.", exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean registerExistingIfCurrent(
            Path localFile,
            Path metadataFile,
            String sourceUrl,
            String displayName,
            List<String> details) {
        HttpURLConnection connection = null;
        try {
            connection = connectionFactory.open(URI.create(sourceUrl).toURL());
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }
            long remoteSize = connection.getContentLengthLong();
            if (remoteSize <= 0 || remoteSize != Files.size(localFile)) {
                return false;
            }
            new HttpMetadata(
                    header(connection, "ETag"),
                    header(connection, "Last-Modified"))
                    .write(metadataFile);
            details.add("기존 로컬 " + displayName
                    + "의 크기가 원격 파일과 일치합니다. 다운로드 없이 최신 정보를 등록합니다.");
            return true;
        } catch (IOException exception) {
            details.add("기존 로컬 파일의 최초 최신 확인을 완료하지 못했습니다: "
                    + exception.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private SourceFile fallbackOrThrow(
            Path localFile,
            boolean localAvailable,
            String displayName,
            String reason,
            List<String> details) throws IOException {
        if (localAvailable) {
            details.add("원격 확인 실패 (" + reason + "). 기존 로컬 "
                    + displayName + "을 사용합니다.");
            return new SourceFile(localFile.toFile(), false);
        }
        throw new IOException("로컬 " + displayName
                + "이 없고 원격 다운로드에도 실패했습니다: " + reason);
    }

    private String header(HttpURLConnection connection, String name) {
        String value = connection.getHeaderField(name);
        return value == null ? "" : value;
    }

    public record SourceFile(File file, boolean updated) {
    }

    private record HttpMetadata(String etag, String lastModified) {

        private static HttpMetadata empty() {
            return new HttpMetadata("", "");
        }

        private boolean isEmpty() {
            return etag.isBlank() && lastModified.isBlank();
        }

        private static HttpMetadata read(Path file) throws IOException {
            String etag = "";
            String lastModified = "";
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int separator = line.indexOf('=');
                    if (separator <= 0) {
                        continue;
                    }
                    String key = line.substring(0, separator);
                    String value = line.substring(separator + 1);
                    if ("etag".equals(key)) {
                        etag = value;
                    } else if ("last-modified".equals(key)) {
                        lastModified = value;
                    }
                }
            }
            return new HttpMetadata(etag, lastModified);
        }

        private void write(Path file) throws IOException {
            String content = "etag=" + etag + System.lineSeparator()
                    + "last-modified=" + lastModified + System.lineSeparator();
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try {
                Files.writeString(temporary, content, StandardCharsets.UTF_8);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
