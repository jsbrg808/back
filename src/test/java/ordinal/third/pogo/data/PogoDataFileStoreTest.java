package ordinal.third.pogo.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PogoDataFileStoreTest {

    @TempDir
    Path tempDir;

    private PogoDataFileStore store;

    @BeforeEach
    void setUp() {
        PogoDataProperties properties = new PogoDataProperties();
        properties.setDirectory(tempDir);
        store = new PogoDataFileStore(properties);
    }

    @Test
    void prepare_상위_디렉터리를_만들고_대상_경로를_반환한다() throws IOException {
        Path target = store.prepare("calcydex/normal-forms-00.tsv");

        assertThat(target).isEqualTo(tempDir.resolve("calcydex").resolve("normal-forms-00.tsv"));
        assertThat(target.getParent()).exists();
        // 원본/레거시 파일이 없으면 파일을 생성하지 않고 경로만 돌려준다
        assertThat(target).doesNotExist();
    }

    @Test
    void prepare_이미_파일이_있으면_쓰지_않고_그_경로를_반환한다() throws IOException {
        Path target = tempDir.resolve("calcydex").resolve("normal-forms-00.tsv");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "기존 내용");

        Path result = store.prepare("calcydex/normal-forms-00.tsv");

        assertThat(result).isEqualTo(target);
        assertThat(result).hasContent("기존 내용");
    }

    @Test
    void prepare_네임스페이스_파일이_없으면_레거시_평면_파일을_복사한다() throws IOException {
        Path legacy = tempDir.resolve("normal-forms-00.tsv");
        Files.writeString(legacy, "레거시 데이터");

        Path target = store.prepare("calcydex/normal-forms-00.tsv");

        assertThat(target).hasContent("레거시 데이터");
    }

    @Test
    void prepare_상대경로_상위_이동은_금지된다() {
        assertThatThrownBy(() -> store.prepare("../outside.tsv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상대 경로여야 합니다");
    }

    @Test
    void prepare_절대경로는_금지된다() {
        String absolute = Path.of(System.getProperty("user.home"))
                .toAbsolutePath().resolve("unrelated.tsv").toString();

        assertThatThrownBy(() -> store.prepare(absolute))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상대 경로여야 합니다");
    }

    @Test
    void prepare_내부_정규화된_경로는_허용된다() throws IOException {
        // "calcydex/../pokedex" 는 normalize되면 "pokedex"로 허용된다
        Path target = store.prepare("calcydex/../pokedex/pokedex.json");

        assertThat(target).isEqualTo(tempDir.resolve("pokedex").resolve("pokedex.json"));
        assertThat(target.getParent()).exists();
    }
}
