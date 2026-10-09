package ordinal.third.pogo.table.mapper;

import ordinal.third.pogo.vo.Pogo5deVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface Pogo5deMapper {
    List<Pogo5deVO> list();
}
