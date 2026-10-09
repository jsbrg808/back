package ordinal.third.pogo.vo;

import java.util.List;
import lombok.Data;

@Data
public class Pogo5deVO {
    private String baseNo;
    private String dv;
    private String name;
    private String pogo5fDup;
    private String pogo5fEvo;
    private String pogo5sDup;
    private String pogo5sEvo;
    private String pogo5tDup;
    private String pogo5tEvo;

    public List<String> getPogo5fEvoByGender() {
        return splitEvo(pogo5fEvo);
    }

    public List<String> getPogo5sEvoByGender() {
        return splitEvo(pogo5sEvo);
    }

    public List<String> getPogo5tEvoByGender() {
        return splitEvo(pogo5tEvo);
    }

    private List<String> splitEvo(String evo) {
        String[] parts = evo == null ? new String[0] : evo.split(",", -1);
        return List.of(
            parts.length > 0 ? parts[0] : "",
            parts.length > 1 ? parts[1] : "",
            parts.length > 2 ? parts[2] : ""
        );
    }
}
