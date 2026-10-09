package ordinal.third.pogo.pokedex.service;

import ordinal.third.pogo.vo.PokedexJsonVO;
import ordinal.third.pogo.vo.PokemonSpeciesCsvVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PokedexDataProcessor {

    private static final double LEVEL_50_CPM_SQUARED = 0.7061041295689163;

    public void calculateMaxCp(List<PokedexJsonVO> pokemon) {
        copyZygardeTenPercentStats(pokemon);
        for (PokedexJsonVO vo : pokemon) {
            if (vo.getAttack() == null || vo.getDefense() == null || vo.getStamina() == null) {
                throw new IllegalStateException(
                        "포켓몬(이름: " + vo.getKorean() + ", ID: " + vo.getId()
                                + ")의 스탯(attack, defense, stamina) 정보가 누락되었습니다.");
            }
            double attack = vo.getAttack() + 15;
            double defense = vo.getDefense() + 15;
            double stamina = vo.getStamina() + 15;
            int maxCp = (int) Math.max(10, Math.floor(
                    attack * Math.sqrt(defense) * Math.sqrt(stamina)
                            * LEVEL_50_CPM_SQUARED / 10.0));
            vo.setMaxCp(maxCp);
        }
    }

    public List<PokedexJsonVO> rankableBasePokemon(List<PokedexJsonVO> pokemon) {
        List<PokedexJsonVO> basePokemon = new ArrayList<>();
        for (PokedexJsonVO vo : pokemon) {
            if (Integer.valueOf(1).equals(vo.getDexFormNr())) {
                basePokemon.add(vo);
            }
        }
        basePokemon.sort((left, right) -> {
            int cpComparison = Integer.compare(right.getMaxCp(), left.getMaxCp());
            return cpComparison != 0
                    ? cpComparison
                    : Integer.compare(right.getDexNr(), left.getDexNr());
        });
        return basePokemon;
    }

    public void calculateEvolutionInfo(List<PokemonSpeciesCsvVO> species) {
        Map<Integer, Integer> minIdByChain = new HashMap<>();
        for (PokemonSpeciesCsvVO vo : species) {
            if (vo.getEvolutionChainId() != null) {
                minIdByChain.merge(vo.getEvolutionChainId(), vo.getId(), Math::min);
            }
        }

        Map<Integer, PokemonSpeciesCsvVO> speciesById = new HashMap<>();
        for (PokemonSpeciesCsvVO vo : species) {
            vo.setBaseNo(vo.getEvolutionChainId() == null
                    ? null : minIdByChain.get(vo.getEvolutionChainId()));
            vo.setEvl(null);
            speciesById.put(vo.getId(), vo);
        }
        for (PokemonSpeciesCsvVO vo : species) {
            calculateEvolutionLevel(vo, speciesById, new HashSet<>());
        }
    }

    public String genderRateCode(int genderRate) {
        return switch (genderRate) {
            case -1 -> "00";
            case 0 -> "10";
            case 1 -> "71";
            case 2 -> "31";
            case 4 -> "11";
            case 6 -> "13";
            case 7 -> "17";
            case 8 -> "01";
            default -> throw new IllegalArgumentException(
                    "지원되지 않는 gender_rate 값입니다: " + genderRate);
        };
    }

    public String pokemonClassCode(String pokemonClass) {
        if (pokemonClass == null) {
            return null;
        }
        return switch (pokemonClass) {
            case "POKEMON_CLASS_LEGENDARY" -> "L";
            case "POKEMON_CLASS_MYTHIC" -> "M";
            case "POKEMON_CLASS_ULTRA_BEAST" -> "U";
            default -> throw new IllegalArgumentException(
                    "지원되지 않는 pokemonClass 값입니다: " + pokemonClass);
        };
    }

    private void copyZygardeTenPercentStats(List<PokedexJsonVO> pokemon) {
        PokedexJsonVO zygardeTenPercent = null;
        PokedexJsonVO zygarde = null;
        for (PokedexJsonVO vo : pokemon) {
            if ("ZYGARDE_TEN_PERCENT".equals(vo.getFormId())) {
                zygardeTenPercent = vo;
            } else if ("ZYGARDE".equals(vo.getFormId())) {
                zygarde = vo;
            }
        }
        if (zygardeTenPercent == null) {
            throw new IllegalStateException(
                    "지가르데 10% 폼(ZYGARDE_TEN_PERCENT) 데이터를 찾을 수 없습니다.");
        }
        if (zygarde == null) {
            throw new IllegalStateException("지가르데 기본 폼(ZYGARDE) 데이터를 찾을 수 없습니다.");
        }
        zygarde.setAttack(zygardeTenPercent.getAttack());
        zygarde.setDefense(zygardeTenPercent.getDefense());
        zygarde.setStamina(zygardeTenPercent.getStamina());
    }

    private int calculateEvolutionLevel(
            PokemonSpeciesCsvVO species,
            Map<Integer, PokemonSpeciesCsvVO> speciesById,
            Set<Integer> visiting) {
        if (species.getEvl() != null) {
            return species.getEvl();
        }
        if (species.getEvolvesFromSpeciesId() == null) {
            species.setEvl(0);
            return 0;
        }
        if (!visiting.add(species.getId())) {
            throw new IllegalStateException(
                    "데이터 정합성 오류: 진화 관계에 순환이 있습니다. ID " + species.getId());
        }
        try {
            PokemonSpeciesCsvVO parent = speciesById.get(species.getEvolvesFromSpeciesId());
            if (parent == null) {
                throw new IllegalStateException(
                        "데이터 정합성 오류: ID " + species.getId() + "의 부모 ID "
                                + species.getEvolvesFromSpeciesId() + "를 찾을 수 없습니다.");
            }
            int level = calculateEvolutionLevel(parent, speciesById, visiting) + 1;
            species.setEvl(level);
            return level;
        } finally {
            visiting.remove(species.getId());
        }
    }
}
