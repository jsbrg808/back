package ordinal.third.pogo.pokedex.service;

import ordinal.third.pogo.vo.PokemonSpeciesCsvVO;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PokemonSpeciesCsvParser {

    public List<PokemonSpeciesCsvVO> parse(Path path) throws IOException {
        String csvContent = Files.readString(path, StandardCharsets.UTF_8);
        String[] lines = csvContent.split("\\r?\\n");
        List<PokemonSpeciesCsvVO> species = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split(",", -1);
            if (parts.length < 9) {
                throw new IllegalArgumentException(
                        "CSV 데이터 행의 열 개수가 부족합니다. (라인 " + (i + 1)
                                + ": 컬럼 개수 " + parts.length + ")");
            }
            species.add(parseRow(parts, i + 1));
        }
        return List.copyOf(species);
    }

    private PokemonSpeciesCsvVO parseRow(String[] parts, int lineNumber) {
        try {
            PokemonSpeciesCsvVO vo = new PokemonSpeciesCsvVO();
            vo.setId(Integer.parseInt(parts[0].trim()));
            vo.setEvolvesFromSpeciesId(nullableInteger(parts[3]));
            vo.setEvolutionChainId(nullableInteger(parts[4]));
            vo.setGenderRate(nullableInteger(parts[8]));
            return vo;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "CSV 데이터 형식 오류 (라인 " + lineNumber + "): " + e.getMessage(), e);
        }
    }

    private Integer nullableInteger(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : Integer.parseInt(trimmed);
    }
}
