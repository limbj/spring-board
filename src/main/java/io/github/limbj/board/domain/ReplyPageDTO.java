package io.github.limbj.board.domain;

import java.util.List;
import lombok.Getter;

@Getter
public final class ReplyPageDTO {
    private final int replyCnt;
    private final List<ReplyVO> list;
    private final int pageNum;

    public ReplyPageDTO(int replyCnt, List<ReplyVO> list) {
        this(replyCnt, list, 1);
    }

    public ReplyPageDTO(int replyCnt, List<ReplyVO> list, int pageNum) {
        this.replyCnt = replyCnt;
        this.list = list == null ? List.of() : List.copyOf(list);
        this.pageNum = pageNum;
    }
}
