package ordinal.third.pogo.table.service;

import ordinal.third.pogo.table.mapper.Pogo5deMapper;
import ordinal.third.pogo.table.mapper.Pogo5TableMapper;
import ordinal.third.pogo.vo.Pogo5TableVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PogoServiceTest {

    @Mock
    private Pogo5TableMapper pogo5TableMapper;
    @Mock
    private Pogo5deMapper pogo5deMapper;
    @InjectMocks
    private PogoService service;

    private Pogo5TableVO vo(String als, String srchNm) {
        Pogo5TableVO vo = new Pogo5TableVO();
        vo.setAls(als);
        vo.setSrchNm(srchNm);
        return vo;
    }

    @Test
    void append_als_문자열을_성별_레벨_이름_IV로_분해한다() {
        Pogo5TableVO vo = vo("Xm50피카츄 96", null);

        service.appendPogo5Table("5f", vo);

        assertThat(vo.getGender()).isEqualTo("m");
        assertThat(vo.getLvl()).isEqualTo("50");
        assertThat(vo.getName()).isEqualTo("피카츄");
        assertThat(vo.getIv()).isEqualTo("96");
        verify(pogo5TableMapper).append("POGO_5F", vo);
    }

    @Test
    void append_IV가_없는_als_문자열은_IV를_100으로_채운다() {
        Pogo5TableVO vo = vo("Xg38뮤", null);

        service.appendPogo5Table("5s", vo);

        assertThat(vo.getGender()).isEqualTo("g");
        assertThat(vo.getLvl()).isEqualTo("38");
        assertThat(vo.getName()).isEqualTo("뮤");
        assertThat(vo.getIv()).isEqualTo("100");
        verify(pogo5TableMapper).append("POGO_5S", vo);
    }

    @Test
    void append_als_문자열이_너무_짧으면_분해하지_않는다() {
        Pogo5TableVO vo = vo("Xm", null);

        service.appendPogo5Table("5t", vo);

        assertThat(vo.getGender()).isNull();
        assertThat(vo.getLvl()).isNull();
        assertThat(vo.getName()).isNull();
        assertThat(vo.getIv()).isNull();
        verify(pogo5TableMapper).append("POGO_5T", vo);
    }

    @Test
    void 알수없는_테이블_키는_예외를_던지고_매퍼를_호출하지_않는다() {
        assertThatThrownBy(() -> service.appendPogo5Table("5x", vo("Xm50피카츄", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5x");
        verifyNoInteractions(pogo5TableMapper);
    }

    @Test
    void 검색어가_없으면_전체_목록을_조회한다() {
        service.selectPogo5TableList("5t", vo(null, null));

        verify(pogo5TableMapper).list("POGO_5T");
    }

    @Test
    void 검색어_y_n은_저장여부_첫글자로_조회한다() {
        service.selectPogo5TableList("5f", vo(null, "y"));
        service.selectPogo5TableList("5f", vo(null, "n"));

        verify(pogo5TableMapper).listByAlsChar("POGO_5F", 1, "y");
        verify(pogo5TableMapper).listByAlsChar("POGO_5F", 1, "n");
    }

    @Test
    void 성별_검색어는_두번째_글자로_조회한다() {
        service.selectPogo5TableList("5s", vo(null, "수컷"));
        service.selectPogo5TableList("5s", vo(null, "암컷"));
        service.selectPogo5TableList("5s", vo(null, "성별불명"));

        verify(pogo5TableMapper).listByAlsChar("POGO_5S", 2, "m");
        verify(pogo5TableMapper).listByAlsChar("POGO_5S", 2, "f");
        verify(pogo5TableMapper).listByAlsChar("POGO_5S", 2, "g");
    }

    @Test
    void 검색어가_플러스로_시작하면_baseNo_검색으로_위임한다() {
        Pogo5TableVO vo = vo(null, "+이상해씨");

        service.selectPogo5TableList("5t", vo);

        verify(pogo5TableMapper).listByBasNm("POGO_5T", vo);
        assertThat(vo.getSrchNm()).isEqualTo("이상해씨");
        verify(pogo5TableMapper, never()).listByNm(any(), any());
    }

    @Test
    void 그밖의_검색어는_이름_검색으로_위임한다() {
        Pogo5TableVO vo = vo(null, "전기타입");

        service.selectPogo5TableList("5f", vo);

        verify(pogo5TableMapper).listByNm("POGO_5F", vo);
    }

    @Test
    void update는_als를_분해하고_매퍼를_호출한다() {
        Pogo5TableVO vo = vo("Xf25레디안 88", null);

        service.updatePogo5Table("5t", vo);

        assertThat(vo.getName()).isEqualTo("레디안");
        verify(pogo5TableMapper).update("POGO_5T", vo);
    }

    @Test
    void delete는_삭제와_순번_감소를_모두_호출한다() {
        Pogo5TableVO vo = vo(null, null);

        service.deletePogo5Table("5f", vo);

        verify(pogo5TableMapper).delete("POGO_5F", vo);
        verify(pogo5TableMapper).updateDec("POGO_5F", vo);
    }

    @Test
    void insert는_순번_증가와_삽입을_순서대로_호출한다() {
        Pogo5TableVO vo = vo("Xm50피카츄 96", null);

        service.insertPogo5Table("5f", vo);

        assertThat(vo.getName()).isEqualTo("피카츄");
        // 순번 증가가 삽입보다 먼저 실행되어야 한다
        var inOrder = org.mockito.Mockito.inOrder(pogo5TableMapper);
        inOrder.verify(pogo5TableMapper).updateInc("POGO_5F", vo);
        inOrder.verify(pogo5TableMapper).insert("POGO_5F", vo);
    }

    @Test
    void pogo5de_목록은_전용_매퍼로_조회한다() {
        service.selectPogo5deList();

        verify(pogo5deMapper).list();
        verify(pogo5TableMapper, never()).list(any());
    }
}
