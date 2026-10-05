package io.github.limbj.board.domain;

import static org.junit.Assert.*;
import org.junit.Test;

public class PageDTOTest {
    @Test public void firstAndLastPageBlocks() {
        PageDTO first = new PageDTO(new Criteria(1, 10), 123);
        assertEquals(1, first.getStartPage());
        assertEquals(10, first.getEndPage());
        assertFalse(first.isPrev());
        assertTrue(first.isNext());
        PageDTO last = new PageDTO(new Criteria(12, 10), 123);
        assertEquals(11, last.getStartPage());
        assertEquals(13, last.getEndPage());
        assertTrue(last.isPrev());
        assertFalse(last.isNext());
    }

    @Test public void missingPageClampsToActualLastBlock() {
        PageDTO page = new PageDTO(new Criteria(Integer.MAX_VALUE, 10), 23);
        assertEquals(3, page.getCri().getPageNum());
        assertEquals(1, page.getStartPage());
        assertEquals(3, page.getEndPage());
    }

    @Test public void emptyBoardsNeverGenerateNegativePageNumbers() {
        PageDTO page = new PageDTO(new Criteria(0, 0), 0);
        assertEquals(1, page.getCri().getPageNum());
        assertEquals(1, page.getStartPage());
        assertEquals(0, page.getEndPage());
        assertFalse(page.isPrev());
        assertFalse(page.isNext());
    }

    @Test public void largeTotalsDoNotOverflow() {
        PageDTO page = new PageDTO(new Criteria(Integer.MAX_VALUE, 1), Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, page.getTotalPages());
        assertTrue(page.getStartPage() > 0);
        assertTrue(page.getEndPage() > 0);
        assertTrue(page.isNext());
    }
}
