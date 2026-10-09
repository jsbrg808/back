package ordinal.third.pogo.calcydex.service;

import com.fasterxml.jackson.databind.JsonNode;
import ordinal.third.pogo.vo.CalcyDexMoveSetVO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CalcyDexMoveSetCalculator {

    private static final double STAB = 1.2;
    private static final double PERFECT_ATTACK_IV = 15.0;
    private static final double LEVEL_30_CPM = 0.7317;
    private static final double ENEMY_ATTACK = 200.0;
    private static final double ENEMY_DPS_SCALING = 4.0;
    private static final double CALCYDEX_DEFENDER_DEFENSE = 70.0;
    private static final double STRUGGLE_ENERGY_COST = 33.0;

    public CalculationResult calculate(int no, JsonNode pokemon) {
        JsonNode stats = requireObject(pokemon, "stats", no);
        double attack = requirePositive(stats.path("attack").asDouble(), "attack", no);
        double defense = requirePositive(stats.path("defense").asDouble(), "defense", no);
        double stamina = requirePositive(stats.path("stamina").asDouble(), "stamina", no);

        String primaryType = pokemon.path("primaryType").path("type").asText(null);
        String secondaryType = pokemon.path("secondaryType").path("type").asText(null);

        Map<String, Move> fastMoves = new LinkedHashMap<>();
        addMoves(fastMoves, pokemon.path("quickMoves"), false, true);
        addMoves(fastMoves, pokemon.path("eliteQuickMoves"), true, true);
        addMoves(fastMoves, pokemon.path("legacyQuickMoves"), true, true);

        Map<String, Move> chargedMoves = new LinkedHashMap<>();
        addMoves(chargedMoves, pokemon.path("cinematicMoves"), false, false);
        addMoves(chargedMoves, pokemon.path("eliteCinematicMoves"), true, false);
        addMoves(chargedMoves, pokemon.path("legacyCinematicMoves"), true, false);
        addMoves(chargedMoves, pokemon.path("nonTmCinematicMoves"), true, false);

        if (fastMoves.isEmpty() || chargedMoves.isEmpty()) {
            throw new IllegalStateException("포켓몬 번호 " + no + "의 PvE 기술 데이터가 없습니다.");
        }

        Map<MoveSetDisplayKey, ScoredMoveSet> bestByDisplayedMoveSet = new LinkedHashMap<>();
        for (Move fastMove : fastMoves.values()) {
            for (Move chargedMove : chargedMoves.values()) {
                double dps = comprehensiveDps(
                        attack, defense, stamina,
                        primaryType, secondaryType,
                        fastMove, chargedMove);
                ScoredMoveSet candidate = new ScoredMoveSet(fastMove, chargedMove, dps);
                MoveSetDisplayKey key = new MoveSetDisplayKey(
                        fastMove.koreanName(), fastMove.legacy(),
                        chargedMove.koreanName(), chargedMove.legacy());
                bestByDisplayedMoveSet.merge(key, candidate,
                        (current, replacement) -> replacement.dps() > current.dps()
                                ? replacement : current);
            }
        }

        List<ScoredMoveSet> scored = new ArrayList<>(bestByDisplayedMoveSet.values());
        scored.sort(Comparator.comparingDouble(ScoredMoveSet::dps).reversed()
                .thenComparing(moveSet -> moveSet.fastMove().id())
                .thenComparing(moveSet -> moveSet.chargedMove().id()));

        double regularBestDps = scored.stream()
                .filter(moveSet -> !moveSet.fastMove().legacy() && !moveSet.chargedMove().legacy())
                .mapToDouble(ScoredMoveSet::dps)
                .max()
                .orElseThrow(() -> new IllegalStateException(
                        "포켓몬 번호 " + no + "에 일반 기술만으로 구성된 Move Set이 없습니다."));

        List<CalcyDexMoveSetVO> selected = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            ScoredMoveSet moveSet = scored.get(i);
            double percent = moveSet.dps() / regularBestDps * 100.0;
            BigDecimal roundedPercent = decimalOne(percent);
            if (roundedPercent.compareTo(new BigDecimal("100.0")) < 0) {
                continue;
            }
            selected.add(new CalcyDexMoveSetVO(
                    no,
                    i + 1,
                    moveSet.fastMove().koreanName(),
                    moveSet.fastMove().legacy() ? "L" : null,
                    moveSet.chargedMove().koreanName(),
                    moveSet.chargedMove().legacy() ? "L" : null,
                    decimalOne(moveSet.dps()),
                    roundedPercent
            ));
        }

        return new CalculationResult(selected, scored.size());
    }

    private double comprehensiveDps(
            double attack,
            double defense,
            double stamina,
            String primaryType,
            String secondaryType,
            Move fastMove,
            Move chargedMove) {

        double fastDamage = fastMove.power()
                * stabMultiplier(fastMove.type(), primaryType, secondaryType) + 0.5;
        double chargedDamage = chargedMove.power()
                * stabMultiplier(chargedMove.type(), primaryType, secondaryType) + 0.5;

        double level30Attack = (attack + PERFECT_ATTACK_IV) * LEVEL_30_CPM;
        double level30Defense = (defense + PERFECT_ATTACK_IV) * LEVEL_30_CPM;
        double level30Hp = Math.floor((stamina + PERFECT_ATTACK_IV) * LEVEL_30_CPM);
        double enemyDps = ENEMY_DPS_SCALING * ENEMY_ATTACK / level30Defense;
        double incomingEnergyPerSecond = Math.min(level30Hp / 4.0, enemyDps / 2.0);
        double effectiveChargedEnergy = chargedMove.energy();

        if (chargedMove.energy() >= 100.0) {
            effectiveChargedEnergy += 0.5 * Math.max(0.0, fastMove.energy() - 1.0)
                    + incomingEnergyPerSecond * chargedMove.damageWindowStartSeconds();
        }

        double fastDps = fastDamage / fastMove.durationSeconds();
        double fastEps = fastMove.energy() / fastMove.durationSeconds();
        double chargedDps = chargedDamage / chargedMove.durationSeconds();
        double chargedEps = effectiveChargedEnergy / chargedMove.durationSeconds();

        double damageDifference = chargedDps - fastDps;
        double weaveDps = fastDps
                + damageDifference / (chargedEps + fastEps)
                * (fastEps + incomingEnergyPerSecond)
                - Math.max(0.0, incomingEnergyPerSecond / level30Hp
                * chargedMove.durationSeconds() * damageDifference);
        double calcyDexDpsScale = 0.5 * level30Attack / CALCYDEX_DEFENDER_DEFENSE;
        return Math.max(fastDps, weaveDps) * calcyDexDpsScale;
    }

    private double stabMultiplier(String moveType, String primaryType, String secondaryType) {
        return moveType != null && (moveType.equals(primaryType) || moveType.equals(secondaryType))
                ? STAB : 1.0;
    }

    private void addMoves(Map<String, Move> target, JsonNode movesNode, boolean legacy, boolean fast) {
        if (!movesNode.isObject()) {
            return;
        }
        movesNode.fields().forEachRemaining(entry -> {
            JsonNode node = entry.getValue();
            String id = node.path("id").asText(entry.getKey());
            if (target.containsKey(id)) {
                return;
            }
            double power = node.path("power").asDouble();
            double rawEnergy = node.path("energy").asDouble();
            double durationSeconds = node.path("durationMs").asDouble() / 1000.0;
            double damageWindowStartSeconds = node.has("damageWindowStartMs")
                    ? node.path("damageWindowStartMs").asDouble() / 1000.0
                    : durationSeconds * 0.85;
            String koreanName = node.path("names").path("Korean").asText(null);
            String type = node.path("type").path("type").asText(null);

            if (power < 0 || durationSeconds <= 0 || koreanName == null || koreanName.isBlank()) {
                throw new IllegalStateException("기술 " + id + "의 PvE 데이터가 올바르지 않습니다.");
            }
            double energy = fast ? rawEnergy : Math.abs(rawEnergy);
            if (!fast && energy == 0 && "STRUGGLE".equals(id)) {
                // pokemon-go-api는 아직 정식 기술 풀이 없는 일부 포켓몬과 Ditto의
                // STRUGGLE 에너지를 0으로 제공한다. PvE의 3-bar 비용으로 보정한다.
                energy = STRUGGLE_ENERGY_COST;
            }
            if (energy <= 0) {
                throw new IllegalStateException("기술 " + id + "의 에너지 데이터가 올바르지 않습니다.");
            }
            target.put(id, new Move(id, koreanName, type, power, energy, durationSeconds,
                    damageWindowStartSeconds, legacy));
        });
    }

    private JsonNode requireObject(JsonNode parent, String field, int no) {
        JsonNode node = parent.path(field);
        if (!node.isObject()) {
            throw new IllegalStateException("포켓몬 번호 " + no + "의 " + field + " 데이터가 없습니다.");
        }
        return node;
    }

    private double requirePositive(double value, String field, int no) {
        if (value <= 0) {
            throw new IllegalStateException("포켓몬 번호 " + no + "의 " + field + " 값이 올바르지 않습니다.");
        }
        return value;
    }

    private BigDecimal decimalOne(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    public record CalculationResult(List<CalcyDexMoveSetVO> selectedMoveSets, int totalCombinationCount) {
    }

    private record Move(
            String id,
            String koreanName,
            String type,
            double power,
            double energy,
            double durationSeconds,
            double damageWindowStartSeconds,
            boolean legacy) {
    }

    private record ScoredMoveSet(Move fastMove, Move chargedMove, double dps) {
    }

    private record MoveSetDisplayKey(
            String fastMoveName,
            boolean fastMoveLegacy,
            String chargedMoveName,
            boolean chargedMoveLegacy) {
    }
}
