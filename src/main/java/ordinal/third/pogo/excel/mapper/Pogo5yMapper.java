package ordinal.third.pogo.excel.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ordinal.third.pogo.vo.Pogo5yVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface Pogo5yMapper {
    List<Pogo5yVO> list();

    void upsertPokedex4Cell(
        @Param("no") Integer no,
        @Param("dv") String dv,
        @Param("field") String field,
        @Param("value") String value,
        @Param("ivValue") Integer ivValue);
}
