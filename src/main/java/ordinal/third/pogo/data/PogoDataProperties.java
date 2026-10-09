package ordinal.third.pogo.data;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pogo.data")
public class PogoDataProperties {

    private Path directory = Path.of(
        System.getProperty("user.home"), "Documents", "정진우", "data");

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        this.directory = directory;
    }

    public Path resolve(Path relativePath) {
        return directory.toAbsolutePath().normalize().resolve(relativePath).normalize();
    }
}
