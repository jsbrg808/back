package ordinal.third.pogo.table.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import ordinal.third.pogo.table.mapper.Pogo5TableMapper;
import ordinal.third.pogo.table.mapper.Pogo5deMapper;
import ordinal.third.pogo.vo.Pogo5TableVO;
import ordinal.third.pogo.vo.Pogo5deVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PogoService {

    private final Pogo5TableMapper pogo5TableMapper;
    private final Pogo5deMapper pogo5deMapper;

    private String tableName(String tbl) {
        return switch (tbl) {
            case "5f" -> "POGO_5F";
            case "5s" -> "POGO_5S";
            case "5t" -> "POGO_5T";
            default -> throw new IllegalArgumentException("Unknown table: " + tbl);
        };
    }

    // als 포맷: X<gender(1)><lvl(2)><name>[ <iv>]
    private void parsePogo5TableVO(Pogo5TableVO vo) {
        String d = vo.getAls();
        if (d == null || d.length() < 4) {
            return;
        }
        vo.setGender(d.substring(1, 2));
        vo.setLvl(d.substring(2, 4));
        int spaceIdx = d.indexOf(' ');
        if (spaceIdx < 0) {
            vo.setName(d.substring(4));
            vo.setIv("100");
        } else {
            vo.setName(d.substring(4, spaceIdx));
            vo.setIv(d.substring(spaceIdx + 1));
        }
    }

    private List<Pogo5TableVO> resolveList(String tblName, Pogo5TableVO vo) {
        String srch = vo.getSrchNm();
        if (srch == null || srch.isBlank()) {
            return pogo5TableMapper.list(tblName);
        }
        switch (srch) {
            case "y":
                return pogo5TableMapper.listByAlsChar(tblName, 1, "y");
            case "n":
                return pogo5TableMapper.listByAlsChar(tblName, 1, "n");
            case "수컷":
                return pogo5TableMapper.listByAlsChar(tblName, 2, "m");
            case "암컷":
                return pogo5TableMapper.listByAlsChar(tblName, 2, "f");
            case "성별불명":
                return pogo5TableMapper.listByAlsChar(tblName, 2, "g");
            default:
                break;
        }
        if (srch.startsWith("+")) {
            vo.setSrchNm(srch.substring(1));
            return pogo5TableMapper.listByBasNm(tblName, vo);
        }
        return pogo5TableMapper.listByNm(tblName, vo);
    }

    public List<Pogo5TableVO> selectPogo5TableList(String tbl, Pogo5TableVO vo) {
        return resolveList(tableName(tbl), vo);
    }

    public void appendPogo5Table(String tbl, Pogo5TableVO vo) {
        parsePogo5TableVO(vo);
        pogo5TableMapper.append(tableName(tbl), vo);
    }

    public void updatePogo5Table(String tbl, Pogo5TableVO vo) {
        parsePogo5TableVO(vo);
        pogo5TableMapper.update(tableName(tbl), vo);
    }

    @Transactional
    public void deletePogo5Table(String tbl, Pogo5TableVO vo) {
        String resolvedTableName = tableName(tbl);
        pogo5TableMapper.delete(resolvedTableName, vo);
        pogo5TableMapper.updateDec(resolvedTableName, vo);
    }

    @Transactional
    public void insertPogo5Table(String tbl, Pogo5TableVO vo) {
        parsePogo5TableVO(vo);
        String resolvedTableName = tableName(tbl);
        pogo5TableMapper.updateInc(resolvedTableName, vo);
        pogo5TableMapper.insert(resolvedTableName, vo);
    }

    public List<Pogo5deVO> selectPogo5deList() {
        return pogo5deMapper.list();
    }

}
