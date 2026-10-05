package io.github.limbj.board.domain;

import static org.junit.Assert.*;
import org.junit.Test;
import io.github.limbj.board.exception.BadRequestException;

public class CriteriaTest {
    @Test public void defaultsAndLimitsAreSafe() {
        Criteria defaults = new Criteria();
        assertEquals(1, defaults.getPageNum());
        assertEquals(10, defaults.getAmount());
        Criteria bounded = new Criteria(-1, -2);
        assertEquals(1, bounded.getPageNum());
        assertEquals(10, bounded.getAmount());
        bounded.setPageNum(Integer.MAX_VALUE);
        bounded.setAmount(Integer.MAX_VALUE);
        assertEquals(Criteria.MAX_PAGE_NUM, bounded.getPageNum());
        assertEquals(100, bounded.getAmount());
    }

    @Test public void canonicalizesSearchTypeAndTrimsKeyword() {
        Criteria criteria = new Criteria();
        criteria.setType(" CWT ");
        criteria.setKeyword(" 스프링 ");
        assertEquals("TWC", criteria.getType());
        assertArrayEquals(new String[] { "T", "W", "C" }, criteria.getTypeArr());
        assertEquals("스프링", criteria.getKeyword());
    }

    @Test public void absentSearchHasNoTypeArray() {
        assertEquals(0, new Criteria().getTypeArr().length);
        Criteria criteria = new Criteria();
        criteria.setType("T");
        criteria.normalize();
        assertNull(criteria.getType());
    }

    @Test(expected = BadRequestException.class)
    public void rejectsUnknownType() { new Criteria().setType("TX"); }

    @Test(expected = BadRequestException.class)
    public void rejectsOverlongKeyword() { new Criteria().setKeyword("x".repeat(101)); }

    @Test(expected = BadRequestException.class)
    public void keywordRequiresSearchType() {
        Criteria criteria = new Criteria();
        criteria.setKeyword("query");
        criteria.normalize();
    }

    @Test public void clampsToLastPageAndEncodesReservedQueryCharacters() {
        Criteria criteria = new Criteria(999, 10);
        criteria.setType("T");
        criteria.setKeyword("스프링 & a=b+#?");
        criteria.clampToTotal(21);
        assertEquals(3, criteria.getPageNum());
        String link = criteria.getListLink();
        assertTrue(link.startsWith("?pageNum=3&amount=10&type=T&keyword="));
        assertTrue(link.contains("%26"));
        assertTrue(link.contains("%3D"));
        assertTrue(link.contains("%23"));
        assertTrue(link.contains("%2B"));
        criteria.clampToTotal(0);
        assertEquals(1, criteria.getPageNum());
    }
}
