package ordinal.third.pogo.pokedex.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import ordinal.third.pogo.vo.PokemonSpeciesCsvVO;
import java.util.List;

@Mapper
public interface PokemonSpeciesCsvMapper {
    void deleteAll();
    void insertBatch(@Param("list") List<PokemonSpeciesCsvVO> list);
    List<PokemonSpeciesCsvVO> selectAll();
    void updateBaseNoAndEvlBatch(@Param("list") List<PokemonSpeciesCsvVO> list);
    void updateGenderRateFor916();
}
