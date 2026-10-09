package ordinal.third.pogo.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import ordinal.third.pogo.excel.service.Pogo5pcExcelService;
import ordinal.third.pogo.excel.service.Pogo5yExcelService;
import ordinal.third.pogo.vo.Pogo5yCellUpdate;
import ordinal.third.pogo.vo.PogoExcelPreview;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PogoExcelController {

    private static final String XLSX_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final Pogo5pcExcelService pogo5pcExcelService;
    private final Pogo5yExcelService pogo5yExcelService;

    @GetMapping("/api/pogo/5pc/preview")
    public PogoExcelPreview preview5pc() throws Exception {
        return pogo5pcExcelService.preview();
    }

    @GetMapping("/api/pogo/5pc/download")
    public void download5pc(HttpServletResponse response) throws Exception {
        prepareDownload(response, "포고 5pc.xlsx");
        pogo5pcExcelService.write(response.getOutputStream());
    }

    @GetMapping("/api/pogo/5y/preview")
    public PogoExcelPreview preview5y() throws Exception {
        return pogo5yExcelService.preview();
    }

    @PostMapping("/api/pogo/5y/cell")
    public ResponseEntity<Map<String, Object>> save5yCell(@RequestBody Pogo5yCellUpdate update) {
        try {
            pogo5yExcelService.saveCell(update);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/api/pogo/5y/download")
    public void download5y(HttpServletResponse response) throws Exception {
        prepareDownload(response, "포고 5y.xlsx");
        pogo5yExcelService.write(response.getOutputStream());
    }

    private void prepareDownload(HttpServletResponse response, String filename) {
        response.setContentType(XLSX_CONTENT_TYPE);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
            + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
    }
}
