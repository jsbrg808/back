package ordinal.third.pogo.pokedex.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import ordinal.third.pogo.vo.PokedexJsonVO;
import java.util.List;

@Mapper
public interface PokedexJsonMapper {
    void deleteAll();
    void insertBatch(@Param("list") List<PokedexJsonVO> list);
    List<PokedexJsonVO> selectAll();
    void updateEvolutionAndCp(PokedexJsonVO vo);
    void upsertPokedex(@Param("no") Integer no,
                       @Param("name") String name,
                       @Param("dv") String dv,
                       @Param("rankNo") Integer rankNo);

    void updatePokedexStep3_1(@Param("no") Integer no,
                              @Param("baseNo") Integer baseNo,
                              @Param("evl") Integer evl,
                              @Param("gndrRt") String gndrRt);
}
