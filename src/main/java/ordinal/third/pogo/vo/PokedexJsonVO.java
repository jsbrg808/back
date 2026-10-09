package ordinal.third.pogo.vo;

import lombok.Data;

@Data
public class PokedexJsonVO {
    private String id;
    private String formId;
    private Integer dexNr;
    private String korean;
    private Integer attack;
    private Integer defense;
    private Integer stamina;
    private String pokemonClass;
    private String image;
    private Integer dexFormNr;
    private String imageG2;
    private Integer maxCp;
}
