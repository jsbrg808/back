package ordinal.third.pogo.table.mapper;

import ordinal.third.pogo.vo.Pogo5TableVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface Pogo5TableMapper {
    List<Pogo5TableVO> list(@Param("tableName") String tableName);
    List<Pogo5TableVO> listByNm(@Param("tableName") String tableName, @Param("vo") Pogo5TableVO vo);
    List<Pogo5TableVO> listByBasNm(@Param("tableName") String tableName, @Param("vo") Pogo5TableVO vo);
    List<Pogo5TableVO> listByAlsChar(
            @Param("tableName") String tableName,
            @Param("position") int position,
            @Param("value") String value);
    void append(@Param("tableName") String tableName, @Param("vo") Pogo5TableVO vo);
    void update(@Param("tableName") String tableName, @Param("vo") Pogo5TableVO vo);
    void delete(@Param("tableName") String tableName, @Param("vo") Pogo5TableVO vo);
    void updateDec(@Param("tableName") String tableName, @Param("vo") Pogo5TableVO vo);
    void updateInc(@Param("tableName") String tableName, @Param("vo") Pogo5TableVO vo);
    void insert(@Param("tableName") String tableName, @Param("vo") Pogo5TableVO vo);
}
