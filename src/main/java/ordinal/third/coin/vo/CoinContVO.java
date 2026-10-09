package ordinal.third.coin.vo;

import lombok.Data;

import java.util.List;

@Data
public class CoinContVO {
    private String contNo;
    private String contName;
    private List<CoinCtryVO> ctrys;

    public int getCtryCount() {
        return ctrys.size();
    }

    public int getCoinCount() {
        return ctrys.stream().mapToInt(CoinCtryVO::getCoinCount).sum();
    }

    public int getYearCount() {
        return ctrys.stream().mapToInt(CoinCtryVO::getYearCount).sum();
    }
}
