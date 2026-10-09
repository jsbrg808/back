package ordinal.third.pogo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalcyDexMoveSetVO {
    private Integer no;
    private Integer moveSetNo;
    private String nrmlMove;
    private String nrmlDv;
    private String spclMove;
    private String spclDv;
    private BigDecimal dps;
    private BigDecimal percent;
}
