package ordinal.third.pogo.vo;

import lombok.Data;

@Data
public class PokedexUpdateRequest {

    private Boolean step1;
    private Boolean step1_5;
    private Boolean step1_6;
    private Boolean step2;
    private Boolean step2_5;
    private Boolean step3;
    private Boolean step3_1;
    private Boolean moveSets;

    public boolean isUnspecified() {
        return step1 == null && step1_5 == null && step1_6 == null
                && step2 == null && step2_5 == null && step3 == null
                && step3_1 == null && moveSets == null;
    }

    public boolean selects(PokedexUpdateStep step) {
        if (isUnspecified()) {
            return step.defaultSelected();
        }
        return Boolean.TRUE.equals(valueOf(step));
    }

    private Boolean valueOf(PokedexUpdateStep step) {
        return switch (step) {
            case STEP1 -> step1;
            case STEP1_5 -> step1_5;
            case STEP1_6 -> step1_6;
            case STEP2 -> step2;
            case STEP2_5 -> step2_5;
            case STEP3 -> step3;
            case STEP3_1 -> step3_1;
            case MOVE_SETS -> moveSets;
        };
    }
}
