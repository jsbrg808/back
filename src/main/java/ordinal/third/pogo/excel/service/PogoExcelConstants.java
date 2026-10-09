package ordinal.third.pogo.excel.service;

import org.apache.poi.xssf.usermodel.XSSFColor;

final class PogoExcelConstants {

    static final String FONT_NAME = "굴림체";
    static final short FONT_SIZE = 11;
    static final short SMALL_FONT_SIZE = 9;

    static final short PAPER_SIZE_A4 = 9;
//    static final short PRINT_SCALE = 77;
    static final short NO_FIT_TO_PAGE = 0;
    static final double ZERO_MARGIN = 0.0;
    static final double COLUMN_WIDTH_PIXEL_FACTOR = 32.0;

    static final XSSFColor BLUE = rgb(0x0000FF);
    static final XSSFColor RED = rgb(0xFF0000);
    static final XSSFColor SLIGHT_BLUE = rgb(0xCCECFF);
    static final XSSFColor SLIGHT_RED = rgb(0xFFCCCC);
    static final XSSFColor SLIGHT_YELLOW = rgb(0xFFFFCC);
    static final XSSFColor LIGHT_RED = rgb(0xFF0000);
    static final XSSFColor LIGHT_ORANGE = rgb(0xFFA500);
    static final XSSFColor LIGHT_YELLOW = rgb(0xFFFF00);
    static final XSSFColor LIGHT_GRAY = rgb(0xD3D3D3);
    static final XSSFColor LIGHT_BLUE = rgb(0xADD8E6);
    static final XSSFColor LIGHT_GREEN = rgb(0x90EE90);

    private PogoExcelConstants() {
    }

    private static XSSFColor rgb(int color) {
        return new XSSFColor(new byte[]{
            (byte) ((color >> 16) & 0xFF),
            (byte) ((color >> 8) & 0xFF),
            (byte) (color & 0xFF)
        }, null);
    }
}
