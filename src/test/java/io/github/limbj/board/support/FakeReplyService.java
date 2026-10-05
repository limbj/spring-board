package io.github.limbj.board.support;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.github.limbj.board.domain.Criteria;
import io.github.limbj.board.domain.InputRules;
import io.github.limbj.board.domain.ReplyPageDTO;
import io.github.limbj.board.domain.ReplyVO;
import io.github.limbj.board.exception.NotFoundException;
import io.github.limbj.board.service.ReplyService;

public class FakeReplyService implements ReplyService {
    private final Map<Long, ReplyVO> replies = new LinkedHashMap<>();
    private final Set<Long> boards = new HashSet<>();
    private long nextRno = 1;

    public void addBoard(long bno) { boards.add(bno); }
    public int count() { return replies.size(); }

    @Override public int register(ReplyVO reply) {
        InputRules.reply(reply, true);
        requireBoard(reply.getBno());
        reply.setRno(nextRno++);
        reply.setReplyDate(new Date());
        replies.put(reply.getRno(), reply);
        return 1;
    }

    @Override public ReplyVO get(Long rno) {
        InputRules.positiveId(rno, "댓글 번호");
        return replies.get(rno);
    }

    @Override public int modify(ReplyVO reply) {
        InputRules.reply(reply, false);
        ReplyVO existing = replies.get(reply.getRno());
        if (existing == null) return 0;
        existing.setReply(reply.getReply());
        existing.setUpdateDate(new Date());
        return 1;
    }

    @Override public int remove(Long rno) {
        InputRules.positiveId(rno, "댓글 번호");
        return replies.remove(rno) == null ? 0 : 1;
    }

    @Override public ReplyPageDTO getListPage(Criteria criteria, Long bno) {
        InputRules.positiveId(bno, "게시글 번호");
        requireBoard(bno);
        List<ReplyVO> found = new ArrayList<>();
        for (ReplyVO reply : replies.values()) {
            if (bno.equals(reply.getBno())) found.add(reply);
        }
        criteria.clampToTotal(found.size());
        int from = (int) Math.min(((long) criteria.getPageNum() - 1L) * criteria.getAmount(), found.size());
        int to = Math.min(from + criteria.getAmount(), found.size());
        return new ReplyPageDTO(found.size(), new ArrayList<>(found.subList(from, to)), criteria.getPageNum());
    }

    private void requireBoard(Long bno) {
        if (!boards.contains(bno)) throw new NotFoundException("게시글이 없습니다.");
    }
}
