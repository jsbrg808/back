package ordinal.third.pogo.excel.service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import ordinal.third.pogo.excel.mapper.Pogo5pcMapper;
import ordinal.third.pogo.vo.PogoExcelPreview;
import ordinal.third.pogo.vo.Pogo5pcVO;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Pogo5pcExcelService {

    private static final float ROW_HEIGHT_POINTS = 17.1f;

    private final Pogo5pcMapper pogo5pcMapper;

    public void write(OutputStream out) throws IOException {
        try (XSSFWorkbook wb = buildWorkbook()) {
            wb.write(out);
        }
    }

    public PogoExcelPreview preview() throws IOException {
        try (XSSFWorkbook wb = buildWorkbook()) {
            return PogoExcelPreviewBuilder.build("포고 5pc", "/pogo/5pc/download", wb);
        }
    }

    private XSSFWorkbook buildWorkbook() throws IOException {
        List<Pogo5pcVO> list = pogo5pcMapper.list();
        XSSFWorkbook wb = new XSSFWorkbook();
        Pogo5pcStyles st = new Pogo5pcStyles(wb);
        SummaryStats summary = new SummaryStats();
        Sheet sheet = setupSheet(wb);
        int rowNum = fillDataRows(list, sheet, st, summary);
        for (int i = 0; i < 10; i++) {
            sheet.setColumnHidden(4 + i, summary.first.powCounts[i] == 0);
            sheet.setColumnHidden(17 + i, summary.second.powCounts[i] == 0);
            sheet.setColumnHidden(30 + i, summary.third.powCounts[i] == 0);
        }
        fillSummaryRows(list, sheet, st, summary, rowNum);
        return wb;
    }

    /* ===== 시트 초기화 ===== */

    private Sheet setupSheet(XSSFWorkbook wb) {
        Sheet sheet = wb.createSheet("Sheet1");
        sheet.setAutobreaks(true);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setPaperSize(PogoExcelConstants.PAPER_SIZE_A4);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight(PogoExcelConstants.NO_FIT_TO_PAGE);
        for (PageMargin margin : PageMargin.values()) {
            sheet.setMargin(margin, PogoExcelConstants.ZERO_MARGIN);
        }

        final double PX = PogoExcelConstants.COLUMN_WIDTH_PIXEL_FACTOR;
        int[] colPx = {
            36, 44, 20, 111,
            36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36,
            36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36,
            36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36
        };
        for (int i = 0; i < colPx.length; i++) {
            sheet.setColumnWidth(i, (int) (colPx[i] * PX));
        }

        sheet.setColumnHidden(2, true);
        return sheet;
    }

    /* ===== 데이터 행 ===== */

    private int fillDataRows(
        List<Pogo5pcVO> list, Sheet sheet, Pogo5pcStyles st, SummaryStats summary) {
        int rowNum = 0, seqNum = 0;
        for (Pogo5pcVO vo : list) {
            seqNum++;
            String div = vo.getDv() == null ? "" : vo.getDv();
            boolean allPowEmpty =
                effectivelyEmpty(vo.getPogo5fPow()) && effectivelyEmpty(vo.getPogo5sPow())
                    && effectivelyEmpty(vo.getPogo5tPow());

            Row row = createRow(sheet, rowNum);
            int ft = "M".equals(div) ? 2 : ("L".equals(div) || "U".equals(div)) ? 1 : 0;

            int[] fCatVals = parseCatVals(vo.getPogo5fCat(), 3);
            int[] sCatVals = parseCatVals(vo.getPogo5sCat(), 3);
            int[] tCatVals = parseCatVals(vo.getPogo5tCat(), 3);
            boolean isOrange = (sCatVals[0] < fCatVals[0] || tCatVals[0] < sCatVals[0])
                || (sCatVals[1] < fCatVals[1] || tCatVals[1] < sCatVals[1])
                || (sCatVals[2] < fCatVals[2] || tCatVals[2] < sCatVals[2]);
            boolean isRed = fCatVals[0] < 0 || fCatVals[1] < 0 || fCatVals[2] < 0
                || sCatVals[0] < 0 || sCatVals[1] < 0 || sCatVals[2] < 0
                || tCatVals[0] < 0 || tCatVals[1] < 0 || tCatVals[2] < 0;
            boolean isYellow = fCatVals[0] != sCatVals[0] || fCatVals[1] != sCatVals[1]
                || fCatVals[2] != sCatVals[2];
            boolean isGray = allPowEmpty
                && fCatVals[0] == sCatVals[0] && sCatVals[0] == tCatVals[0]
                && fCatVals[1] == sCatVals[1] && sCatVals[1] == tCatVals[1]
                && fCatVals[2] == sCatVals[2] && sCatVals[2] == tCatVals[2];

            int fPowCnt = countFilled(vo.getPogo5fPow());
            int sPowCnt = countFilled(vo.getPogo5sPow());
            int tPowCnt = countFilled(vo.getPogo5tPow());
            boolean isBlueCaseA = fPowCnt <= 1 && sPowCnt <= 1 && tPowCnt <= 1
                && effectivelyEmpty(vo.getPogo5fCat()) && effectivelyEmpty(vo.getPogo5sCat())
                && effectivelyEmpty(vo.getPogo5tCat());
            int maxCatVal = 0;
            int activePositions = 0;
            for (int i = 0; i < 3; i++) {
                maxCatVal = Math.max(maxCatVal,
                    Math.max(fCatVals[i], Math.max(sCatVals[i], tCatVals[i])));
                if (fCatVals[i] != 0 || sCatVals[i] != 0 || tCatVals[i] != 0) {
                    activePositions++;
                }
            }
            boolean isBlueCaseB = allPowEmpty && maxCatVal <= 1 && activePositions <= 1;
            boolean isBlue = isBlueCaseA || isBlueCaseB;

            for (int col = 0; col <= 42; col++) {
                CellStyle cs = isRed ? st.red[ft]
                    : isOrange ? st.orange[ft]
                        : isYellow ? st.yellow[ft]
                            : isGray ? st.gray[ft]
                                : isBlue ? st.blue[ft]
                                    : st.base[ft][(col >= 4 && col <= 13) ? 1
                                        : (col >= 17 && col <= 26) ? 2
                                            : (col >= 30 && col <= 39) ? 3 : 0];
                row.createCell(col).setCellStyle(cs);
            }

            row.getCell(0).setCellValue(seqNum);
            if (!empty(vo.getBaseNo())) {
                row.getCell(1).setCellValue(vo.getBaseNo());
            }
            if (!empty(div)) {
                row.getCell(2).setCellValue(div);
            }
            if (!empty(vo.getName())) {
                row.getCell(3).setCellValue(vo.getName());
            }

            fillPow(row, 4, vo.getPogo5fPow(), summary.first.powCounts);
            fillCat(row, 14, vo.getPogo5fCat(), summary.first.categorySums);
            fillPow(row, 17, vo.getPogo5sPow(), summary.second.powCounts);
            fillCat(row, 27, vo.getPogo5sCat(), summary.second.categorySums);
            fillPow(row, 30, vo.getPogo5tPow(), summary.third.powCounts);
            fillCat(row, 40, vo.getPogo5tCat(), summary.third.categorySums);

            countLvl(vo.getPogo5fPow(), div, summary.first.levelCounts);
            countLvl(vo.getPogo5sPow(), div, summary.second.levelCounts);
            countLvl(vo.getPogo5tPow(), div, summary.third.levelCounts);
            rowNum++;
        }
        return rowNum;
    }

    /* ===== 요약 행 ===== */

    private void fillSummaryRows(
        List<Pogo5pcVO> list, Sheet sheet, Pogo5pcStyles st, SummaryStats summary, int dc) {
        int grandTotal = list.isEmpty() ? 0 : list.get(0).getTotal();

        Row perLvlRow = createRow(sheet, dc);
        for (int col = 0; col <= 42; col++) {
            perLvlRow.createCell(col).setCellStyle(st.green);
        }
        for (int i = 0; i < 10; i++) {
            if (summary.first.powCounts[i] > 0) {
                perLvlRow.getCell(4 + i).setCellValue(summary.first.powCounts[i]);
            }
            if (summary.second.powCounts[i] > 0) {
                perLvlRow.getCell(17 + i).setCellValue(summary.second.powCounts[i]);
            }
            if (summary.third.powCounts[i] > 0) {
                perLvlRow.getCell(30 + i).setCellValue(summary.third.powCounts[i]);
            }
        }
        for (int i = 0; i < 3; i++) {
            if (summary.first.categorySums[i] > 0) {
                perLvlRow.getCell(14 + i).setCellValue(summary.first.categorySums[i]);
            }
            if (summary.second.categorySums[i] > 0) {
                perLvlRow.getCell(27 + i).setCellValue(summary.second.categorySums[i]);
            }
            if (summary.third.categorySums[i] > 0) {
                perLvlRow.getCell(40 + i).setCellValue(summary.third.categorySums[i]);
            }
        }

        Row totalsRow = createRow(sheet, dc + 1);
        for (int col = 0; col <= 42; col++) {
            totalsRow.createCell(col).setCellStyle(st.green);
        }
        int totalFirstPow = 0, totalSecondPow = 0, totalThirdPow = 0;
        for (int v : summary.first.powCounts) {
            totalFirstPow += v;
        }
        for (int v : summary.second.powCounts) {
            totalSecondPow += v;
        }
        for (int v : summary.third.powCounts) {
            totalThirdPow += v;
        }
        int totalFirstCat = summary.first.categoryTotal();
        int totalSecondCat = summary.second.categoryTotal();
        int totalThirdCat = summary.third.categoryTotal();
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        addMerge(sheet, totalsRow, dc + 1, 0, 3, today);
        addMerge(sheet, totalsRow, dc + 1, 4, 13, totalFirstPow);
        addMerge(sheet, totalsRow, dc + 1, 14, 16, grandTotal - totalFirstCat);
        addMerge(sheet, totalsRow, dc + 1, 17, 26, totalSecondPow);
        addMerge(sheet, totalsRow, dc + 1, 27, 29, grandTotal - totalSecondCat);
        addMerge(sheet, totalsRow, dc + 1, 30, 39, totalThirdPow);
        addMerge(sheet, totalsRow, dc + 1, 40, 42, grandTotal - totalThirdCat);

        List<Integer> firstColsList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            if (summary.first.powCounts[i] > 0) {
                firstColsList.add(4 + i);
            }
        }
        firstColsList.add(14);
        firstColsList.add(15);
        firstColsList.add(16);
        int[] firstCols = firstColsList.stream().mapToInt(Integer::intValue).toArray();

        List<Integer> secondColsList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            if (summary.second.powCounts[i] > 0) {
                secondColsList.add(17 + i);
            }
        }
        secondColsList.add(27);
        secondColsList.add(28);
        secondColsList.add(29);
        int[] secondCols = secondColsList.stream().mapToInt(Integer::intValue).toArray();

        List<Integer> thirdColsList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            if (summary.third.powCounts[i] > 0) {
                thirdColsList.add(30 + i);
            }
        }
        thirdColsList.add(40);
        thirdColsList.add(41);
        thirdColsList.add(42);
        int[] thirdCols = thirdColsList.stream().mapToInt(Integer::intValue).toArray();
        List<LevelCount> firstLvlCounts = buildLvlCounts(summary.first.levelCounts);
        List<LevelCount> secondLvlCounts = buildLvlCounts(summary.second.levelCounts);
        List<LevelCount> thirdLvlCounts = buildLvlCounts(summary.third.levelCounts);
        int summaryCountPairs = Math.max(
            rowsNeeded(firstLvlCounts, firstCols.length),
            Math.max(rowsNeeded(secondLvlCounts, secondCols.length),
                rowsNeeded(thirdLvlCounts, thirdCols.length)));

        for (int i = 0; i < summaryCountPairs; i++) {
            Row labelRow = createRow(sheet, dc + 2 + (i * 2));
            Row countRow = createRow(sheet, dc + 3 + (i * 2));
            for (int col = 0; col <= 42; col++) {
                int bg = i > 0 ? 0
                    : (col >= 4 && col <= 16) ? 1
                        : (col >= 17 && col <= 29) ? 2
                            : (col >= 30) ? 3 : 0;
                labelRow.createCell(col).setCellStyle(st.noBorder[bg]);
                countRow.createCell(col).setCellStyle(st.noBorder[bg]);
            }
            fillLvlCountRows(labelRow, countRow, firstCols, firstLvlCounts, i);
            fillLvlCountRows(labelRow, countRow, secondCols, secondLvlCounts, i);
            fillLvlCountRows(labelRow, countRow, thirdCols, thirdLvlCounts, i);
        }
    }

    /* ===== 헬퍼 ===== */

    private boolean empty(String s) {
        return s == null || s.isEmpty();
    }

    private Row createRow(Sheet sheet, int rowNum) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(ROW_HEIGHT_POINTS);
        return row;
    }

    private boolean effectivelyEmpty(String s) {
        if (s == null || s.isEmpty()) {
            return true;
        }
        for (String p : s.split("\t", -1)) {
            if (!p.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private int countFilled(String pow) {
        if (empty(pow)) {
            return 0;
        }
        int cnt = 0;
        for (String p : pow.split("\t", -1)) {
            if (!p.isEmpty()) {
                cnt++;
            }
        }
        return cnt;
    }

    private void countLvl(String pow, String div, Map<String, Integer> map) {
        if (effectivelyEmpty(pow)) {
            return;
        }
        boolean hasDiv = !empty(div);
        for (String part : pow.split("\t", -1)) {
            if (part.length() >= 2) {
                try {
                    int lvl = Integer.parseInt(part.substring(1));
                    String key = hasDiv ? "?" + lvl : String.valueOf(lvl);
                    map.merge(key, 1, Integer::sum);
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private List<LevelCount> buildLvlCounts(Map<String, Integer> map) {
        List<LevelCount> levelCounts = new ArrayList<>();

        map.entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .sorted(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getKey().startsWith("?") ? 0 : 1)
                .thenComparing(
                    Comparator.comparingInt((Map.Entry<String, Integer> e) -> parseLevel(e.getKey().startsWith("?") ? e.getKey().substring(1) : e.getKey())).reversed()
                )
            )
            .forEach(e -> {
                String key = e.getKey();
                int level = parseLevel(key.startsWith("?") ? key.substring(1) : key);
                levelCounts.add(new LevelCount(formatLevelLabel(key), level, e.getValue()));
            });

        return levelCounts;
    }

    private int parseLevel(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String formatLevelLabel(String value) {
        int prefixLength = value.startsWith("?") ? 1 : 0;
        String level = value.substring(prefixLength);
        return level.length() == 1 && Character.isDigit(level.charAt(0))
            ? value.substring(0, prefixLength) + "0" + level : value;
    }

    private String formatPowLabel(String value) {
        return value.length() == 2 && Character.isDigit(value.charAt(1))
            ? value.substring(0, 1) + "0" + value.substring(1) : value;
    }

    private int rowsNeeded(List<LevelCount> values, int colCount) {
        return values.isEmpty() ? 0 : ((values.size() + colCount - 1) / colCount);
    }

    private void fillLvlCountRows(Row labelRow, Row countRow, int[] cols, List<LevelCount> values,
        int rowOffset) {
        int start = rowOffset * cols.length;
        for (int i = 0; i < cols.length && start + i < values.size(); i++) {
            LevelCount value = values.get(start + i);
            labelRow.getCell(cols[i]).setCellValue(value.label());
            countRow.getCell(cols[i]).setCellValue(value.count());
        }
    }

    private void fillPow(Row row, int startCol, String value, int[] cnt) {
        if (empty(value)) {
            return;
        }
        String[] parts = value.split("\t", -1);
        for (int i = 0; i < parts.length && i < cnt.length; i++) {
            if (!parts[i].isEmpty()) {
                row.getCell(startCol + i).setCellValue(formatPowLabel(parts[i]));
                cnt[i]++;
            }
        }
    }

    private int[] parseCatVals(String value, int count) {
        int[] vals = new int[count];
        if (value == null || value.isEmpty()) {
            return vals;
        }
        String[] parts = value.split("\t", -1);
        for (int i = 0; i < parts.length && i < count; i++) {
            String p = parts[i].trim();
            if (!p.isEmpty()) {
                try {
                    vals[i] = Integer.parseInt(p);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return vals;
    }

    private void fillCat(Row row, int startCol, String value, int[] sum) {
        if (empty(value)) {
            return;
        }
        String[] parts = value.split("\t", -1);
        for (int i = 0; i < parts.length && i < sum.length; i++) {
            String p = parts[i].trim();
            if (!p.isEmpty()) {
                try {
                    int num = Integer.parseInt(p);
                    row.getCell(startCol + i).setCellValue(num);
                    sum[i] += num;
                } catch (NumberFormatException e) {
                    row.getCell(startCol + i).setCellValue(p);
                }
            }
        }
    }

    private void addMerge(Sheet sheet, Row row, int rowIdx, int c1, int c2, int value) {
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, c1, c2));
        row.getCell(c1).setCellValue(value);
    }

    private void addMerge(Sheet sheet, Row row, int rowIdx, int c1, int c2, String value) {
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, c1, c2));
        row.getCell(c1).setCellValue(value);
    }

    /* ===== 스타일 컨테이너 ===== */

    private static class SummaryStats {

        private final GroupStats first = new GroupStats();
        private final GroupStats second = new GroupStats();
        private final GroupStats third = new GroupStats();
    }

    private static class GroupStats {

        private final int[] powCounts = new int[10];
        private final int[] categorySums = new int[3];
        private final Map<String, Integer> levelCounts = new HashMap<>();

        private int categoryTotal() {
            return categorySums[0] + categorySums[1] + categorySums[2];
        }
    }

    private record LevelCount(String label, int level, int count) {
    }
}
