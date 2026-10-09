package ordinal.third.coin.controller;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import ordinal.third.coin.service.CoinService;
import ordinal.third.coin.vo.CoinContVO;
import ordinal.third.coin.vo.CoinVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CoinController {

    private final CoinService coinService;

    @GetMapping("/api/coin")
    public Map<String, Object> coinList(CoinVO coinVO) {
        List<CoinContVO> coinConts = coinService.selectCoinList(coinVO);
        int totalCtrys = coinConts.stream().mapToInt(CoinContVO::getCtryCount).sum();
        int totalCoins = coinConts.stream().mapToInt(CoinContVO::getCoinCount).sum();
        int totalYears = coinConts.stream().mapToInt(CoinContVO::getYearCount).sum();
        return Map.of(
            "coinConts", coinConts,
            "totalCtrys", totalCtrys,
            "totalCoins", totalCoins,
            "totalYears", totalYears);
    }
}
