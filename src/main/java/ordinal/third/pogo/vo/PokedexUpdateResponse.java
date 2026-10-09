package ordinal.third.pogo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PokedexUpdateResponse {

    private boolean ok;
    private String message;
    private String status;
    private PokedexStepResult step1;
    private PokedexStepResult step1_5;
    private PokedexStepResult step1_6;
    private PokedexStepResult step2;
    private PokedexStepResult step2_5;
    private PokedexStepResult step3;
    private PokedexStepResult step3_1;
    private PokedexStepResult moveSets;

    public static PokedexUpdateResponse failure(String message) {
        PokedexUpdateResponse response = new PokedexUpdateResponse();
        response.fail(message);
        return response;
    }

    public void put(PokedexUpdateStep step, PokedexStepResult result) {
        switch (step) {
            case STEP1 -> step1 = result;
            case STEP1_5 -> step1_5 = result;
            case STEP1_6 -> step1_6 = result;
            case STEP2 -> step2 = result;
            case STEP2_5 -> step2_5 = result;
            case STEP3 -> step3 = result;
            case STEP3_1 -> step3_1 = result;
            case MOVE_SETS -> moveSets = result;
        }
    }

    public void succeed() {
        ok = true;
        message = null;
    }

    public void fail(String message) {
        ok = false;
        this.message = message;
    }

    public boolean isOk() {
        return ok;
    }

    public String getMessage() {
        return message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public PokedexStepResult getStep1() {
        return step1;
    }

    @JsonProperty("step1_5")
    public PokedexStepResult getStep1_5() {
        return step1_5;
    }

    @JsonProperty("step1_6")
    public PokedexStepResult getStep1_6() {
        return step1_6;
    }

    public PokedexStepResult getStep2() {
        return step2;
    }

    @JsonProperty("step2_5")
    public PokedexStepResult getStep2_5() {
        return step2_5;
    }

    public PokedexStepResult getStep3() {
        return step3;
    }

    @JsonProperty("step3_1")
    public PokedexStepResult getStep3_1() {
        return step3_1;
    }

    public PokedexStepResult getMoveSets() {
        return moveSets;
    }
}
