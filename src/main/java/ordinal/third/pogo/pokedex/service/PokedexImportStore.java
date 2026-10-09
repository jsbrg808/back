package ordinal.third.pogo.pokedex.service;

import lombok.RequiredArgsConstructor;
import ordinal.third.pogo.calcydex.mapper.CalcyDexMoveSetMapper;
import ordinal.third.pogo.pokedex.mapper.PokedexJsonMapper;
import ordinal.third.pogo.pokedex.mapper.PokemonSpeciesCsvMapper;
import ordinal.third.pogo.vo.CalcyDexMoveSetVO;
import ordinal.third.pogo.vo.PokedexJsonVO;
import ordinal.third.pogo.vo.PokemonSpeciesCsvVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
public class PokedexImportStore {

    private static final int BATCH_SIZE = 500;

    private final PokedexJsonMapper pokedexJsonMapper;
    private final PokemonSpeciesCsvMapper pokemonSpeciesCsvMapper;
    private final CalcyDexMoveSetMapper calcyDexMoveSetMapper;

    @Transactional
    public int replacePokedex(
            List<PokedexJsonVO> pokemon,
            BiConsumer<Integer, Integer> progress) {
        pokedexJsonMapper.deleteAll();
        return insertBatches(pokemon, pokedexJsonMapper::insertBatch, progress);
    }

    @Transactional
    public int replaceSpecies(
            List<PokemonSpeciesCsvVO> species,
            BiConsumer<Integer, Integer> progress) {
        pokemonSpeciesCsvMapper.deleteAll();
        return insertBatches(species, pokemonSpeciesCsvMapper::insertBatch, progress);
    }

    @Transactional
    public int replaceMoveSets(List<CalcyDexMoveSetVO> moveSets) {
        calcyDexMoveSetMapper.deleteAll();
        return insertBatches(moveSets, calcyDexMoveSetMapper::insertBatch, (current, total) -> { });
    }

    private <T> int insertBatches(
            List<T> rows,
            java.util.function.Consumer<List<T>> insert,
            BiConsumer<Integer, Integer> progress) {
        int inserted = 0;
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            List<T> batch = rows.subList(i, Math.min(i + BATCH_SIZE, rows.size()));
            insert.accept(batch);
            inserted += batch.size();
            progress.accept(inserted, rows.size());
        }
        return inserted;
    }
}
