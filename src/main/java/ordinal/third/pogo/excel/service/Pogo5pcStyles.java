package ordinal.third.pogo.excel.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class Pogo5pcStyles {

    final CellStyle[][] base;
    final CellStyle[] orange;
    final CellStyle[] red;
    final CellStyle[] yellow;
    final CellStyle[] blue;
    final CellStyle[] gray;
    final CellStyle[] noBorder;
    final CellStyle green;

    Pogo5pcStyles(XSSFWorkbook workbook) {
        XSSFFont[] fonts = {
            font(workbook, null),
            font(workbook, PogoExcelConstants.BLUE),
            font(workbook, PogoExcelConstants.RED)
        };
        XSSFColor[] backgrounds = {
            null,
            PogoExcelConstants.SLIGHT_BLUE,
            PogoExcelConstants.SLIGHT_RED,
            PogoExcelConstants.SLIGHT_YELLOW
        };

        base = new CellStyle[fonts.length][backgrounds.length];
        for (int font = 0; font < fonts.length; font++) {
            for (int background = 0; background < backgrounds.length; background++) {
                base[font][background] = bordered(workbook, fonts[font], backgrounds[background]);
            }
        }

        red = colored(workbook, fonts, PogoExcelConstants.LIGHT_RED);
        orange = colored(workbook, fonts, PogoExcelConstants.LIGHT_ORANGE);
        yellow = colored(workbook, fonts, PogoExcelConstants.LIGHT_YELLOW);
        gray = colored(workbook, fonts, PogoExcelConstants.LIGHT_GRAY);
        blue = colored(workbook, fonts, PogoExcelConstants.LIGHT_BLUE);
        noBorder = new CellStyle[backgrounds.length];
        for (int background = 0; background < backgrounds.length; background++) {
            noBorder[background] = baseStyle(workbook, fonts[0], backgrounds[background]);
        }
        green = bordered(workbook, font(workbook, null), PogoExcelConstants.LIGHT_GREEN);
    }

    private static XSSFFont font(XSSFWorkbook workbook, XSSFColor color) {
        XSSFFont font = workbook.createFont();
        font.setFontName(PogoExcelConstants.FONT_NAME);
        font.setFontHeightInPoints(PogoExcelConstants.FONT_SIZE);
        if (color != null) {
            font.setColor(color);
        }
        return font;
    }

    private static XSSFCellStyle bordered(
        XSSFWorkbook workbook, XSSFFont font, XSSFColor background) {
        XSSFCellStyle style = baseStyle(workbook, font, background);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static XSSFCellStyle baseStyle(
        XSSFWorkbook workbook, XSSFFont font, XSSFColor background) {
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

    private static CellStyle[] colored(
        XSSFWorkbook workbook, XSSFFont[] fonts, XSSFColor background) {
        CellStyle[] styles = new CellStyle[fonts.length];
        for (int i = 0; i < fonts.length; i++) {
            styles[i] = bordered(workbook, fonts[i], background);
        }
        return styles;
    }
}
