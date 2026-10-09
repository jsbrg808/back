package ordinal.third.pogo.excel.mapper;

import ordinal.third.pogo.vo.Pogo5pcVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface Pogo5pcMapper {
    List<Pogo5pcVO> list();
}
