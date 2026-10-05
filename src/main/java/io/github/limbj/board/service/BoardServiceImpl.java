package io.github.limbj.board.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.limbj.board.domain.BoardAttachVO;
import io.github.limbj.board.domain.BoardVO;
import io.github.limbj.board.domain.Criteria;
import io.github.limbj.board.domain.InputRules;
import io.github.limbj.board.exception.NotFoundException;
import io.github.limbj.board.mapper.BoardAttachMapper;
import io.github.limbj.board.mapper.BoardMapper;
import io.github.limbj.board.mapper.ReplyMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {
    private final BoardMapper mapper;
    private final BoardAttachMapper attachMapper;
    private final ReplyMapper replyMapper;

    @Transactional
    @Override public void register(BoardVO board) {
        InputRules.board(board, true);
        mapper.insert(board);
        if (board.getBno() == null || board.getBno() < 1) throw new IllegalStateException("Missing inserted board number");
        insertAttachments(board);
    }

    @Override public BoardVO get(Long bno) {
        InputRules.positiveId(bno, "게시글 번호");
        return mapper.read(bno);
    }

    @Transactional
    @Override public boolean modify(BoardVO board) {
        InputRules.board(board, false);
        requireLockedBoard(board.getBno());
        if (mapper.update(board) != 1) throw new NotFoundException("게시글이 없습니다.");
        // A missing attachment parameter preserves the database's current list.
        if (board.getAttachList() != null) {
            attachMapper.deleteAll(board.getBno());
            insertAttachments(board);
        }
        return true;
    }

    @Transactional
    @Override public boolean remove(Long bno) {
        InputRules.positiveId(bno, "게시글 번호");
        requireLockedBoard(bno);
        // Every board/reply mutation acquires this parent lock before child locks.
        replyMapper.deleteAll(bno);
        attachMapper.deleteAll(bno);
        if (mapper.delete(bno) != 1) throw new NotFoundException("게시글이 없습니다.");
        return true;
    }

    @Override public List<BoardVO> getList(Criteria criteria) {
        requireCriteria(criteria);
        criteria.clampToTotal(mapper.getTotalCount(criteria));
        return mapper.getListWithPaging(criteria);
    }

    @Override public int getTotal(Criteria criteria) {
        requireCriteria(criteria);
        return mapper.getTotalCount(criteria);
    }

    @Override public List<BoardAttachVO> getAttachList(Long bno) {
        InputRules.positiveId(bno, "게시글 번호");
        if (mapper.read(bno) == null) throw new NotFoundException("게시글이 없습니다.");
        return attachMapper.findByBno(bno);
    }

    @Override public boolean isAttached(String uuid) {
        return uuid != null && attachMapper.findByUuid(uuid) != null;
    }

    private void requireLockedBoard(Long bno) {
        if (mapper.readForUpdate(bno) == null) throw new NotFoundException("게시글이 없습니다.");
    }

    private void requireCriteria(Criteria criteria) {
        if (criteria == null) throw new IllegalArgumentException("Criteria is required");
        criteria.normalize();
    }

    private void insertAttachments(BoardVO board) {
        if (board.getAttachList() == null) return;
        if (board.getAttachList().size() > 10) throw new io.github.limbj.board.exception.BadRequestException("첨부파일은 최대 10개입니다.");
        for (BoardAttachVO attach : board.getAttachList()) {
            if (attach == null || attach.getUuid() == null || attach.getFileName() == null || attach.getUploadPath() == null) {
                throw new io.github.limbj.board.exception.BadRequestException("첨부파일 정보가 올바르지 않습니다.");
            }
            attach.setBno(board.getBno());
            attachMapper.insert(attach);
        }
    }
}
