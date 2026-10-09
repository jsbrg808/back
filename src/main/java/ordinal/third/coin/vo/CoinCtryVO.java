package ordinal.third.coin.vo;

import lombok.Data;

import java.util.List;

@Data
public class CoinCtryVO {
    private String ctryNo;
    private String ctryName;
    private List<CoinVO> coins;

    public int getCoinCount() {
        return (int) coins.stream()
                .filter(c -> c.getCoinNo() != null && !c.getCoinNo().isEmpty())
                .count();
    }

    public int getYearCount() {
        return coins.stream()
                .filter(c -> c.getCoinYears() != null)
                .mapToInt(c -> c.getCoinYearPairs().size())
                .sum();
    }

    public int getTotalYearRowCount() {
        return coins.stream().mapToInt(CoinVO::getYearRowCount).sum();
    }
}
