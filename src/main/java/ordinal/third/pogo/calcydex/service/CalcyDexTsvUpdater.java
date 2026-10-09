package ordinal.third.pogo.calcydex.service;

import lombok.RequiredArgsConstructor;
import ordinal.third.pogo.data.PogoDataFileStore;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class CalcyDexTsvUpdater {

    static final int PART_COUNT = 11;
    private static final String DATA_FILE_PATTERN = "calcydex/normal-forms-%02d.tsv";
    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "^# Calcy IV (\\S+) normal-form calculation data$");
    private static final Map<Integer, String> TYPE_NAMES = Map.ofEntries(
            Map.entry(-1, ""),
            Map.entry(0, "POKEMON_TYPE_NORMAL"),
            Map.entry(1, "POKEMON_TYPE_FIGHTING"),
            Map.entry(2, "POKEMON_TYPE_FLYING"),
            Map.entry(3, "POKEMON_TYPE_POISON"),
            Map.entry(4, "POKEMON_TYPE_GROUND"),
            Map.entry(5, "POKEMON_TYPE_ROCK"),
            Map.entry(6, "POKEMON_TYPE_BUG"),
            Map.entry(7, "POKEMON_TYPE_GHOST"),
            Map.entry(8, "POKEMON_TYPE_STEEL"),
            Map.entry(9, "POKEMON_TYPE_FIRE"),
            Map.entry(10, "POKEMON_TYPE_WATER"),
            Map.entry(11, "POKEMON_TYPE_GRASS"),
            Map.entry(12, "POKEMON_TYPE_ELECTRIC"),
            Map.entry(13, "POKEMON_TYPE_PSYCHIC"),
            Map.entry(14, "POKEMON_TYPE_ICE"),
            Map.entry(15, "POKEMON_TYPE_DRAGON"),
            Map.entry(16, "POKEMON_TYPE_DARK"),
            Map.entry(17, "POKEMON_TYPE_FAIRY"),
            Map.entry(18, "POKEMON_TYPE_UNKNOWN"));
    private static final List<MoveTable> MOVE_TABLES = List.of(
            new MoveTable("MonsterMoves", "Q", "C"),
            new MoveTable("MonsterLegacyMoves", "LQ", "LC"),
            new MoveTable("MonsterEliteTmMoves", "EQ", "EC"));

    private final PogoDataFileStore dataFileStore;

    Optional<String> localDataVersion() throws IOException {
        String version = null;
        for (int part = 0; part < PART_COUNT; part++) {
            Path file = dataFileStore.prepare(DATA_FILE_PATTERN.formatted(part));
            if (!Files.isRegularFile(file) || Files.size(file) == 0) {
                return Optional.empty();
            }
            String header;
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                header = reader.readLine();
            }
            Matcher matcher = HEADER_PATTERN.matcher(header == null ? "" : header);
            if (!matcher.matches()) {
                return Optional.empty();
            }
            if (version == null) {
                version = matcher.group(1);
            } else if (!version.equals(matcher.group(1))) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(version);
    }

    public int update(Path database, String sourceVersion, String sourceSha256) throws IOException {
        if (!Files.isRegularFile(database) || Files.size(database) == 0) {
            throw new IOException("Calcy IV gamestats.db 파일이 비어 있습니다.");
        }

        Map<Integer, List<String>> linesByPart = initializeParts(sourceVersion, sourceSha256);
        int pokemonCount;
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toAbsolutePath().normalize())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("pragma query_only=on");
            }
            validateSchema(connection);
            String hiddenPowerName = hiddenPowerName(connection);
            pokemonCount = readPokemon(connection, hiddenPowerName, linesByPart);
        } catch (SQLException exception) {
            throw new IOException("Calcy IV gamestats.db를 읽지 못했습니다.", exception);
        }
        if (pokemonCount == 0) {
            throw new IOException("Calcy IV 노말 폼 데이터가 비어 있습니다.");
        }
        writeParts(linesByPart);
        return pokemonCount;
    }

    private Map<Integer, List<String>> initializeParts(String version, String sha256) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        for (int part = 0; part < PART_COUNT; part++) {
            List<String> lines = new ArrayList<>();
            lines.add("# Calcy IV " + version + " normal-form calculation data");
            lines.add("# source-xapk-sha256 " + sha256);
            lines.add("# P no attack defense stamina primaryType secondaryType");
            lines.add("# M no bucket moveId nameKOBase64 type power durationMs energy damageWindowStartMs");
            result.put(part, lines);
        }
        return result;
    }

    private void validateSchema(Connection connection) throws SQLException, IOException {
        for (String table : List.of(
                "BaseMonsters", "Monsters", "Moves",
                "MonsterMoves", "MonsterLegacyMoves", "MonsterEliteTmMoves")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select count(*) from sqlite_master where type='table' and name=?")) {
                statement.setString(1, table);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        throw new IOException("Calcy IV DB에 필수 테이블이 없습니다: " + table);
                    }
                }
            }
        }
    }

    private String hiddenPowerName(Connection connection) throws SQLException, IOException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "select nameKO from Moves where nameEN='Hidden Power' limit 1")) {
            if (!result.next() || result.getString(1) == null) {
                throw new IOException("Calcy IV DB에서 잠재파워 기술명을 찾지 못했습니다.");
            }
            return result.getString(1);
        }
    }

    private int readPokemon(
            Connection connection,
            String hiddenPowerName,
            Map<Integer, List<String>> linesByPart) throws SQLException, IOException {
        int count = 0;
        String sql = """
                select b.number,m.id,m.baseAtt,m.baseDef,m.baseSta,m.type1,m.type2
                  from BaseMonsters b
                  join Monsters m on m.id=b.educatedFormId
                 order by b.number
                """;
        try (Statement statement = connection.createStatement();
             ResultSet pokemon = statement.executeQuery(sql)) {
            while (pokemon.next()) {
                int number = pokemon.getInt("number");
                int part = (number - 1) / 100;
                List<String> lines = linesByPart.get(part);
                if (lines == null) {
                    throw new IOException("지원 범위를 벗어난 포켓몬 번호입니다: " + number);
                }
                int monsterId = pokemon.getInt("id");
                lines.add(String.join("\t",
                        "P", Integer.toString(number),
                        pokemon.getString("baseAtt"), pokemon.getString("baseDef"),
                        pokemon.getString("baseSta"), typeName(pokemon.getInt("type1")),
                        typeName(pokemon.getInt("type2"))));
                readMoves(connection, number, monsterId, hiddenPowerName, lines);
                count++;
            }
        }
        return count;
    }

    private void readMoves(
            Connection connection,
            int number,
            int monsterId,
            String hiddenPowerName,
            List<String> lines) throws SQLException, IOException {
        for (MoveTable moveTable : MOVE_TABLES) {
            String sql = """
                    select mv.id,mv.form,mv.nameEN,mv.nameKO,mv.type,mv.power,
                           mv.duration,mv.energy,mv.damageWindowStartMs
                      from %s mm join Moves mv on mv.id=mm.moveId
                     where mm.monsterId=?
                     order by mv.form,mv.id
                    """.formatted(moveTable.table());
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, monsterId);
                try (ResultSet move = statement.executeQuery()) {
                    while (move.next()) {
                        String englishName = move.getString("nameEN");
                        String koreanName = englishName.startsWith("Hidden Power")
                                ? hiddenPowerName : move.getString("nameKO");
                        String moveId = "Struggle".equals(englishName)
                                ? "STRUGGLE" : "CALCY_MOVE_" + move.getInt("id");
                        String bucket = move.getInt("form") == 0
                                ? moveTable.regularBucket() : moveTable.legacyBucket();
                        lines.add(String.join("\t",
                                "M", Integer.toString(number), bucket, moveId,
                                Base64.getEncoder().encodeToString(
                                        koreanName.getBytes(StandardCharsets.UTF_8)),
                                typeName(move.getInt("type")),
                                databaseValue(move, "power"),
                                Long.toString(Math.round(move.getDouble("duration") * 1000)),
                                databaseValue(move, "energy"),
                                databaseValue(move, "damageWindowStartMs")));
                    }
                }
            }
        }
    }

    private String databaseValue(ResultSet result, String column) throws SQLException {
        Object value = result.getObject(column);
        return value == null ? "" : value.toString();
    }

    private String typeName(int type) throws IOException {
        String name = TYPE_NAMES.get(type);
        if (name == null) {
            throw new IOException("알 수 없는 Calcy IV 타입 번호입니다: " + type);
        }
        return name;
    }

    private void writeParts(Map<Integer, List<String>> linesByPart) throws IOException {
        Map<Path, Path> candidates = new LinkedHashMap<>();
        try {
            for (int part = 0; part < PART_COUNT; part++) {
                Path target = dataFileStore.prepare(DATA_FILE_PATTERN.formatted(part));
                Path candidate = target.resolveSibling(target.getFileName() + ".tmp");
                Files.write(candidate, linesByPart.get(part), StandardCharsets.UTF_8);
                candidates.put(target, candidate);
            }
            for (Map.Entry<Path, Path> candidate : candidates.entrySet()) {
                replace(candidate.getValue(), candidate.getKey());
            }
        } finally {
            for (Path candidate : candidates.values()) {
                Files.deleteIfExists(candidate);
            }
        }
    }

    private void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record MoveTable(String table, String regularBucket, String legacyBucket) {
    }
}
