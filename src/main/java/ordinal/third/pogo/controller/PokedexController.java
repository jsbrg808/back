package ordinal.third.pogo.controller;

import lombok.RequiredArgsConstructor;
import ordinal.third.pogo.pokedex.service.PokedexService;
import ordinal.third.pogo.pokedex.service.PokedexUpdateException;
import ordinal.third.pogo.vo.PokedexStepResult;
import ordinal.third.pogo.vo.PokedexUpdateRequest;
import ordinal.third.pogo.vo.PokedexUpdateResponse;
import ordinal.third.pogo.vo.PokedexUpdateStep;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PokedexController {

    private final PokedexService pokedexService;

    @PostMapping("/api/pogo/dex/update")
    public ResponseEntity<PokedexUpdateResponse> update(
        @ModelAttribute PokedexUpdateRequest request) {
        PokedexUpdateResponse response = new PokedexUpdateResponse();
        try {
            for (PokedexUpdateStep step : PokedexUpdateStep.values()) {
                if (!request.selects(step)) {
                    continue;
                }
                PokedexStepResult result = run(step);
                response.put(step, result);
                if (!result.isOk()) {
                    response.fail(result.getMessage());
                    return ResponseEntity.status(500).body(response);
                }
            }

            if (request.isUnspecified() && response.getStep1() != null) {
                response.setStatus(response.getStep1().getStatus());
            }

            response.succeed();
            return ResponseEntity.ok(response);
        } catch (PokedexUpdateException e) {
            response.put(e.getStep(), e.toStepResult());
            response.fail(e.getMessage());
            return ResponseEntity.status(500).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                PokedexUpdateResponse.failure("오류 발생: " + e.getMessage()));
        }
    }

    private PokedexStepResult run(PokedexUpdateStep step) throws Exception {
        return switch (step) {
            case STEP1 -> pokedexService.downloadPokedexJson();
            case STEP1_5 -> pokedexService.processPokedexStep1_5();
            case STEP1_6 -> pokedexService.processPokedexStep1_6();
            case STEP2 -> pokedexService.processPokedexStep2();
            case STEP2_5 -> pokedexService.processPokedexStep2_5();
            case STEP3 -> pokedexService.importPokedexStep3();
            case STEP3_1 -> pokedexService.processPokedexStep3_1();
            case MOVE_SETS -> pokedexService.processCalcyDexMoveSets();
        };
    }
}
