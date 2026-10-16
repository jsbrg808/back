package ordinal.third.pogo.pokedex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ordinal.third.pogo.vo.PokedexJsonVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PokedexJsonParserTest {

    @TempDir
    Path tempDir;

    private PokedexJsonParser parser;

    @BeforeEach
    void setUp() {
        parser = new PokedexJsonParser(new ObjectMapper());
    }

    private Path write(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    @Test
    void 기본폼과_지역폼을_모두_파싱한다() throws IOException {
        String json = """
                [
                  {
                    "id": "BULBASAUR",
                    "formId": "BULBASAUR_NORMAL",
                    "dexNr": 1,
                    "names": { "Korean": "이상해씨" },
                    "stats": { "attack": 118, "defense": 111, "stamina": 128 },
                    "assets": { "image": "pm1024" },
                    "assetForms": [],
                    "regionForms": {
                      "ANTIQUA": {
                        "id": "BULBASAUR_ANTIQUA",
                        "formId": "BULBASAUR_ANTIQUA",
                        "dexNr": 1,
                        "names": { "Korean": "고대 이상해씨" },
                        "stats": { "attack": 137, "defense": 130, "stamina": 145 }
                      }
                    }
                  }
                ]
                """;
        Path path = write("pokedex.json", json);

        var result = parser.parse(path);

        assertThat(result.pokemon()).hasSize(2);
        assertThat(result.baseCount()).isEqualTo(1);
        assertThat(result.formCount()).isEqualTo(1);
        List<PokedexJsonVO> pokemon = result.pokemon();
        assertThat(pokemon.get(0).getKorean()).isEqualTo("이상해씨");
        assertThat(pokemon.get(0).getDexFormNr()).isEqualTo(1);
        assertThat(pokemon.get(0).getImage()).isEqualTo("pm1024");
        assertThat(pokemon.get(1).getKorean()).isEqualTo("고대 이상해씨");
        assertThat(pokemon.get(1).getDexFormNr()).isEqualTo(0);
        assertThat(pokemon.get(0).getPokemonClass()).isNull();
    }

    @Test
    void assetForms의_암컷_이미지를_imageG2로_맵핑한다() throws IOException {
        String json = """
                [
                  {
                    "id": "PUGPOO",
                    "formId": "PUGPOO_NORMAL",
                    "dexNr": 209,
                    "names": { "Korean": "구리스" },
                    "stats": { "attack": 80, "defense": 70, "stamina": 90 },
                    "assets": { "image": "pm210" },
                    "assetForms": [
                      { "form": "FEMALE", "image": "pm210.fFEMALE.g2" },
                      { "isFemale": false, "image": "pm210.g2" }
                    ]
                  }
                ]
                """;
        Path path = write("pokedex.json", json);

        var result = parser.parse(path);

        assertThat(result.pokemon().get(0).getImageG2()).isEqualTo("pm210.fFEMALE.g2");
        assertThat(result.femaleImageCount()).isEqualTo(1);
    }

    @Test
    void 최상위가_배열이_아니면_예외를_던진다() throws IOException {
        Path path = write("pokedex.json", "{}");

        assertThatThrownBy(() -> parser.parse(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("배열이 아님");
    }

    @Test
    void 필수_필드가_누락되면_예외를_던진다() throws IOException {
        assertThatThrownBy(() -> parser.parse(
                write("no-id.json", "[{ \"formId\": \"A\", \"dexNr\": 1, \"names\": {\"Korean\": \"a\"}, \"stats\": {\"attack\":1,\"defense\":1,\"stamina\":1}}]")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> parser.parse(
                write("no-korean.json", "[{ \"id\": \"A\", \"formId\": \"A\", \"dexNr\": 1, \"names\": {\"English\": \"a\"}, \"stats\": {\"attack\":1,\"defense\":1,\"stamina\":1}}]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("한국어");

        assertThatThrownBy(() -> parser.parse(
                write("no-stats.json", "[{ \"id\": \"A\", \"formId\": \"A\", \"dexNr\": 1, \"names\": {\"Korean\": \"a\"}}]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stats");
    }

    @Test
    void dexNr가_음수면_예외를_던진다() throws IOException {
        Path path = write("pokedex.json", """
                [
                  {
                    "id": "A",
                    "formId": "A",
                    "dexNr": -1,
                    "names": { "Korean": "가" },
                    "stats": { "attack": 1, "defense": 1, "stamina": 1 }
                  }
                ]
                """);

        assertThatThrownBy(() -> parser.parse(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dexNr");
    }
}
