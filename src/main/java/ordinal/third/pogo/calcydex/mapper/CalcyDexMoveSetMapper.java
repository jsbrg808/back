package ordinal.third.pogo.calcydex.mapper;

import ordinal.third.pogo.vo.CalcyDexMoveSetVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CalcyDexMoveSetMapper {
    List<Integer> selectTopRankedPokedexNos();

    void deleteAll();

    void insertBatch(@Param("list") List<CalcyDexMoveSetVO> list);
}
