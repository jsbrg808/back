package ordinal.third.coin.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CoinVO {
    private String contNo;
    private String ctryNo;
    private String coinNo;
    private String coinYears;
    private String coinYear2s;
    private String contName;
    private String ctryName;
    private String coinName;
    private String coinEtc;
    private String ctryCnt;

    public List<String[]> getCoinYearPairs() {
        String[] years = coinYears != null ? coinYears.split("\\|") : new String[0];
        String[] year2s = coinYear2s != null ? coinYear2s.split("\\|") : new String[0];
        List<String[]> pairs = new ArrayList<>();
        for (int i = 0; i < years.length; i++) {
            String y2 = (i < year2s.length) ? year2s[i] : "";
            pairs.add(new String[]{years[i], y2});
        }
        return pairs;
    }

    public int getYearRowCount() {
        List<String[]> pairs = getCoinYearPairs();
        return Math.max(1, (pairs.size() + 3) / 4);
    }

    public List<List<String[]>> getCoinYearRows() {
        List<String[]> pairs = getCoinYearPairs();
        List<List<String[]>> rows = new ArrayList<>();
        if (pairs.isEmpty()) {
            rows.add(new ArrayList<>());
            return rows;
        }
        for (int i = 0; i < pairs.size(); i += 4)
            rows.add(new ArrayList<>(pairs.subList(i, Math.min(i + 4, pairs.size()))));
        return rows;
    }
}
