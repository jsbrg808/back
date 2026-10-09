package ordinal.third.pogo.pokedex.service;

import ordinal.third.pogo.vo.PokedexStepResult;
import ordinal.third.pogo.vo.PokedexUpdateStep;

import java.util.List;

/**
 * 도감 갱신 단계의 실패 정보를 보존하면서 트랜잭션 rollback을 유도하는 예외입니다.
 */
public class PokedexUpdateException extends RuntimeException {

    private final PokedexUpdateStep step;
    private final List<String> details;

    public PokedexUpdateException(
            PokedexUpdateStep step,
            List<String> details,
            Throwable cause) {
        super("오류 발생: " + cause.getMessage(), cause);
        this.step = step;
        this.details = List.copyOf(details);
    }

    public PokedexUpdateStep getStep() {
        return step;
    }

    public List<String> getDetails() {
        return details;
    }

    public PokedexStepResult toStepResult() {
        return PokedexStepResult.failure(getMessage(), details);
    }
}
