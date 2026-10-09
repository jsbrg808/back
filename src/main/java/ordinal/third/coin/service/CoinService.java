package ordinal.third.coin.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import ordinal.third.coin.mapper.CodeMapper;
import ordinal.third.coin.mapper.CoinMapper;
import ordinal.third.coin.vo.CodeVO;
import ordinal.third.coin.vo.CoinContVO;
import ordinal.third.coin.vo.CoinCtryVO;
import ordinal.third.coin.vo.CoinVO;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CoinService {

    private final CoinMapper coinMapper;
    private final CodeMapper codeMapper;

    public List<CoinContVO> selectCoinList(CoinVO vo) {
        List<CodeVO> contCodes = codeMapper.list();
        List<CoinVO> allCoins = coinMapper.list(vo);

        List<CoinContVO> conts = new ArrayList<>();
        for (CodeVO cont : contCodes) {
            Map<String, CoinCtryVO> ctryMap = new LinkedHashMap<>();
            List<CoinCtryVO> ctrys = new ArrayList<>();

            for (CoinVO coin : allCoins) {
                if (!cont.getCodeNo().equals(coin.getContNo())) {
                    continue;
                }
                ctryMap.computeIfAbsent(coin.getCtryNo(), k -> {
                    CoinCtryVO c = new CoinCtryVO();
                    c.setCtryNo(k);
                    c.setCtryName(coin.getCtryName());
                    c.setCoins(new ArrayList<>());
                    ctrys.add(c);
                    return c;
                }).getCoins().add(coin);
            }

            CoinContVO coinCont = new CoinContVO();
            coinCont.setContNo(cont.getCodeNo());
            coinCont.setContName(cont.getCodeName());
            coinCont.setCtrys(ctrys);
            conts.add(coinCont);
        }
        return conts;
    }
}
