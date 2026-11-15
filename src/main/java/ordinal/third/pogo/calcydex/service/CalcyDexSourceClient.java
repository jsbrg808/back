package ordinal.third.pogo.calcydex.service;

import lombok.RequiredArgsConstructor;
import ordinal.third.pogo.data.PogoDataFileStore;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
@RequiredArgsConstructor
public class CalcyDexSourceClient {

    static final String SOURCE_URL =
            "https://d.apkpure.net/b/XAPK/tesmath.calcy?version=latest";
    static final String SOURCE_METADATA_PATH = "calcydex/source-calcy.properties";
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("_(\\d+(?:\\.\\d+)+[A-Za-z]*)_APKPure\\.xapk", Pattern.CASE_INSENSITIVE);
    private static final String BASE_APK_ENTRY = "tesmath.calcy.apk";
    private static final String GAMESTATS_ENTRY = "assets/gamestats/gamestats.db";

    private final PogoDataFileStore dataFileStore;
    private final CalcyDexHttpConnectionFactory connectionFactory;
    private final CalcyDexTsvUpdater tsvUpdater;

    public boolean refresh(List<String> details) throws IOException {
        Path metadataFile = dataFileStore.prepare(SOURCE_METADATA_PATH);
        SourceMetadata current = SourceMetadata.read(metadataFile);
        Optional<String> localVersion = tsvUpdater.localDataVersion();
        Release release = resolveLatest(details);
        if (release == null) {
            if (localVersion.isEmpty()) {
                throw new IOException("로컬 CalcyDex TSV가 없고 Calcy IV 최신 버전도 확인하지 못했습니다.");
            }
            return false;
        }
        boolean completeDataSet = localVersion.isPresent();
        if (release.releaseId().equals(current.releaseId()) && completeDataSet) {
            details.add("Calcy IV " + release.version()
                    + " 데이터가 최신입니다. XAPK 다운로드를 건너뜁니다.");
            return false;
        }
        if (current.releaseId().isBlank()
                && localVersion.filter(release.version()::equals).isPresent()) {
            new SourceMetadata(release.releaseId(), release.version(), "")
                    .write(metadataFile);
            details.add("기존 로컬 CalcyDex TSV가 Calcy IV " + release.version()
                    + "과 일치합니다. 다운로드 없이 릴리스 정보를 등록합니다.");
            return false;
        }

        details.add(completeDataSet
                ? "Calcy IV " + release.version() + " 새 릴리스 확인. XAPK 다운로드 중..."
                : "로컬 CalcyDex TSV가 없어 Calcy IV " + release.version()
                        + " XAPK를 다운로드합니다...");
        Files.createDirectories(metadataFile.getParent());
        Path xapk = Files.createTempFile(metadataFile.getParent(), "calcy-", ".xapk");
        Path apk = Files.createTempFile(metadataFile.getParent(), "calcy-base-", ".apk");
        Path database = Files.createTempFile(metadataFile.getParent(), "gamestats-", ".db");
        try {
            download(release.downloadUrl(), xapk);
            String sha256 = sha256(xapk);
            extractEntry(xapk, BASE_APK_ENTRY, apk);
            extractEntry(apk, GAMESTATS_ENTRY, database);
            int pokemonCount = tsvUpdater.update(database, release.version(), sha256);
            new SourceMetadata(release.releaseId(), release.version(), sha256)
                    .write(metadataFile);
            details.add("CalcyDex TSV 갱신 완료: " + pokemonCount + "건");
            return true;
        } finally {
            Files.deleteIfExists(database);
            Files.deleteIfExists(apk);
            Files.deleteIfExists(xapk);
        }
    }

    private Release resolveLatest(List<String> details) throws IOException {
        HttpURLConnection connection = open(SOURCE_URL, 5000, 5000, false);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < 300 || responseCode >= 400) {
                details.add("Calcy IV 최신 버전 확인 실패 (HTTP " + responseCode
                        + "). 기존 TSV를 사용합니다.");
                return null;
            }
            String location = connection.getHeaderField("Location");
            if (location == null || location.isBlank()) {
                throw new IOException("Calcy IV 최신 XAPK 응답에 다운로드 주소가 없습니다.");
            }
            URI downloadUri = URI.create(location);
            return new Release(
                    downloadUri.toString(), releaseId(downloadUri), version(downloadUri));
        } finally {
            connection.disconnect();
        }
    }

    private void download(String url, Path target) throws IOException {
        HttpURLConnection connection = open(url, 10000, 60000, true);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Calcy IV XAPK 다운로드 실패 (HTTP " + responseCode + ")");
            }
            try (InputStream input = connection.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(
            String value,
            int connectTimeout,
            int readTimeout,
            boolean followRedirects) throws IOException {
        URL url = URI.create(value).toURL();
        HttpURLConnection connection = connectionFactory.open(url);
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(followRedirects);
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setRequestProperty("User-Agent", "third-calcydex-updater/1.0");
        return connection;
    }

    private String releaseId(URI uri) throws IOException {
        String path = uri.getPath();
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash == path.length() - 1) {
            throw new IOException("Calcy IV XAPK 릴리스 식별자를 확인할 수 없습니다.");
        }
        try {
            return new String(Base64.getUrlDecoder().decode(path.substring(slash + 1)),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Calcy IV XAPK 릴리스 식별자가 올바르지 않습니다.", exception);
        }
    }

    static String version(URI uri) throws IOException {
        String query = uri.getRawQuery();
        if (query != null) {
            for (String parameter : query.split("&")) {
                int separator = parameter.indexOf('=');
                if (separator <= 0 || !"filename".equals(parameter.substring(0, separator))) {
                    continue;
                }
                String filename = URLDecoder.decode(
                        parameter.substring(separator + 1), StandardCharsets.UTF_8);
                Matcher matcher = VERSION_PATTERN.matcher(filename);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        throw new IOException("Calcy IV XAPK 버전을 확인할 수 없습니다.");
    }

    private void extractEntry(Path archive, String entryName, Path target) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException(
                        "Calcy IV 패키지에서 " + entryName + " 파일을 찾지 못했습니다.");
            }
            try (InputStream input = zip.getInputStream(entry)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, count);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest()).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private record Release(String downloadUrl, String releaseId, String version) {
    }

    private record SourceMetadata(String releaseId, String version, String sha256) {

        private static SourceMetadata read(Path file) throws IOException {
            String releaseId = "";
            String version = "";
            String sha256 = "";
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int separator = line.indexOf('=');
                    if (separator <= 0) {
                        continue;
                    }
                    String key = line.substring(0, separator).trim();
                    String value = line.substring(separator + 1).trim();
                    switch (key) {
                        case "release-id" -> releaseId = value;
                        case "version" -> version = value;
                        case "sha256" -> sha256 = value;
                        default -> {
                        }
                    }
                }
            }
            return new SourceMetadata(releaseId, version, sha256);
        }

        private void write(Path file) throws IOException {
            String content = "release-id=" + releaseId + System.lineSeparator()
                    + "version=" + version + System.lineSeparator()
                    + "sha256=" + sha256 + System.lineSeparator();
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
