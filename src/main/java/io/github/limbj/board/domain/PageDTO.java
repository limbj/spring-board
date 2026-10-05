package io.github.limbj.board.domain;

import lombok.Getter;

/** Immutable page block calculated after the requested page has been bounded. */
@Getter
public final class PageDTO {
    private final int startPage;
    private final int endPage;
    private final boolean prev;
    private final boolean next;
    private final int total;
    private final int totalPages;
    private final Criteria cri;

    public PageDTO(Criteria criteria, int total) {
        if (criteria == null || total < 0) {
            throw new IllegalArgumentException("Invalid page criteria or total");
        }
        criteria.clampToTotal(total);
        this.cri = criteria;
        this.total = total;
        this.totalPages = (int) (((long) total + criteria.getAmount() - 1L) / criteria.getAmount());
        if (totalPages == 0) {
            startPage = 1;
            endPage = 0;
            prev = false;
            next = false;
        } else {
            startPage = ((criteria.getPageNum() - 1) / 10) * 10 + 1;
            endPage = (int) Math.min((long) startPage + 9L, totalPages);
            prev = startPage > 1;
            next = endPage < totalPages;
        }
    }
}
