package ordinal.third.pogo.controller;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import ordinal.third.pogo.table.service.PogoService;
import ordinal.third.pogo.vo.Pogo5TableVO;
import ordinal.third.pogo.vo.Pogo5deVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PogoTableController {

    private final PogoService pogoService;

    @GetMapping("/api/pogo/{tbl:5[fst]}")
    public Map<String, Object> list(@PathVariable String tbl, Pogo5TableVO vo) {
        List<Pogo5TableVO> pogo5TableList = pogoService.selectPogo5TableList(tbl, vo);
        return Map.of(
            "pogo5TableList", pogo5TableList,
            "tbl", tbl,
            "srchNm", vo.getSrchNm() == null ? "" : vo.getSrchNm());
    }

    @PostMapping("/api/pogo/{tbl:5[fst]}/append")
    public Map<String, Object> append(@PathVariable String tbl, Pogo5TableVO vo) {
        pogoService.appendPogo5Table(tbl, vo);
        return Map.of("ok", true);
    }

    @PostMapping("/api/pogo/{tbl:5[fst]}/update")
    public Map<String, Object> update(@PathVariable String tbl, Pogo5TableVO vo) {
        pogoService.updatePogo5Table(tbl, vo);
        return Map.of("ok", true, "srlNo", vo.getSrlNo());
    }

    @PostMapping("/api/pogo/{tbl:5[fst]}/delete")
    public Map<String, Object> delete(@PathVariable String tbl, Pogo5TableVO vo) {
        pogoService.deletePogo5Table(tbl, vo);
        return Map.of("ok", true, "srlNo", vo.getSrlNo());
    }

    @PostMapping("/api/pogo/{tbl:5[fst]}/insert")
    public Map<String, Object> insert(@PathVariable String tbl, Pogo5TableVO vo) {
        pogoService.insertPogo5Table(tbl, vo);
        return Map.of("ok", true, "srlNo", vo.getSrlNo());
    }

    @GetMapping("/api/pogo/5de")
    public List<Pogo5deVO> evolutionList() {
        return pogoService.selectPogo5deList();
    }
}
