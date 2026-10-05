package io.github.limbj.board.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.limbj.board.domain.Criteria;
import io.github.limbj.board.domain.InputRules;
import io.github.limbj.board.domain.ReplyPageDTO;
import io.github.limbj.board.domain.ReplyVO;
import io.github.limbj.board.exception.NotFoundException;
import io.github.limbj.board.mapper.BoardMapper;
import io.github.limbj.board.mapper.ReplyMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReplyServiceImpl implements ReplyService {
    private final ReplyMapper mapper;
    private final BoardMapper boardMapper;

    @Transactional
    @Override public int register(ReplyVO reply) {
        InputRules.reply(reply, true);
        if (boardMapper.readForUpdate(reply.getBno()) == null) throw new NotFoundException("게시글이 없습니다.");
        if (mapper.insert(reply) != 1) throw new IllegalStateException("Expected one inserted reply");
        updateCount(reply.getBno(), 1);
        return 1;
    }

    @Override public ReplyVO get(Long rno) {
        InputRules.positiveId(rno, "댓글 번호");
        return mapper.read(rno);
    }

    @Transactional
    @Override public int modify(ReplyVO reply) {
        InputRules.reply(reply, false);
        ReplyVO existing = lockReply(reply.getRno());
        return existing == null ? 0 : mapper.update(reply);
    }

    @Transactional
    @Override public int remove(Long rno) {
        InputRules.positiveId(rno, "댓글 번호");
        ReplyVO existing = lockReply(rno);
        if (existing == null) return 0;
        int deleted = mapper.delete(rno);
        if (deleted == 1) updateCount(existing.getBno(), -1);
        return deleted;
    }

    @Override public ReplyPageDTO getListPage(Criteria criteria, Long bno) {
        InputRules.positiveId(bno, "게시글 번호");
        if (boardMapper.read(bno) == null) throw new NotFoundException("게시글이 없습니다.");
        if (criteria == null) throw new IllegalArgumentException("Criteria is required");
        criteria.normalize();
        int count = mapper.getCountByBno(bno);
        criteria.clampToTotal(count);
        return new ReplyPageDTO(count, mapper.getListWithPaging(criteria, bno), criteria.getPageNum());
    }

    private ReplyVO lockReply(Long rno) {
        ReplyVO candidate = mapper.read(rno);
        if (candidate == null || boardMapper.readForUpdate(candidate.getBno()) == null) return null;
        return mapper.readForUpdate(rno);
    }

    private void updateCount(Long bno, int amount) {
        if (boardMapper.updateReplyCnt(bno, amount) != 1) {
            throw new NotFoundException("게시글이 없습니다.");
        }
    }
}
