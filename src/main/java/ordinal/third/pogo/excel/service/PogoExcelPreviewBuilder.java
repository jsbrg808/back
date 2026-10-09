package ordinal.third.pogo.excel.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ordinal.third.pogo.vo.PogoExcelPreview;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class PogoExcelPreviewBuilder {

    private PogoExcelPreviewBuilder() {
    }

    static PogoExcelPreview build(String title, String downloadPath, XSSFWorkbook wb) {
        return build(title, downloadPath, wb, CellEditorResolver.NONE_RESOLVER);
    }

    static PogoExcelPreview build(
        String title, String downloadPath, XSSFWorkbook wb, CellEditorResolver cellEditorResolver) {
        Sheet sheet = wb.getSheetAt(0);
        List<Integer> visibleColumns = visibleColumns(sheet);
        List<Integer> columnWidths = columnWidths(sheet, visibleColumns);
        List<PogoExcelPreview.PreviewRow> rows = rows(
            wb, sheet, visibleColumns, new MergeIndex(sheet, visibleColumns), cellEditorResolver);
        int tableWidth = columnWidths.stream().mapToInt(Integer::intValue).sum();
        return new PogoExcelPreview(title, downloadPath, columnWidths, tableWidth, rows);
    }

    private static List<Integer> visibleColumns(Sheet sheet) {
        int lastColumn = 0;
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null && row.getLastCellNum() > lastColumn) {
                lastColumn = row.getLastCellNum();
            }
        }

        List<Integer> columns = new ArrayList<>();
        for (int i = 0; i < lastColumn; i++) {
            if (!sheet.isColumnHidden(i)) {
                columns.add(i);
            }
        }
        return columns;
    }

    private static List<Integer> columnWidths(Sheet sheet, List<Integer> visibleColumns) {
        List<Integer> widths = new ArrayList<>();
        for (Integer col : visibleColumns) {
            int width = Math.round(sheet.getColumnWidthInPixels(col));
            widths.add(width);
        }
        return widths;
    }

    private static List<PogoExcelPreview.PreviewRow> rows(
        XSSFWorkbook wb, Sheet sheet, List<Integer> visibleColumns, MergeIndex mergeIndex,
        CellEditorResolver cellEditorResolver) {
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        Map<CellStyle, String> styleCache = new IdentityHashMap<>();
        List<PogoExcelPreview.PreviewRow> rows = new ArrayList<>();
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row sheetRow = sheet.getRow(i);
            List<PogoExcelPreview.PreviewCell> cells = new ArrayList<>();
            for (Integer col : visibleColumns) {
                long key = key(i, col);
                if (mergeIndex.isCovered(key)) {
                    continue;
                }

                MergeSpan mergeSpan = mergeIndex.start(key);
                Cell cell = displayCell(sheet, sheetRow, mergeSpan, col);
                String value = cell == null ? "" : formatter.formatCellValue(cell);
                String style = cell == null ? "" : styleCache.computeIfAbsent(cell.getCellStyle(), unused -> style(wb, cell));
                int colSpan = mergeSpan == null ? 1 : mergeSpan.colSpan();
                int rowSpan = mergeSpan == null ? 1 : mergeSpan.rowSpan();
                CellEditor editor = cellEditorResolver.resolve(sheet, i, col);
                cells.add(new PogoExcelPreview.PreviewCell(
                    value, style, colSpan, rowSpan, editor.editable(), editor.savePath(),
                    editor.no(), editor.dv(), editor.field()));
            }
            rows.add(new PogoExcelPreview.PreviewRow(cells));
        }
        return rows;
    }

    private static Cell displayCell(Sheet sheet, Row row, MergeSpan mergeSpan, int col) {
        if (mergeSpan == null) {
            return row == null ? null : row.getCell(col);
        }
        Row firstRow = sheet.getRow(mergeSpan.firstRow());
        if (firstRow == null) {
            return null;
        }
        Cell cell = firstRow.getCell(mergeSpan.firstColumn());
        return cell == null ? firstRow.getCell(col) : cell;
    }

    private static long key(int row, int col) {
        return (((long) row) << 32) | (col & 0xffffffffL);
    }

    private static int firstVisibleColumn(CellRangeAddress region, List<Integer> visibleColumns) {
        for (Integer col : visibleColumns) {
            if (col >= region.getFirstColumn() && col <= region.getLastColumn()) {
                return col;
            }
        }
        return region.getFirstColumn();
    }

    private static int visibleColumnCount(CellRangeAddress region, List<Integer> visibleColumns) {
        int count = 0;
        for (Integer col : visibleColumns) {
            if (col >= region.getFirstColumn() && col <= region.getLastColumn()) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private static class MergeIndex {

        private final Map<Long, MergeSpan> starts = new HashMap<>();
        private final Set<Long> covered = new HashSet<>();

        MergeIndex(Sheet sheet, List<Integer> visibleColumns) {
            for (CellRangeAddress region : sheet.getMergedRegions()) {
                int firstVisibleColumn = firstVisibleColumn(region, visibleColumns);
                int colSpan = visibleColumnCount(region, visibleColumns);
                MergeSpan span = new MergeSpan(
                    region.getFirstRow(),
                    region.getFirstColumn(),
                    colSpan,
                    region.getLastRow() - region.getFirstRow() + 1);
                long startKey = key(region.getFirstRow(), firstVisibleColumn);
                starts.put(startKey, span);

                for (int row = region.getFirstRow(); row <= region.getLastRow(); row++) {
                    for (Integer col : visibleColumns) {
                        if (col < region.getFirstColumn() || col > region.getLastColumn()) {
                            continue;
                        }
                        long cellKey = key(row, col);
                        if (cellKey != startKey) {
                            covered.add(cellKey);
                        }
                    }
                }
            }
        }

        MergeSpan start(long key) {
            return starts.get(key);
        }

        boolean isCovered(long key) {
            return covered.contains(key);
        }
    }

    private record MergeSpan(int firstRow, int firstColumn, int colSpan, int rowSpan) {
    }

    interface CellEditorResolver {

        CellEditor NONE = new CellEditor(false, null, null, null, null);
        CellEditorResolver NONE_RESOLVER = (sheet, row, col) -> NONE;

        CellEditor resolve(Sheet sheet, int row, int col);
    }

    record CellEditor(boolean editable, String savePath, String no, String dv, String field) {
    }

    private static String style(XSSFWorkbook wb, Cell cell) {
        if (cell == null) {
            return "";
        }
        CellStyle style = cell.getCellStyle();
        StringBuilder css = new StringBuilder();
        if (style instanceof XSSFCellStyle xssfStyle
            && xssfStyle.getFillPattern() == FillPatternType.SOLID_FOREGROUND) {
            String bg = hex(xssfStyle.getFillForegroundColorColor());
            if (bg != null) {
                css.append("background-color:").append(bg).append(';');
            }
        }

        XSSFFont font = wb.getFontAt(style.getFontIndex());
        String color = hex(font.getXSSFColor());
        if (color != null) {
            css.append("color:").append(color).append(';');
        }
        if (font.getStrikeout()) {
            css.append("text-decoration:line-through;");
        }
        return css.toString();
    }

    private static String hex(XSSFColor color) {
        if (color == null || color.getRGB() == null) {
            return null;
        }
        byte[] rgb = color.getRGB();
        return String.format("#%02X%02X%02X", rgb[0] & 0xff, rgb[1] & 0xff, rgb[2] & 0xff);
    }
}
