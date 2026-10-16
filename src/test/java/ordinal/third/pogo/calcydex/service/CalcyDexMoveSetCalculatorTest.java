package ordinal.third.pogo.calcydex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalcyDexMoveSetCalculatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CalcyDexMoveSetCalculator calculator = new CalcyDexMoveSetCalculator();

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String BASE_POKEMON = """
            {
              "stats": { "attack": 255, "defense": 100, "stamina": 200 },
              "primaryType": { "type": "ELECTRIC" },
              "quickMoves": {
                "f1": { "id": "THUNDER_SHOCK", "names": { "Korean": "전기쇼크" },
                        "type": { "type": "ELECTRIC" }, "power": 5, "energy": 8,
                        "durationMs": 600, "damageWindowStartMs": 300 },
                "f2": { "id": "SPARK", "names": { "Korean": "스파크" },
                        "type": { "type": "WATER" }, "power": 3, "energy": 10,
                        "durationMs": 1230, "damageWindowStartMs": 1000 }
              },
              "cinematicMoves": {
                "c1": { "id": "THUNDERBOLT", "names": { "Korean": "10만볼트" },
                        "type": { "type": "ELECTRIC" }, "power": 90, "energy": 55,
                        "durationMs": 2500, "damageWindowStartMs": 2200 },
                "c2": { "id": "THUNDER", "names": { "Korean": "천둥" },
                        "type": { "type": "ELECTRIC" }, "power": 70, "energy": 80,
                        "durationMs": 2800, "damageWindowStartMs": 2500 }
              }
            }
            """;

    @Test
    void 기술조합을_계산해_DPS순으로_정렬된_선택목록을_반환한다() {
        var result = calculator.calculate(25, json(BASE_POKEMON));

        assertThat(result.totalCombinationCount()).isEqualTo(4);
        assertThat(result.selectedMoveSets()).isNotEmpty();

        var first = result.selectedMoveSets().get(0);
        assertThat(first.getNo()).isEqualTo(25);
        assertThat(first.getMoveSetNo()).isEqualTo(1);
        // STAB이 적용되는 최상위 조합
        assertThat(first.getNrmlMove()).isEqualTo("전기쇼크");
        assertThat(first.getNrmlDv()).isNull();
        assertThat(first.getSpclMove()).isEqualTo("10만볼트");
        assertThat(first.getSpclDv()).isNull();
        // 일반 조합 기준 백분율이므로 최상위는 반올림 100.0이고, 나머지는 그 미만으로 걸러진다
        assertThat(first.getPercent()).isEqualByComparingTo("100.0");
        assertThat(result.selectedMoveSets())
                .allSatisfy(vo -> assertThat(vo.getPercent()).isGreaterThanOrEqualTo(java.math.BigDecimal.valueOf(100.0)));
        // DPS 내림차순 정렬 확인
        for (int i = 1; i < result.selectedMoveSets().size(); i++) {
            assertThat(result.selectedMoveSets().get(i).getDps())
                    .isLessThanOrEqualTo(result.selectedMoveSets().get(i - 1).getDps());
        }
    }

    @Test
    void 자속_타입_기술이_DPS를_높인다_STAB() {
        JsonNode stabPokemon = json(BASE_POKEMON);
        JsonNode noStabPokemon = json(BASE_POKEMON
                .replace("\"ELECTRIC\" }, \"power\": 5", "\"NORMAL\" }, \"power\": 5")
                .replace("\"ELECTRIC\" }, \"power\": 90", "\"NORMAL\" }, \"power\": 90"));

        double stabDps = calculator.calculate(25, stabPokemon).selectedMoveSets().get(0).getDps().doubleValue();
        double noStabDps = calculator.calculate(25, noStabPokemon).selectedMoveSets().get(0).getDps().doubleValue();

        assertThat(stabDps).isGreaterThan(noStabDps);
    }

    @Test
    void 레거시_기술은_L_표시를_붙인다() {
        String withLegacy = """
                {
                  "stats": { "attack": 255, "defense": 100, "stamina": 200 },
                  "primaryType": { "type": "ELECTRIC" },
                  "quickMoves": {
                    "f1": { "id": "THUNDER_SHOCK", "names": { "Korean": "전기쇼크" },
                            "type": { "type": "ELECTRIC" }, "power": 5, "energy": 8,
                            "durationMs": 600, "damageWindowStartMs": 300 }
                  },
                  "cinematicMoves": {
                    "c1": { "id": "THUNDERBOLT", "names": { "Korean": "10만볼트" },
                            "type": { "type": "ELECTRIC" }, "power": 90, "energy": 55,
                            "durationMs": 2500, "damageWindowStartMs": 2200 }
                  },
                  "eliteCinematicMoves": {
                    "c1l": { "id": "MAGICAL_LEAF", "names": { "Korean": "10만볼트" },
                             "type": { "type": "ELECTRIC" }, "power": 90, "energy": 55,
                             "durationMs": 2500, "damageWindowStartMs": 2200 }
                  }
                }
                """;
        JsonNode parsed = json(withLegacy);

        var result = calculator.calculate(25, parsed);

        // 일반/레거시 두 조합의 DPS와 표시 이름은 같지만 "L" 표시로 표시 키가 달라져 별도 항목으로 남는다
        assertThat(result.totalCombinationCount()).isEqualTo(2);
        var legacyEntry = result.selectedMoveSets().stream()
                .filter(vo -> "L".equals(vo.getSpclDv()))
                .findFirst();
        assertThat(legacyEntry).isPresent();
        assertThat(legacyEntry.get().getSpclMove()).isEqualTo("10만볼트");
        var regularEntry = result.selectedMoveSets().stream()
                .filter(vo -> vo.getSpclDv() == null)
                .findFirst();
        assertThat(regularEntry).isPresent();
        assertThat(regularEntry.get().getDps()).isEqualByComparingTo(legacyEntry.get().getDps());
    }

    @Test
    void STRUGGLE_에너지가_0으로_제공되면_보정한다() {
        String ditto = """
                {
                  "stats": { "attack": 91, "defense": 91, "stamina": 48 },
                  "primaryType": { "type": "NORMAL" },
                  "quickMoves": {
                    "f1": { "id": "TRANSFORM", "names": { "Korean": "변신" },
                            "type": { "type": "NORMAL" }, "power": 1, "energy": 1,
                            "durationMs": 350, "damageWindowStartMs": 200 }
                  },
                  "cinematicMoves": {
                    "c1": { "id": "STRUGGLE", "names": { "Korean": "발버둥" },
                            "type": { "type": "NORMAL" }, "power": 45, "energy": 0,
                            "durationMs": 2150, "damageWindowStartMs": 1800 }
                  }
                }
                """;
        var result = calculator.calculate(132, json(ditto));

        // 에너지 0 보정으로 예외 없이 결과가 나온다
        assertThat(result.selectedMoveSets()).isNotEmpty();
        assertThat(result.selectedMoveSets().get(0).getSpclMove()).isEqualTo("발버둥");
    }

    @Test
    void STRUGGLE이_아닌_기술의_에너지가_0이면_예외를_던진다() {
        String broken = BASE_POKEMON.replace("\"energy\": 55,", "\"energy\": 0,");
        assertThatThrownBy(() -> calculator.calculate(25, json(broken)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("에너지 데이터가 올바르지 않습니다");
    }

    @Test
    void 빠른기술이_없으면_예외를_던진다() {
        String noFast = BASE_POKEMON.replace("\"quickMoves\"", "\"otherMoves\"");
        assertThatThrownBy(() -> calculator.calculate(25, json(noFast)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PvE 기술 데이터가 없습니다");
    }

    @Test
    void 특수기술이_없으면_예외를_던진다() {
        String noCharged = BASE_POKEMON.replace("\"cinematicMoves\"", "\"otherMoves\"");
        assertThatThrownBy(() -> calculator.calculate(25, json(noCharged)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PvE 기술 데이터가 없습니다");
    }

    @Test
    void 일반_기술만으로_된_조합이_없으면_예외를_던진다() {
        String onlyLegacy = BASE_POKEMON
                .replace("\"quickMoves\"", "\"legacyQuickMoves\"")
                .replace("\"cinematicMoves\"", "\"legacyCinematicMoves\"");
        assertThatThrownBy(() -> calculator.calculate(25, json(onlyLegacy)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("일반 기술만으로 구성된 Move Set이 없습니다");
    }

    @Test
    void 기술_데이터가_음수_위력이면_예외를_던진다() {
        String negativePower = BASE_POKEMON.replace("\"power\": 3,", "\"power\": -3,");
        assertThatThrownBy(() -> calculator.calculate(25, json(negativePower)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PvE 데이터가 올바르지 않습니다");
    }

    @Test
    void 한국어_기술명이_없으면_예외를_던진다() {
        String noName = BASE_POKEMON.replace("\"Korean\": \"스파크\"", "\"English\": \"Spark\"");
        assertThatThrownBy(() -> calculator.calculate(25, json(noName)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PvE 데이터가 올바르지 않습니다");
    }
}
