package ordinal.third.pogo.pokedex.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import ordinal.third.pogo.calcydex.service.CalcyDexMoveSetCalculator;
import ordinal.third.pogo.calcydex.service.CalcyDexPokemonClient;
import ordinal.third.pogo.calcydex.service.CalcyDexSourceClient;
import ordinal.third.pogo.calcydex.mapper.CalcyDexMoveSetMapper;
import ordinal.third.pogo.pokedex.mapper.PokedexJsonMapper;
import ordinal.third.pogo.pokedex.mapper.PokemonSpeciesCsvMapper;
import ordinal.third.pogo.vo.CalcyDexMoveSetVO;
import ordinal.third.pogo.vo.PokedexJsonVO;
import ordinal.third.pogo.vo.PokedexStepResult;
import ordinal.third.pogo.vo.PokedexUpdateStep;
import ordinal.third.pogo.vo.PokemonSpeciesCsvVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class PokedexService {

    private final PokedexJsonMapper pokedexJsonMapper;
    private final PokemonSpeciesCsvMapper pokemonSpeciesCsvMapper;
    private final CalcyDexMoveSetMapper calcyDexMoveSetMapper;
    private final CalcyDexMoveSetCalculator calcyDexMoveSetCalculator;
    private final CalcyDexPokemonClient calcyDexPokemonClient;
    private final CalcyDexSourceClient calcyDexSourceClient;
    private final PokedexJsonParser pokedexJsonParser;
    private final PokemonSpeciesCsvParser pokemonSpeciesCsvParser;
    private final PokedexDataProcessor pokedexDataProcessor;
    private final PokedexSourceClient pokedexSourceClient;
    private final PokedexImportStore pokedexImportStore;
    private final DataSource dataSource;
    private final ReentrantLock lock = new ReentrantLock();

    private static final String FORM_NUMBER_SCRIPT_PATH =
        "sql/runtime/스텝1.6.sql";

    public PokedexStepResult downloadPokedexJson() {
        lock.lock();
        List<String> details = new ArrayList<>();
        try {
            PokedexSourceClient.SourceFile source = pokedexSourceClient.refreshPokedex(details);
            boolean downloadTriggered = source.updated();

            // 1. pokedex.json 파싱 및 pokedex_json 적재 시작
            details.add("pokedex.json 파싱 및 pokedex_json 테이블 적재를 시작합니다...");
            var file = source.file();
            if (!file.exists()) {
                details.add("오류: 로컬 pokedex.json 파일이 존재하지 않습니다.");
                return PokedexStepResult.failure(
                        "로컬 pokedex.json 파일이 존재하지 않습니다.", details);
            }

            PokedexJsonParser.ParseResult parsed = pokedexJsonParser.parse(file.toPath());
            details.add("JSON 파싱 및 메모리 로드 완료. 포켓몬 변환 시작...");
            List<PokedexJsonVO> listToInsert = parsed.pokemon();
            details.add(String.format("포켓몬 기본형(base) %d건 파싱 완료", parsed.baseCount()));
            details.add(String.format("지역 폼(regionForms) %d건 파싱 완료", parsed.formCount()));
            details.add(String.format("암컷 이미지(image_g2) 매핑 완료: %d건", parsed.femaleImageCount()));
            // pokedex_json 테이블의 기존 데이터를 다 지워줘.
            details.add("pokedex_json 테이블의 기존 데이터를 비우는 중...");
            int totalInserted = pokedexImportStore.replacePokedex(
                    listToInsert,
                    (inserted, total) -> details.add(
                            String.format("데이터 삽입 진행 중... (%d/%d건)", inserted, total)));
            details.add("pokedex_json 기존 데이터 교체 완료");

            details.add(String.format("최종 DB 적재 완료: 총 %d건", totalInserted));
            return PokedexStepResult.success(
                    downloadTriggered ? "UPDATED" : "LATEST",
                    details,
                    "insertedCount",
                    totalInserted);
        } catch (Exception e) {
            details.add("예외 발생: " + e.getMessage());
            throw new PokedexUpdateException(PokedexUpdateStep.STEP1, details, e);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public PokedexStepResult processPokedexStep1_5() throws Exception {
        lock.lock();
        List<String> details = new ArrayList<>();
        try {
            details.add("pokedex_json 테이블에서 전체 포켓몬 정보 조회 중...");
            List<PokedexJsonVO> list = pokedexJsonMapper.selectAll();
            if (list == null || list.isEmpty()) {
                details.add("오류: 데이터가 존재하지 않습니다.");
                return PokedexStepResult.failure(
                        "데이터가 존재하지 않습니다. 1단계를 먼저 실행해 주세요.", details);
            }
            details.add(String.format("조회 완료: 총 %d건", list.size()));

            pokedexDataProcessor.calculateMaxCp(list);
            details.add("지가르데(Zygarde) 스탯 보정 처리 완료 (10% 폼 스탯 복사)");
            details.add("최대 CP 계산 완료");

            // 4. Update DB
            details.add("데이터베이스 업데이트(pokedex_json) 진행 중...");
            for (PokedexJsonVO vo : list) {
                pokedexJsonMapper.updateEvolutionAndCp(vo);
            }
            details.add("데이터베이스 업데이트 완료");

            return PokedexStepResult.success(
                    "SUCCESS", details, "updatedCount", list.size());
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public PokedexStepResult importPokedexStep3() throws Exception {
        lock.lock();
        List<String> details = new ArrayList<>();
        try {
            details.add("pokedex_json 데이터 전체 조회 중...");
            List<PokedexJsonVO> list = pokedexJsonMapper.selectAll();
            if (list == null || list.isEmpty()) {
                details.add("오류: pokedex_json 데이터가 존재하지 않습니다.");
                return PokedexStepResult.failure(
                        "pokedex_json 데이터가 존재하지 않습니다. 1단계를 먼저 실행해 주세요.",
                        details);
            }
            details.add(String.format("조회 성공: 총 %d건", list.size()));

            List<PokedexJsonVO> baseList = pokedexDataProcessor.rankableBasePokemon(list);
            details.add(String.format("기본형(dexFormNr = 1) 필터링 완료: %d건", baseList.size()));

            // Sort: maxCp DESC, dexNr DESC
            details.add("최대 CP 내림차순 및 도감 번호 내림차순 기준 정렬 시작...");
            baseList.sort((a, b) -> {
                int maxCpA = a.getMaxCp();
                int maxCpB = b.getMaxCp();
                if (maxCpA != maxCpB) {
                    return Integer.compare(maxCpB, maxCpA);
                }
                int dexNrA = a.getDexNr();
                int dexNrB = b.getDexNr();
                return Integer.compare(dexNrB, dexNrA);
            });
            details.add("정렬 완료 및 랭킹 부여 시작");

            // Upsert each record into pokedex table
            details.add("pokedex 테이블 Upsert(동기화) 진행 중...");
            int upsertedCount = 0;
            for (int i = 0; i < baseList.size(); i++) {
                PokedexJsonVO vo = baseList.get(i);
                Integer no = vo.getDexNr();
                String name = vo.getKorean();
                String dv = pokedexDataProcessor.pokemonClassCode(vo.getPokemonClass());
                Integer rankNo = i + 1;

                pokedexJsonMapper.upsertPokedex(no, name, dv, rankNo);
                upsertedCount++;
                
                if (upsertedCount % 100 == 0 || upsertedCount == baseList.size()) {
                    details.add(String.format("Upsert 완료 (%d/%d건)...", upsertedCount, baseList.size()));
                }
            }

            return PokedexStepResult.success(
                    "SUCCESS", details, "upsertedCount", upsertedCount);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public PokedexStepResult processPokedexStep3_1() throws Exception {
        lock.lock();
        List<String> details = new ArrayList<>();
        try {
            details.add("pokemon_species_csv 테이블에서 데이터 조회 중...");
            List<PokemonSpeciesCsvVO> speciesList = pokemonSpeciesCsvMapper.selectAll();
            if (speciesList == null || speciesList.isEmpty()) {
                details.add("오류: pokemon_species_csv 테이블에 데이터가 없습니다. 스텝2를 먼저 진행해 주세요.");
                return PokedexStepResult.failure(
                        "데이터가 존재하지 않습니다. 스텝2를 먼저 실행해 주세요.", details);
            }
            details.add(String.format("데이터 조회 성공: %d건", speciesList.size()));

            // Update database pokedex table for each species entry
            details.add("pokedex 테이블에 base_no, evl, 성비(gndr_rt) 컬럼 업데이트 진행 중...");
            int updatedCount = 0;
            for (PokemonSpeciesCsvVO vo : speciesList) {
                if (vo.getId() == null) {
                    throw new IllegalStateException("pokemon_species_csv 테이블의 데이터에 ID가 누락되었습니다.");
                }
                if (vo.getBaseNo() == null) {
                    throw new IllegalStateException("포켓몬 ID " + vo.getId() + "의 base_no 데이터가 누락되었습니다. 스텝2.5를 먼저 실행해 주세요.");
                }
                if (vo.getEvl() == null) {
                    throw new IllegalStateException("포켓몬 ID " + vo.getId() + "의 evl 데이터가 누락되었습니다. 스텝2.5를 먼저 실행해 주세요.");
                }
                if (vo.getGenderRate() == null) {
                    throw new IllegalStateException("포켓몬 ID " + vo.getId() + "의 gender_rate 데이터가 누락되었습니다.");
                }
                String matchedCode = pokedexDataProcessor.genderRateCode(vo.getGenderRate());
                
                pokedexJsonMapper.updatePokedexStep3_1(vo.getId(), vo.getBaseNo(), vo.getEvl(), matchedCode);
                updatedCount++;
            }
            details.add(String.format("베이스 정보 및 성비 업데이트 완료: 총 %d건", updatedCount));

            return PokedexStepResult.success(
                    "SUCCESS", details, "updatedCount", updatedCount);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public PokedexStepResult processPokedexStep1_6() {
        lock.lock();
        List<String> details = new ArrayList<>();
        try {
            details.add("스텝1.6.sql 실행 시작...");

            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
            populator.addScript(new ClassPathResource(FORM_NUMBER_SCRIPT_PATH));
            DatabasePopulatorUtils.execute(populator, dataSource);

            details.add("스텝1.6.sql 실행 완료");
            return PokedexStepResult.success("SUCCESS", details);
        } finally {
            lock.unlock();
        }
    }

    public PokedexStepResult processCalcyDexMoveSets() throws Exception {
        lock.lock();
        List<String> details = new ArrayList<>();
        try {
            boolean downloadTriggered = calcyDexSourceClient.refresh(details);
            calcyDexPokemonClient.refresh();
            details.add("pokedex 테이블에서 rank_ 1~205 대상 조회 중...");
            List<Integer> targetNos = calcyDexMoveSetMapper.selectTopRankedPokedexNos();
            if (targetNos == null || targetNos.isEmpty()) {
                throw new IllegalStateException("pokedex 테이블에 rank_ 1~205 대상이 없습니다.");
            }
            details.add(String.format("대상 포켓몬 조회 완료: %d건", targetNos.size()));

            Map<Integer, JsonNode> normalForms = new HashMap<>();
            details.add("외부 CalcyDex 데이터 파일을 다시 읽는 중...");
            for (Integer no : targetNos) {
                normalForms.put(no, calcyDexPokemonClient.fetchNormalForm(no));
            }
            details.add(String.format("외부 CalcyDex 데이터 로드 완료: %d건", normalForms.size()));

            List<CalcyDexMoveSetVO> rows = new ArrayList<>();
            int totalCombinationCount = 0;
            for (Integer no : targetNos) {
                CalcyDexMoveSetCalculator.CalculationResult result =
                        calcyDexMoveSetCalculator.calculate(no, normalForms.get(no));
                rows.addAll(result.selectedMoveSets());
                totalCombinationCount += result.totalCombinationCount();
                details.add(String.format(
                        "포켓몬 번호 %d 계산 완료: 전체 %d개 조합, 저장 대상 %d개",
                        no, result.totalCombinationCount(), result.selectedMoveSets().size()));
            }

            details.add("calcydex 테이블의 기존 데이터를 새 계산 결과로 교체 중...");
            int insertedCount = pokedexImportStore.replaceMoveSets(rows);
            details.add(String.format("calcydex 적재 완료: 총 %d건", insertedCount));

            return PokedexStepResult.success(
                    downloadTriggered ? "UPDATED" : "LATEST",
                    details,
                    Map.of(
                            "pokemonCount", targetNos.size(),
                            "combinationCount", totalCombinationCount,
                            "insertedCount", insertedCount));
        } finally {
            lock.unlock();
        }
    }

    public PokedexStepResult processPokedexStep2() throws Exception {
        lock.lock();
        List<String> details = new ArrayList<>();
        try {
            PokedexSourceClient.SourceFile source = pokedexSourceClient.refreshSpecies(details);
            boolean downloadTriggered = source.updated();
            var localFile = source.file();

            // 2. CSV 파싱 및 DB 적재
            if (!localFile.exists()) {
                details.add("오류: 파싱할 로컬 CSV 파일이 없습니다.");
                return PokedexStepResult.failure(
                        "로컬 CSV 파일이 존재하지 않습니다.", details);
            }

            details.add("로컬 pokemon_species.csv 파싱 시작...");
            List<PokemonSpeciesCsvVO> listToInsert = pokemonSpeciesCsvParser.parse(localFile.toPath());
            details.add(String.format("CSV 파싱 완료: 총 %d건 추출", listToInsert.size()));

            // DB 비우기 및 벌크 인서트
            details.add("pokemon_species_csv 테이블의 기존 데이터를 비우는 중...");
            int totalInserted = pokedexImportStore.replaceSpecies(
                    listToInsert,
                    (inserted, total) -> details.add(
                            String.format("데이터 적재 중... (%d/%d건)", inserted, total)));

            details.add(String.format("최종 DB 적재 완료: 총 %d건", totalInserted));
            return PokedexStepResult.success(
                    downloadTriggered ? "UPDATED" : "LATEST",
                    details,
                    "insertedCount",
                    totalInserted);
        } catch (Exception e) {
            details.add("예외 발생: " + e.getMessage());
            throw new PokedexUpdateException(PokedexUpdateStep.STEP2, details, e);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public PokedexStepResult processPokedexStep2_5() throws Exception {
        lock.lock();
        List<String> details = new ArrayList<>();
        try {
            // 0. Update ID 916's gender_rate
            details.add("ID 916의 gender_rate를 ID 915의 값으로 업데이트 중...");
            pokemonSpeciesCsvMapper.updateGenderRateFor916();

            details.add("pokemon_species_csv 데이터 조회 중...");
            List<PokemonSpeciesCsvVO> speciesList = pokemonSpeciesCsvMapper.selectAll();
            if (speciesList == null || speciesList.isEmpty()) {
                throw new IllegalStateException(
                        "pokemon_species_csv 테이블에 데이터가 없습니다. 스텝2를 먼저 진행해 주세요.");
            }

            pokedexDataProcessor.calculateEvolutionInfo(speciesList);
            details.add("1) evolution_chain_id 기준 base_no 계산 완료.");
            details.add("2) evolves_from_species_id 기준 evl 계산 완료.");

            // 3. Update DB in batches
            details.add("DB 업데이트 시작 (500개씩 벌크 분할)...");
            int totalUpdated = 0;
            int batchSize = 500;
            for (int i = 0; i < speciesList.size(); i += batchSize) {
                List<PokemonSpeciesCsvVO> subList = speciesList.subList(i, Math.min(i + batchSize, speciesList.size()));
                pokemonSpeciesCsvMapper.updateBaseNoAndEvlBatch(subList);
                totalUpdated += subList.size();
                details.add(String.format("데이터 업데이트 중... (%d/%d건)", totalUpdated, speciesList.size()));
            }

            details.add(String.format("최종 DB 업데이트 완료: 총 %d건", totalUpdated));
            return PokedexStepResult.success(
                    "SUCCESS", details, "updatedCount", totalUpdated);
        } catch (Exception e) {
            details.add("예외 발생: " + e.getMessage());
            throw new PokedexUpdateException(PokedexUpdateStep.STEP2_5, details, e);
        } finally {
            lock.unlock();
        }
    }

}
