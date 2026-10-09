package ordinal.third.pogo.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PogoExcelPreview {

    private final String title;
    private final String downloadPath;
    private final List<Integer> columnWidths;
    private final int tableWidth;
    private final List<PreviewRow> rows;

    @Getter
    @AllArgsConstructor
    public static class PreviewRow {

        private final List<PreviewCell> cells;
    }

    @Getter
    @AllArgsConstructor
    public static class PreviewCell {

        private final String value;
        private final String style;
        private final int colSpan;
        private final int rowSpan;
        private final boolean editable;
        private final String savePath;
        private final String no;
        private final String dv;
        private final String field;
    }
}
