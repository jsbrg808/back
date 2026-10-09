package ordinal.third.coin.mapper;

import ordinal.third.coin.vo.CodeVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CodeMapper {
    List<CodeVO> list();
}
