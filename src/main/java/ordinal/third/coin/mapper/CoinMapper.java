package ordinal.third.coin.mapper;

import ordinal.third.coin.vo.CoinVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CoinMapper {
    List<CoinVO> list(CoinVO vo);
}
