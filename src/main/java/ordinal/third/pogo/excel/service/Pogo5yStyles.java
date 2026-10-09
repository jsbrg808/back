package ordinal.third.pogo.excel.service;

import java.util.HashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class Pogo5yStyles {

    private final XSSFWorkbook workbook;
    private final Map<String, CellStyle> cache = new HashMap<>();

    Pogo5yStyles(XSSFWorkbook workbook) {
        this.workbook = workbook;
    }

    CellStyle get(XSSFColor fontColor, XSSFColor background, boolean strikeout) {
        return get(fontColor, background, strikeout, false);
    }

    CellStyle green() {
        return get(null, PogoExcelConstants.LIGHT_GREEN, false);
    }

    CellStyle noBorder(XSSFColor background) {
        String key = "noBorder:" + colorKey(background);
        return cache.computeIfAbsent(key, unused -> createNoBorderStyle(background));
    }

    CellStyle get(
        XSSFColor fontColor, XSSFColor background, boolean strikeout, boolean smallFont) {
        String key = colorKey(fontColor) + ":" + colorKey(background)
            + ":" + strikeout + ":" + smallFont;
        return cache.computeIfAbsent(
            key, unused -> createStyle(fontColor, background, strikeout, smallFont));
    }

    private CellStyle createStyle(
        XSSFColor fontColor, XSSFColor background, boolean strikeout, boolean smallFont) {
        XSSFFont font = font(fontColor, smallFont);
        font.setStrikeout(strikeout);
        XSSFCellStyle style = baseStyle(font, background);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createNoBorderStyle(XSSFColor background) {
        return baseStyle(font(null, false), background);
    }

    private XSSFFont font(XSSFColor color, boolean small) {
        XSSFFont font = workbook.createFont();
        font.setFontName(PogoExcelConstants.FONT_NAME);
        font.setFontHeightInPoints(
            small ? PogoExcelConstants.SMALL_FONT_SIZE : PogoExcelConstants.FONT_SIZE);
        if (color != null) {
            font.setColor(color);
        }
        return font;
    }

    private XSSFCellStyle baseStyle(XSSFFont font, XSSFColor background) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        if (background != null) {
            style.setFillForegroundColor(background);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return style;
    }

    private static String colorKey(XSSFColor color) {
        return color == null ? "none" : color.getARGBHex();
    }
}
