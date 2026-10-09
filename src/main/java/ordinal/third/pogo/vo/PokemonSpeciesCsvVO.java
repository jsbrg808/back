package ordinal.third.pogo.vo;

import lombok.Data;

@Data
public class PokemonSpeciesCsvVO {
    private Integer id;
    private Integer evolvesFromSpeciesId;
    private Integer evolutionChainId;
    private Integer baseNo;
    private Integer evl;
    private Integer genderRate;
}
