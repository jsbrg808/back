package ordinal.third.pogo.calcydex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import ordinal.third.pogo.data.PogoDataFileStore;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CalcyDexPokemonClient {

    private static final int RESOURCE_PART_COUNT = 11;
    private static final String DATA_FILE_PATTERN = "calcydex/normal-forms-%02d.tsv";

    private final ObjectMapper objectMapper;
    private final PogoDataFileStore dataFileStore;
    private volatile Map<Integer, JsonNode> normalForms;

    public synchronized void refresh() {
        normalForms = null;
    }

    public JsonNode fetchNormalForm(int no) throws IOException {
        JsonNode pokemon = getNormalForms().get(no);
        if (pokemon == null) {
            throw new IOException("Calcy IV 데이터에서 포켓몬 번호 %d의 노말 폼을 찾지 못했습니다."
                    .formatted(no));
        }
        return pokemon.deepCopy();
    }

    Map<Integer, JsonNode> fetchAllNormalForms() throws IOException {
        Map<Integer, JsonNode> copy = new LinkedHashMap<>();
        getNormalForms().forEach((no, pokemon) -> copy.put(no, pokemon.deepCopy()));
        return copy;
    }

    private Map<Integer, JsonNode> getNormalForms() throws IOException {
        Map<Integer, JsonNode> cached = normalForms;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (normalForms == null) {
                normalForms = loadNormalForms();
            }
            return normalForms;
        }
    }

    private Map<Integer, JsonNode> loadNormalForms() throws IOException {
        Map<Integer, JsonNode> result = new LinkedHashMap<>();
        for (int part = 0; part < RESOURCE_PART_COUNT; part++) {
            String relativePath = DATA_FILE_PATTERN.formatted(part);
            Path file = dataFileStore.prepare(relativePath);
            try (InputStream input = Files.exists(file) ? Files.newInputStream(file) : null) {
                if (input == null) {
                    throw new IOException("Calcy IV 데이터 파일을 찾지 못했습니다: " + file);
                }
                readResource(input, file.toString(), result);
            }
        }
        if (result.isEmpty()) {
            throw new IOException("Calcy IV 노말 폼 데이터가 비어 있습니다.");
        }
        return Map.copyOf(result);
    }

    void readResource(
            InputStream input,
            String resourceName,
            Map<Integer, JsonNode> result) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                try {
                    switch (columns[0]) {
                        case "P" -> readPokemon(columns, result);
                        case "M" -> readMove(columns, result);
                        default -> throw new IllegalArgumentException("알 수 없는 행 유형입니다.");
                    }
                } catch (RuntimeException exception) {
                    throw new IOException("잘못된 Calcy IV 데이터: %s:%d"
                            .formatted(resourceName, lineNumber), exception);
                }
            }
        }
    }

    private void readPokemon(String[] columns, Map<Integer, JsonNode> result) {
        if (columns.length != 7) {
            throw new IllegalArgumentException("포켓몬 행의 컬럼 수가 올바르지 않습니다.");
        }
        int no = Integer.parseInt(columns[1]);
        ObjectNode pokemon = objectMapper.createObjectNode();
        pokemon.put("dexNr", no);
        pokemon.putObject("stats")
                .put("attack", Integer.parseInt(columns[2]))
                .put("defense", Integer.parseInt(columns[3]))
                .put("stamina", Integer.parseInt(columns[4]));
        pokemon.putObject("primaryType").put("type", columns[5]);
        if (!columns[6].isBlank()) {
            pokemon.putObject("secondaryType").put("type", columns[6]);
        }
        pokemon.putObject("quickMoves");
        pokemon.putObject("cinematicMoves");
        pokemon.putObject("legacyQuickMoves");
        pokemon.putObject("legacyCinematicMoves");
        pokemon.putObject("eliteQuickMoves");
        pokemon.putObject("eliteCinematicMoves");
        if (result.put(no, pokemon) != null) {
            throw new IllegalArgumentException("포켓몬 번호가 중복되었습니다: " + no);
        }
    }

    private void readMove(String[] columns, Map<Integer, JsonNode> result) {
        if (columns.length != 10) {
            throw new IllegalArgumentException("기술 행의 컬럼 수가 올바르지 않습니다.");
        }
        int no = Integer.parseInt(columns[1]);
        JsonNode pokemon = result.get(no);
        if (!(pokemon instanceof ObjectNode pokemonObject)) {
            throw new IllegalArgumentException("기술보다 포켓몬 행이 먼저 나와야 합니다: " + no);
        }

        String field = switch (columns[2]) {
            case "Q" -> "quickMoves";
            case "C" -> "cinematicMoves";
            case "LQ" -> "legacyQuickMoves";
            case "LC" -> "legacyCinematicMoves";
            case "EQ" -> "eliteQuickMoves";
            case "EC" -> "eliteCinematicMoves";
            default -> throw new IllegalArgumentException("알 수 없는 기술 분류입니다: " + columns[2]);
        };

        String moveId = columns[3];
        String koreanName = new String(Base64.getDecoder().decode(columns[4]),
                StandardCharsets.UTF_8);
        ObjectNode move = objectMapper.createObjectNode();
        move.put("id", moveId);
        move.putObject("names").put("Korean", koreanName);
        move.putObject("type").put("type", columns[5]);
        move.put("power", Double.parseDouble(columns[6]));
        move.put("durationMs", Double.parseDouble(columns[7]));
        move.put("energy", Double.parseDouble(columns[8]));
        move.put("damageWindowStartMs", Double.parseDouble(columns[9]));
        ((ObjectNode) pokemonObject.path(field)).set(moveId, move);
    }
}
