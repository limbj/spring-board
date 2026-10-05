package io.github.limbj.board.domain;

import org.springframework.web.util.UriComponentsBuilder;
import io.github.limbj.board.exception.BadRequestException;

/** Bounded query parameters shared by board and reply pagination. */
public class Criteria {
    public static final int MAX_AMOUNT = 100;
    public static final int MAX_PAGE_NUM = 1_000_000_000;
    private int pageNum;
    private int amount;
    private String type;
    private String keyword;

    public Criteria() { this(1, 10); }
    public Criteria(int pageNum, int amount) {
        setPageNum(pageNum);
        setAmount(amount);
    }

    public int getPageNum() { return pageNum; }
    public void setPageNum(int pageNum) { this.pageNum = Math.min(MAX_PAGE_NUM, Math.max(1, pageNum)); }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount < 1 ? 10 : Math.min(MAX_AMOUNT, amount); }
    public String getType() { return type; }
    public void setType(String type) {
        String value = type == null ? "" : type.strip();
        if (value.isEmpty()) {
            this.type = null;
            return;
        }
        if (!value.matches("[TWC]{1,3}")) {
            throw new BadRequestException("검색 조건은 제목(T), 내용(C), 작성자(W)만 사용할 수 있습니다.");
        }
        StringBuilder normalized = new StringBuilder();
        for (char field : new char[] { 'T', 'W', 'C' }) {
            if (value.indexOf(field) >= 0) normalized.append(field);
        }
        this.type = normalized.toString();
    }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) {
        String value = keyword == null ? "" : keyword.strip();
        if (value.length() > 100) throw new BadRequestException("검색어는 100자 이내로 입력해 주세요.");
        this.keyword = value.isEmpty() ? null : value;
    }

    public String[] getTypeArr() {
        return type == null ? new String[0] : type.split("");
    }

    public void normalize() {
        setPageNum(pageNum);
        setAmount(amount);
        setType(type);
        setKeyword(keyword);
        if (keyword != null && type == null) {
            throw new BadRequestException("검색 조건을 선택해 주세요.");
        }
        if (keyword == null) type = null;
    }

    public void clampToTotal(int total) {
        normalize();
        long pages = ((long) Math.max(0, total) + amount - 1L) / amount;
        pageNum = (int) Math.max(1L, Math.min(pageNum, pages));
    }

    public String getListLink() {
        normalize();
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("")
                .queryParam("pageNum", pageNum).queryParam("amount", amount);
        if (type != null && keyword != null) {
            builder.queryParam("type", type).queryParam("keyword", "{keyword}");
            return builder.encode().buildAndExpand(java.util.Map.of("keyword", keyword)).toUriString();
        }
        return builder.build().encode().toUriString();
    }
}
