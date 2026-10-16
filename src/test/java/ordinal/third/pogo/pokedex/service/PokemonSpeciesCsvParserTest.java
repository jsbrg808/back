package ordinal.third.pogo.pokedex.service;

import ordinal.third.pogo.vo.PokemonSpeciesCsvVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PokemonSpeciesCsvParserTest {

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    @Test
    void 헤더를_건너뛰고_각_행을_파싱한다() throws IOException {
        String csv = """
                id,identifier,generation,evolves_from_species_id,evolution_chain_id,col5,col6,col7,gender_rate,col9
                1,a,b,,10,c,d,e,8,f
                2,a,b,1,10,c,d,e,4,f
                """;
        Path path = write("species.csv", csv);

        var species = new PokemonSpeciesCsvParser().parse(path);

        assertThat(species).hasSize(2);
        PokemonSpeciesCsvVO first = species.get(0);
        assertThat(first.getId()).isEqualTo(1);
        // evolves_from_species_id 가 공란이면 null
        assertThat(first.getEvolvesFromSpeciesId()).isNull();
        assertThat(first.getEvolutionChainId()).isEqualTo(10);
        assertThat(first.getGenderRate()).isEqualTo(8);

        PokemonSpeciesCsvVO second = species.get(1);
        assertThat(second.getEvolvesFromSpeciesId()).isEqualTo(1);
        assertThat(second.getGenderRate()).isEqualTo(4);
    }

    @Test
    void 빈_줄은_건너뛴다() throws IOException {
        String csv = """
                id,a,b,c,d,e,f,g,h
                1,,,10,,c,d,e,0
                """;
        Path path = write("species.csv", csv + "\n\n");

        assertThat(new PokemonSpeciesCsvParser().parse(path)).hasSize(1);
    }

    @Test
    void 열_개수가_부족하면_예외를_던진다() throws IOException {
        Path path = write("species.csv", "id\nx,y,z\n");

        assertThatThrownBy(() -> new PokemonSpeciesCsvParser().parse(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("열 개수가 부족")
                .hasMessageContaining("2");
    }

    @Test
    void 숫자_필드가_정수가_아니면_예외를_던진다() throws IOException {
        Path path = write("species.csv", "id,a,b,c,d,e,f,g,h\nzz,a,b,,10,c,d,e,4\n");

        assertThatThrownBy(() -> new PokemonSpeciesCsvParser().parse(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CSV 데이터 형식 오류");
    }
}
