package ordinal.third.pogo.vo;

public enum PokedexUpdateStep {
    STEP1(true),
    STEP1_5(true),
    STEP1_6(true),
    STEP2(true),
    STEP2_5(true),
    STEP3(true),
    STEP3_1(true),
    MOVE_SETS(false);

    private final boolean defaultSelected;

    PokedexUpdateStep(boolean defaultSelected) {
        this.defaultSelected = defaultSelected;
    }

    public boolean defaultSelected() {
        return defaultSelected;
    }
}
