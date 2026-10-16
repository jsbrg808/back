package ordinal.third.pogo.pokedex.service;

import ordinal.third.pogo.vo.PokedexJsonVO;
import ordinal.third.pogo.vo.PokemonSpeciesCsvVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PokedexDataProcessorTest {

    private final PokedexDataProcessor processor = new PokedexDataProcessor();

    private PokedexJsonVO pokemon(String formId, int dexNr, Integer attack, Integer defense, Integer stamina) {
        PokedexJsonVO vo = new PokedexJsonVO();
        vo.setId(formId);
        vo.setFormId(formId);
        vo.setDexNr(dexNr);
        vo.setKorean("이름-" + formId);
        vo.setAttack(attack);
        vo.setDefense(defense);
        vo.setStamina(stamina);
        vo.setDexFormNr(1);
        return vo;
    }

    private PokemonSpeciesCsvVO species(int id, Integer evolvesFrom, Integer chainId) {
        PokemonSpeciesCsvVO vo = new PokemonSpeciesCsvVO();
        vo.setId(id);
        vo.setEvolvesFromSpeciesId(evolvesFrom);
        vo.setEvolutionChainId(chainId);
        return vo;
    }

    @Test
    void calculateMaxCp_50레벨_CP를_계산한다() {
        // calculateMaxCp는 항상 지가르데 폼 쌍을 필요로 하므로 함께 넣는다
        List<PokedexJsonVO> pokemon = new ArrayList<>(List.of(
                pokemon("ZYGARDE_TEN_PERCENT", 718, 203, 100, 101),
                pokemon("ZYGARDE", 718, null, null, null),
                pokemon("BULBASAUR", 1, 100, 100, 100)));

        processor.calculateMaxCp(pokemon);

        // (100+15) * sqrt(115) * sqrt(115) * 0.7061041295689163 / 10 = 933.82... → 933
        assertThat(pokemon.get(2).getMaxCp()).isEqualTo(933);
    }

    @Test
    void calculateMaxCp_지가르데_10퍼센트_스탯을_기본_폼에_복사한다() {
        List<PokedexJsonVO> pokemon = new ArrayList<>(List.of(
                pokemon("ZYGARDE_TEN_PERCENT", 718, 203, 100, 101),
                pokemon("ZYGARDE", 718, null, null, null)));

        processor.calculateMaxCp(pokemon);

        PokedexJsonVO zygarde = pokemon.get(1);
        assertThat(zygarde.getAttack()).isEqualTo(203);
        assertThat(zygarde.getDefense()).isEqualTo(100);
        assertThat(zygarde.getStamina()).isEqualTo(101);
        assertThat(zygarde.getMaxCp()).isNotNull();
    }

    @Test
    void calculateMaxCp_지가르데_폼이_없으면_예외를_던진다() {
        List<PokedexJsonVO> pokemon = List.of(pokemon("BULBASAUR", 1, 100, 100, 100));

        assertThatThrownBy(() -> processor.calculateMaxCp(pokemon))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ZYGARDE_TEN_PERCENT");
    }

    @Test
    void calculateMaxCp_스탯이_누락되면_예외를_던진다() {
        List<PokedexJsonVO> pokemon = new ArrayList<>(List.of(
                pokemon("ZYGARDE_TEN_PERCENT", 718, 203, 100, 101),
                pokemon("ZYGARDE", 718, null, null, null),
                pokemon("BULBASAUR", 1, 100, null, 100)));

        assertThatThrownBy(() -> processor.calculateMaxCp(pokemon))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BULBASAUR");
    }

    @Test
    void rankableBasePokemon_기본폼만_추려_CP내림차순으로_정렬한다() {
        PokedexJsonVO low = pokemon("L", 1, 1, 1, 1);
        low.setMaxCp(300);
        PokedexJsonVO high = pokemon("H", 3, 1, 1, 1);
        high.setMaxCp(500);
        PokedexJsonVO tieBreaker = pokemon("T", 2, 1, 1, 1);
        tieBreaker.setMaxCp(500);
        PokedexJsonVO form = pokemon("F", 4, 1, 1, 1);
        form.setMaxCp(999);
        form.setDexFormNr(2);

        List<PokedexJsonVO> ranked = processor.rankableBasePokemon(List.of(low, high, tieBreaker, form));

        assertThat(ranked).extracting(PokedexJsonVO::getFormId)
                .containsExactly("H", "T", "L");
    }

    @Test
    void calculateEvolutionInfo_진화층수와_기본번호를_계산한다() {
        List<PokemonSpeciesCsvVO> species = new ArrayList<>(List.of(
                species(1, null, 10),
                species(2, 1, 10),
                species(3, 2, 10),
                species(20, null, null)));

        processor.calculateEvolutionInfo(species);

        assertThat(species.get(0).getBaseNo()).isEqualTo(1);
        assertThat(species.get(0).getEvl()).isEqualTo(0);
        assertThat(species.get(1).getBaseNo()).isEqualTo(1);
        assertThat(species.get(1).getEvl()).isEqualTo(1);
        assertThat(species.get(2).getBaseNo()).isEqualTo(1);
        assertThat(species.get(2).getEvl()).isEqualTo(2);
        // 진화 체인이 없는 종은 기본번호와 층수가 비어 있다
        assertThat(species.get(3).getBaseNo()).isNull();
        assertThat(species.get(3).getEvl()).isEqualTo(0);
    }

    @Test
    void calculateEvolutionInfo_순환_진화는_예외를_던진다() {
        List<PokemonSpeciesCsvVO> species = List.of(
                species(1, 2, 30),
                species(2, 1, 30));

        assertThatThrownBy(() -> processor.calculateEvolutionInfo(species))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("순환");
    }

    @Test
    void calculateEvolutionInfo_존재하지_않는_부모면_예외를_던진다() {
        List<PokemonSpeciesCsvVO> species = List.of(species(1, 99, 40));

        assertThatThrownBy(() -> processor.calculateEvolutionInfo(species))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("99");
    }

    @Test
    void genderRateCode() {
        assertThat(processor.genderRateCode(-1)).isEqualTo("00");
        assertThat(processor.genderRateCode(0)).isEqualTo("10");
        assertThat(processor.genderRateCode(1)).isEqualTo("71");
        assertThat(processor.genderRateCode(2)).isEqualTo("31");
        assertThat(processor.genderRateCode(4)).isEqualTo("11");
        assertThat(processor.genderRateCode(6)).isEqualTo("13");
        assertThat(processor.genderRateCode(7)).isEqualTo("17");
        assertThat(processor.genderRateCode(8)).isEqualTo("01");
        assertThatThrownBy(() -> processor.genderRateCode(3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3");
    }

    @Test
    void pokemonClassCode() {
        assertThat(processor.pokemonClassCode("POKEMON_CLASS_LEGENDARY")).isEqualTo("L");
        assertThat(processor.pokemonClassCode("POKEMON_CLASS_MYTHIC")).isEqualTo("M");
        assertThat(processor.pokemonClassCode("POKEMON_CLASS_ULTRA_BEAST")).isEqualTo("U");
        assertThat(processor.pokemonClassCode(null)).isNull();
        assertThatThrownBy(() -> processor.pokemonClassCode("POKEMON_CLASS_NORMAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("POKEMON_CLASS_NORMAL");
    }
}
