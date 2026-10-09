package ordinal.third.pogo.vo;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PokedexStepResult {

    private final boolean ok;
    private final String status;
    private final String message;
    private final List<String> details;
    private final Map<String, Integer> counts;

    private PokedexStepResult(
            boolean ok,
            String status,
            String message,
            List<String> details,
            Map<String, Integer> counts) {
        this.ok = ok;
        this.status = status;
        this.message = message;
        this.details = List.copyOf(details);
        this.counts = Map.copyOf(counts);
    }

    public static PokedexStepResult success(String status, List<String> details) {
        return success(status, details, Map.of());
    }

    public static PokedexStepResult success(
            String status,
            List<String> details,
            String countName,
            int count) {
        return success(status, details, Map.of(countName, count));
    }

    public static PokedexStepResult success(
            String status,
            List<String> details,
            Map<String, Integer> counts) {
        return new PokedexStepResult(true, status, null, details, new LinkedHashMap<>(counts));
    }

    public static PokedexStepResult failure(String message, List<String> details) {
        return new PokedexStepResult(false, null, message, details, Map.of());
    }

    public boolean isOk() {
        return ok;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getDetails() {
        return details;
    }

    @JsonAnyGetter
    public Map<String, Integer> getCounts() {
        return counts;
    }
}
