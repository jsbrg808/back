package ordinal.third.pogo.excel.service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import ordinal.third.pogo.excel.mapper.Pogo5yMapper;
import ordinal.third.pogo.vo.Pogo5yCellUpdate;
import ordinal.third.pogo.vo.PogoExcelPreview;
import ordinal.third.pogo.vo.Pogo5yRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Pogo5yExcelService {

    private static final int LAST_COL = 20;
    private static final float ROW_HEIGHT_POINTS = 16.4f;
    private static final Set<String> EDITABLE_DVS = Set.of("FM", "FS", "SM", "SS", "TM", "TS");
    private static final Set<String> EDITABLE_FIELDS = Set.of("iv", "gndr_lvl", "nrml_move", "spcl_move");

    private final Pogo5yMapper pogo5yMapper;

    public void write(OutputStream out) throws IOException {
        try (XSSFWorkbook wb = buildWorkbook()) {
            wb.write(out);
        }
    }

    public PogoExcelPreview preview() throws IOException {
        try (XSSFWorkbook wb = buildWorkbook()) {
            return PogoExcelPreviewBuilder.build(
                "포고 5y", "/pogo/5y/download", wb, this::resolveEditor);
        }
    }

    public void saveCell(Pogo5yCellUpdate update) {
        if (update == null || update.getNo() == null) {
            throw new IllegalArgumentException("no is required");
        }
        String dv = cleanSaveValue(update.getDv());
        String field = cleanSaveValue(update.getField());
        String value = cleanSaveValue(update.getValue());
        if (!EDITABLE_DVS.contains(dv)) {
            throw new IllegalArgumentException("invalid dv: " + dv);
        }
        if (!EDITABLE_FIELDS.contains(field)) {
            throw new IllegalArgumentException("invalid field: " + field);
        }

        Integer ivValue = null;
        if ("iv".equals(field) && value != null) {
            try {
                ivValue = Integer.valueOf(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("iv must be numeric");
            }
        }
        if ("gndr_lvl".equals(field) && value != null && value.length() > 3) {
            throw new IllegalArgumentException("gndr_lvl is too long");
        }
        if (("nrml_move".equals(field) || "spcl_move".equals(field)) && value != null && value.length() > 100) {
            throw new IllegalArgumentException("move is too long");
        }
        pogo5yMapper.upsertPokedex4Cell(update.getNo(), dv, field, value, ivValue);
    }

    private PogoExcelPreviewBuilder.CellEditor resolveEditor(Sheet sheet, int row, int col) {
        if (col < 9 || col > 20) {
            return PogoExcelPreviewBuilder.CellEditorResolver.NONE;
        }
        int baseRow = row % 2 == 0 ? row : row - 1;
        String no = cellText(sheet, baseRow, 1);
        if (no == null) {
            return PogoExcelPreviewBuilder.CellEditorResolver.NONE;
        }

        String dv = Pogo5yRow.Slot.fromColumn(col).name();
        String field = row % 2 == 0
            ? (col % 2 == 1 ? "iv" : "nrml_move")
            : (col % 2 == 1 ? "gndr_lvl" : "spcl_move");
        return new PogoExcelPreviewBuilder.CellEditor(true, "/pogo/5y/cell", no, dv, field);
    }

    private String cellText(Sheet sheet, int rowNum, int colNum) {
        Row row = sheet.getRow(rowNum);
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(colNum);
        if (cell == null) {
            return null;
        }
        String value = new DataFormatter().formatCellValue(cell);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String cleanSaveValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private XSSFWorkbook buildWorkbook() throws IOException {
        List<Pogo5yRow> list = pogo5yMapper.list().stream()
            .map(Pogo5yRow::from)
            .toList();
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = setupSheet(wb);
        Pogo5yStyles styles = new Pogo5yStyles(wb);
        SummaryStats summary = new SummaryStats();
        int rowNum = fillDataRows(list, sheet, styles, summary);
        fillSummaryRows(sheet, styles, rowNum, summary);
        return wb;
    }

    private Sheet setupSheet(XSSFWorkbook wb) {
        Sheet sheet = wb.createSheet("Sheet1");
        sheet.setAutobreaks(true);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setPaperSize(PogoExcelConstants.PAPER_SIZE_A4);
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        for (PageMargin margin : PageMargin.values()) {
            sheet.setMargin(margin, PogoExcelConstants.ZERO_MARGIN);
        }

        final double PX = PogoExcelConstants.COLUMN_WIDTH_PIXEL_FACTOR;
        int[] colPx = {
            36, 44, 111, 72, 72, 72, 72, 72, 72,
            36, 93, 36, 93, 36, 93, 36, 93, 36, 93, 36, 93
        };
        for (int i = 0; i < colPx.length; i++) {
            sheet.setColumnWidth(i, (int) (colPx[i] * PX));
        }
        return sheet;
    }

    private int fillDataRows(
        List<Pogo5yRow> list, Sheet sheet, Pogo5yStyles styles, SummaryStats summary) {
        int rowNum = 0;
        for (Pogo5yRow row : list) {
            Row topRow = createFixedHeightRow(sheet, rowNum);
            Row bottomRow = createFixedHeightRow(sheet, rowNum + 1);

            boolean gray = row.rankNo() != null && row.rankNo() % 2 == 0;
            XSSFColor fontColor = fontColor(row.dv());
            for (int col = 0; col <= LAST_COL; col++) {
                topRow.createCell(col).setCellStyle(styles.get(fontColor, baseBg(col, gray), false));
                bottomRow.createCell(col).setCellStyle(styles.get(fontColor, baseBg(col, gray), false));
            }

            sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum + 1, 0, 0));
            setCell(topRow, 0, row.rankNo());
            setCell(topRow, 1, row.no());
            setCell(bottomRow, 1, row.dv());
            setCell(topRow, 2, row.name());

            for (int i = 0; i < row.moveSets().size(); i++) {
                int col = 3 + i;
                Pogo5yRow.MoveSet moveSet = row.moveSets().get(i);
                setMoveCell(topRow, col, moveSet.normalMove(),
                    styles.get(fontColor, baseBg(col, gray), isLost(moveSet.normalDivision()), true));
                setMoveCell(bottomRow, col, moveSet.specialMove(),
                    styles.get(fontColor, baseBg(col, gray), isLost(moveSet.specialDivision()), true));
            }
            for (Pogo5yRow.OwnedSlot ownedSlot : row.ownedSlots()) {
                fillPokedex4(
                    topRow, bottomRow, styles, summary, fontColor, gray, row, ownedSlot);
            }
            rowNum += 2;
        }
        return rowNum;
    }

    private void fillPokedex4(Row topRow, Row bottomRow, Pogo5yStyles styles, SummaryStats summary,
        XSSFColor fontColor, boolean gray, Pogo5yRow row, Pogo5yRow.OwnedSlot ownedSlot) {
        int startCol = ownedSlot.slot().startColumn();
        String iv = ownedSlot.iv();
        String gndrLvl = ownedSlot.genderLevel();
        String nrmlMove = ownedSlot.normalMove();
        String spclMove = ownedSlot.specialMove();
        XSSFColor ivBg = invalidIvLvl(iv, gndrLvl, ownedSlot.slot()) ? PogoExcelConstants.LIGHT_ORANGE
            : baseBg(startCol, gray);
        XSSFColor moveBg = moveBg(row, startCol, gray, iv, nrmlMove, spclMove);
        summary.countContent(startCol, iv);
        summary.countContent(startCol, gndrLvl);
        summary.countContent(startCol + 1, nrmlMove);
        summary.countContent(startCol + 1, spclMove);
        if (ivBg == PogoExcelConstants.LIGHT_ORANGE) {
            summary.addOrangePair(startCol);
        }
        if (moveBg == PogoExcelConstants.LIGHT_ORANGE) {
            summary.addOrangePair(startCol + 1);
        } else if (moveBg == PogoExcelConstants.LIGHT_YELLOW) {
            summary.addYellowPair(startCol + 1);
        }
        topRow.getCell(startCol).setCellStyle(styles.get(fontColor, ivBg, false));
        bottomRow.getCell(startCol).setCellStyle(styles.get(fontColor, ivBg, false));
        topRow.getCell(startCol + 1).setCellStyle(styles.get(fontColor, moveBg, false));
        bottomRow.getCell(startCol + 1).setCellStyle(styles.get(fontColor, moveBg, false));
        setCell(topRow, startCol, iv);
        setCell(bottomRow, startCol, gndrLvl);
        setCell(topRow, startCol + 1, nrmlMove);
        setCell(bottomRow, startCol + 1, spclMove);
    }

    private void fillSummaryRows(
        Sheet sheet, Pogo5yStyles styles, int rowNum, SummaryStats summary) {
        Row totalTopRow = createFixedHeightRow(sheet, rowNum);
        Row totalBottomRow = createFixedHeightRow(sheet, rowNum + 1);
        Row orangeTopRow = createFixedHeightRow(sheet, rowNum + 2);
        Row orangeBottomRow = createFixedHeightRow(sheet, rowNum + 3);

        for (int col = 0; col <= LAST_COL; col++) {
            totalTopRow.createCell(col).setCellStyle(styles.green());
            totalBottomRow.createCell(col).setCellStyle(styles.green());
            orangeTopRow.createCell(col).setCellStyle(styles.noBorder(summaryBg(col)));
            orangeBottomRow.createCell(col).setCellStyle(styles.noBorder(summaryBg(col)));
        }

        int[] contentSummary = new int[LAST_COL + 1];
        int[] issueSummary = new int[LAST_COL + 1];
        for (int col = 9; col <= LAST_COL; col++) {
            contentSummary[col] = summary.contentCounts[col] / 2;
            issueSummary[col] = issueCount(summary, col) / 2;
            setPositive(totalTopRow, col, contentSummary[col]);
            setPositive(orangeTopRow, col, issueSummary[col]);
        }

        sheet.addMergedRegion(new CellRangeAddress(rowNum + 1, rowNum + 1, 0, 2));
        totalBottomRow.getCell(0).setCellValue(
            new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()));

        fillPairSums(sheet, totalBottomRow, rowNum + 1, contentSummary);
        fillPairSums(sheet, orangeBottomRow, rowNum + 3, issueSummary);
    }

    private Row createFixedHeightRow(Sheet sheet, int rowNum) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(ROW_HEIGHT_POINTS);
        return row;
    }

    private int issueCount(SummaryStats summary, int col) {
        return col % 2 == 1 ? summary.orangeCounts[col] : summary.yellowCounts[col];
    }

    private void fillPairSums(Sheet sheet, Row row, int rowIdx, int[] values) {
        addPairSum(sheet, row, rowIdx, 9, values[9] + values[11]);
        addPairSum(sheet, row, rowIdx, 11, values[10] + values[12]);
        addPairSum(sheet, row, rowIdx, 13, values[13] + values[15]);
        addPairSum(sheet, row, rowIdx, 15, values[14] + values[16]);
        addPairSum(sheet, row, rowIdx, 17, values[17] + values[19]);
        addPairSum(sheet, row, rowIdx, 19, values[18] + values[20]);
    }

    private void addPairSum(Sheet sheet, Row row, int rowIdx, int startCol, int value) {
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, startCol, startCol + 1));
        setPositive(row, startCol, value);
    }

    private void setPositive(Row row, int col, int value) {
        if (value > 0) {
            row.getCell(col).setCellValue(value);
        }
    }

    private void setMoveCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.getCell(col);
        cell.setCellStyle(style);
        setCell(row, col, value);
    }

    private void setCell(Row row, int col, Integer value) {
        if (value != null) {
            row.getCell(col).setCellValue(value);
        }
    }

    private void setCell(Row row, int col, String value) {
        if (value != null && !value.isEmpty()) {
            row.getCell(col).setCellValue(value);
        }
    }

    private XSSFColor fontColor(String dv) {
        if ("L".equals(dv) || "U".equals(dv)) {
            return PogoExcelConstants.BLUE;
        }
        if ("M".equals(dv)) {
            return PogoExcelConstants.RED;
        }
        return null;
    }

    private boolean isLost(String dv) {
        return "L".equals(dv);
    }

    private XSSFColor baseBg(int col, boolean gray) {
        if (gray) {
            return PogoExcelConstants.LIGHT_GRAY;
        }
        if (col >= 9 && col <= 12) {
            return PogoExcelConstants.SLIGHT_BLUE;
        }
        if (col >= 13 && col <= 16) {
            return PogoExcelConstants.SLIGHT_RED;
        }
        if (col >= 17 && col <= 20) {
            return PogoExcelConstants.SLIGHT_YELLOW;
        }
        return null;
    }

    private XSSFColor summaryBg(int col) {
        if (col >= 9 && col <= 12) {
            return PogoExcelConstants.SLIGHT_BLUE;
        }
        if (col >= 13 && col <= 16) {
            return PogoExcelConstants.SLIGHT_RED;
        }
        if (col >= 17 && col <= 20) {
            return PogoExcelConstants.SLIGHT_YELLOW;
        }
        return null;
    }

    private XSSFColor moveBg(Pogo5yRow row, int startCol, boolean gray, String iv, String nrmlMove,
        String spclMove) {
        if (hasNumericValue(iv) && emptyPair(nrmlMove, spclMove)) {
            return PogoExcelConstants.LIGHT_ORANGE;
        }
        if (!emptyPair(nrmlMove, spclMove) && !row.matchesMoveSet(nrmlMove, spclMove)) {
            return PogoExcelConstants.LIGHT_YELLOW;
        }
        return baseBg(startCol + 1, gray);
    }

    private boolean invalidIvLvl(String iv, String gndrLvl, Pogo5yRow.Slot slot) {
        Integer expectedLvl = expectedLvl(iv, slot);
        if (expectedLvl == null) {
            return false;
        }
        if (gndrLvl == null || gndrLvl.length() < 2) {
            return true;
        }
        return !String.format("%02d", expectedLvl).equals(gndrLvl.substring(gndrLvl.length() - 2));
    }

    private Integer expectedLvl(String iv, Pogo5yRow.Slot slot) {
        if ("100".equals(iv)) {
            return switch (slot) {
                case FM -> 40;
                case SM -> 37;
                case FS, SS, TM, TS -> 35;
            };
        }
        if ("98".equals(iv) || "96".equals(iv)) {
            return 35;
        }
        if ("93".equals(iv) || "91".equals(iv)) {
            return 30;
        }
        if ("89".equals(iv) || "87".equals(iv)) {
            return 25;
        }
        if ("84".equals(iv) || "82".equals(iv)) {
            return 20;
        }
        return null;
    }

    private boolean hasNumericValue(String value) {
        if (empty(value)) {
            return false;
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean emptyPair(String nrmlMove, String spclMove) {
        return empty(nrmlMove) && empty(spclMove);
    }

    private boolean empty(String value) {
        return value == null || value.isEmpty();
    }

    private static class SummaryStats {

        private final int[] contentCounts = new int[LAST_COL + 1];
        private final int[] orangeCounts = new int[LAST_COL + 1];
        private final int[] yellowCounts = new int[LAST_COL + 1];

        void countContent(int col, String value) {
            if (col >= 9 && col <= LAST_COL && value != null && !value.isEmpty()) {
                contentCounts[col]++;
            }
        }

        void addOrangePair(int col) {
            if (col >= 9 && col <= LAST_COL) {
                orangeCounts[col] += 2;
            }
        }

        void addYellowPair(int col) {
            if (col >= 9 && col <= LAST_COL) {
                yellowCounts[col] += 2;
            }
        }
    }

}
