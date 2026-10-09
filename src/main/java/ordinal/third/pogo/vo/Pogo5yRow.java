package ordinal.third.pogo.vo;

import java.util.List;

public record Pogo5yRow(
        Integer rankNo,
        String no,
        String dv,
        String name,
        List<MoveSet> moveSets,
        List<OwnedSlot> ownedSlots) {

    public static Pogo5yRow from(Pogo5yVO source) {
        return new Pogo5yRow(
                source.getRankNo(),
                source.getNo(),
                source.getDv(),
                source.getName(),
                List.of(
                        new MoveSet(source.getNrmlMove1(), source.getNrmlDv1(), source.getSpclMove1(), source.getSpclDv1()),
                        new MoveSet(source.getNrmlMove2(), source.getNrmlDv2(), source.getSpclMove2(), source.getSpclDv2()),
                        new MoveSet(source.getNrmlMove3(), source.getNrmlDv3(), source.getSpclMove3(), source.getSpclDv3()),
                        new MoveSet(source.getNrmlMove4(), source.getNrmlDv4(), source.getSpclMove4(), source.getSpclDv4()),
                        new MoveSet(source.getNrmlMove5(), source.getNrmlDv5(), source.getSpclMove5(), source.getSpclDv5()),
                        new MoveSet(source.getNrmlMove6(), source.getNrmlDv6(), source.getSpclMove6(), source.getSpclDv6())),
                List.of(
                        new OwnedSlot(Slot.FM, source.getFmIv(), source.getFmGndrLvl(), source.getFmNrmlMove(), source.getFmSpclMove()),
                        new OwnedSlot(Slot.FS, source.getFsIv(), source.getFsGndrLvl(), source.getFsNrmlMove(), source.getFsSpclMove()),
                        new OwnedSlot(Slot.SM, source.getSmIv(), source.getSmGndrLvl(), source.getSmNrmlMove(), source.getSmSpclMove()),
                        new OwnedSlot(Slot.SS, source.getSsIv(), source.getSsGndrLvl(), source.getSsNrmlMove(), source.getSsSpclMove()),
                        new OwnedSlot(Slot.TM, source.getTmIv(), source.getTmGndrLvl(), source.getTmNrmlMove(), source.getTmSpclMove()),
                        new OwnedSlot(Slot.TS, source.getTsIv(), source.getTsGndrLvl(), source.getTsNrmlMove(), source.getTsSpclMove())));
    }

    public boolean matchesMoveSet(String normalMove, String specialMove) {
        return moveSets.stream().anyMatch(moveSet ->
                same(normalMove, moveSet.normalMove())
                        && same(specialMove, moveSet.specialMove()));
    }

    private boolean same(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }

    public record MoveSet(
            String normalMove,
            String normalDivision,
            String specialMove,
            String specialDivision) {
    }

    public record OwnedSlot(
            Slot slot,
            String iv,
            String genderLevel,
            String normalMove,
            String specialMove) {
    }

    public enum Slot {
        FM(9),
        FS(11),
        SM(13),
        SS(15),
        TM(17),
        TS(19);

        private final int startColumn;

        Slot(int startColumn) {
            this.startColumn = startColumn;
        }

        public int startColumn() {
            return startColumn;
        }

        public static Slot fromColumn(int column) {
            for (Slot slot : values()) {
                if (column == slot.startColumn || column == slot.startColumn + 1) {
                    return slot;
                }
            }
            throw new IllegalArgumentException("편집할 수 없는 열입니다: " + column);
        }
    }
}
