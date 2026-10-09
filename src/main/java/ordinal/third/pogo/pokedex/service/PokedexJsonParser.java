package ordinal.third.pogo.pokedex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import ordinal.third.pogo.vo.PokedexJsonVO;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PokedexJsonParser {

    private final ObjectMapper objectMapper;

    public ParseResult parse(Path path) throws IOException {
        JsonNode rootNode = objectMapper.readTree(path.toFile());
        if (!rootNode.isArray()) {
            throw new IllegalArgumentException("pokedex.json 파일의 형식이 올바르지 않습니다. (배열이 아님)");
        }

        List<PokedexJsonVO> pokemon = new ArrayList<>();
        int baseCount = 0;
        int formCount = 0;

        for (JsonNode node : rootNode) {
            pokemon.add(parsePokemonNode(node, 1));
            baseCount++;

            JsonNode regionFormsNode = node.get("regionForms");
            if (regionFormsNode != null && regionFormsNode.isObject()) {
                Iterator<JsonNode> forms = regionFormsNode.elements();
                while (forms.hasNext()) {
                    pokemon.add(parsePokemonNode(forms.next(), 0));
                    formCount++;
                }
            }
        }

        int femaleImageCount = mapFemaleImages(rootNode, pokemon);
        return new ParseResult(List.copyOf(pokemon), baseCount, formCount, femaleImageCount);
    }

    private int mapFemaleImages(JsonNode rootNode, List<PokedexJsonVO> pokemon) {
        Map<String, List<PokedexJsonVO>> pokemonByImage = new HashMap<>();
        for (PokedexJsonVO vo : pokemon) {
            if (vo.getImage() != null) {
                pokemonByImage.computeIfAbsent(vo.getImage(), unused -> new ArrayList<>()).add(vo);
            }
        }

        int mappedCount = 0;
        for (JsonNode node : rootNode) {
            JsonNode assetFormsNode = node.get("assetForms");
            if (assetFormsNode == null || !assetFormsNode.isArray()) {
                continue;
            }
            for (JsonNode assetForm : assetFormsNode) {
                JsonNode isFemaleNode = assetForm.get("isFemale");
                JsonNode formNode = assetForm.get("form");
                boolean female = (isFemaleNode != null && isFemaleNode.asBoolean())
                        || (formNode != null && "FEMALE".equals(formNode.asText()));
                JsonNode imageNode = assetForm.get("image");
                if (!female || imageNode == null || imageNode.isNull()) {
                    continue;
                }

                String originalImage = imageNode.asText();
                String baseImage = originalImage.replace(".g2", "").replace(".fFEMALE", "");
                for (PokedexJsonVO vo : pokemonByImage.getOrDefault(baseImage, List.of())) {
                    vo.setImageG2(originalImage);
                    mappedCount++;
                }
            }
        }
        return mappedCount;
    }

    private PokedexJsonVO parsePokemonNode(JsonNode node, int dexFormNr) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("포켓몬 노드가 비어 있습니다.");
        }

        PokedexJsonVO vo = new PokedexJsonVO();
        vo.setDexFormNr(dexFormNr);

        String id = node.path("id").asText("");
        if (id.isEmpty()) {
            throw new IllegalArgumentException("포켓몬 노드에 'id' 필드가 존재하지 않거나 비어 있습니다.");
        }
        vo.setId(id);

        String formId = node.path("formId").asText("");
        if (formId.isEmpty()) {
            throw new IllegalArgumentException("포켓몬 노드에 'formId' 필드가 존재하지 않거나 비어 있습니다.");
        }
        vo.setFormId(formId);

        int dexNr = node.path("dexNr").asInt(-1);
        if (dexNr < 0) {
            throw new IllegalArgumentException("포켓몬 노드(ID: " + id + ")에 'dexNr' 필드가 존재하지 않거나 음수입니다.");
        }
        vo.setDexNr(dexNr);

        String koreanName = node.path("names").path("Korean").asText("");
        if (koreanName.isEmpty()) {
            throw new IllegalArgumentException("포켓몬 노드(ID: " + id + ")에 한국어 이름(names.Korean)이 누락되었습니다.");
        }
        vo.setKorean(koreanName);

        JsonNode statsNode = node.path("stats");
        if (statsNode.isMissingNode() || statsNode.isNull()) {
            throw new IllegalArgumentException("포켓몬 노드(ID: " + id + ")에 'stats' 정보가 없습니다.");
        }
        int attack = statsNode.path("attack").asInt(-1);
        int defense = statsNode.path("defense").asInt(-1);
        int stamina = statsNode.path("stamina").asInt(-1);
        if (attack < 0 || defense < 0 || stamina < 0) {
            throw new IllegalArgumentException("포켓몬 노드(ID: " + id + ")의 공격/방어/체력 스탯 값이 올바르지 않습니다.");
        }
        vo.setAttack(attack);
        vo.setDefense(defense);
        vo.setStamina(stamina);

        JsonNode pokemonClassNode = node.get("pokemonClass");
        vo.setPokemonClass(pokemonClassNode == null || pokemonClassNode.isNull()
                ? null : pokemonClassNode.asText());

        JsonNode imageNode = node.path("assets").path("image");
        vo.setImage(imageNode.isMissingNode() || imageNode.isNull() ? null : imageNode.asText());
        return vo;
    }

    public record ParseResult(
            List<PokedexJsonVO> pokemon,
            int baseCount,
            int formCount,
            int femaleImageCount) {
    }
}
