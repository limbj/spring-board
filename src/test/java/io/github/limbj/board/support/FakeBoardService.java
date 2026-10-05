package io.github.limbj.board.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.github.limbj.board.domain.BoardAttachVO;
import io.github.limbj.board.domain.BoardVO;
import io.github.limbj.board.domain.Criteria;
import io.github.limbj.board.domain.InputRules;
import io.github.limbj.board.exception.NotFoundException;
import io.github.limbj.board.service.BoardService;

/** Controller fixture with the production service's validation and paging semantics. */
public class FakeBoardService implements BoardService {
    private final Map<Long, BoardVO> boards = new LinkedHashMap<>();
    private final Map<Long, List<BoardAttachVO>> attachments = new HashMap<>();
    private long nextBno = 1;
    public BoardVO lastRegistered;

    public BoardVO addBoard(String title) {
        BoardVO board = new BoardVO();
        board.setTitle(title);
        board.setContent("내용");
        board.setWriter("tester");
        register(board);
        lastRegistered = null;
        return board;
    }

    public void addAttach(Long bno, BoardAttachVO attach) {
        attach.setBno(bno);
        attachments.computeIfAbsent(bno, key -> new ArrayList<>()).add(attach);
    }

    @Override public void register(BoardVO board) {
        InputRules.board(board, true);
        board.setBno(nextBno++);
        board.setRegdate(new Date());
        board.setUpdateDate(board.getRegdate());
        boards.put(board.getBno(), board);
        attachments.put(board.getBno(), copy(board.getAttachList()));
        lastRegistered = board;
    }

    @Override public BoardVO get(Long bno) {
        InputRules.positiveId(bno, "게시글 번호");
        return boards.get(bno);
    }

    @Override public boolean modify(BoardVO board) {
        InputRules.board(board, false);
        BoardVO existing = boards.get(board.getBno());
        if (existing == null) throw new NotFoundException("게시글이 없습니다.");
        existing.setTitle(board.getTitle());
        existing.setContent(board.getContent());
        existing.setUpdateDate(new Date());
        if (board.getAttachList() != null) attachments.put(board.getBno(), copy(board.getAttachList()));
        return true;
    }

    @Override public boolean remove(Long bno) {
        InputRules.positiveId(bno, "게시글 번호");
        if (!boards.containsKey(bno)) throw new NotFoundException("게시글이 없습니다.");
        attachments.remove(bno);
        return boards.remove(bno) != null;
    }

    @Override public List<BoardVO> getList(Criteria criteria) {
        criteria.normalize();
        List<BoardVO> found = matching(criteria);
        criteria.clampToTotal(found.size());
        int from = (int) Math.min(((long) criteria.getPageNum() - 1L) * criteria.getAmount(), found.size());
        int to = Math.min(from + criteria.getAmount(), found.size());
        return new ArrayList<>(found.subList(from, to));
    }

    @Override public int getTotal(Criteria criteria) {
        criteria.normalize();
        return matching(criteria).size();
    }

    @Override public List<BoardAttachVO> getAttachList(Long bno) {
        InputRules.positiveId(bno, "게시글 번호");
        if (!boards.containsKey(bno)) throw new NotFoundException("게시글이 없습니다.");
        return copy(attachments.get(bno));
    }

    @Override public boolean isAttached(String uuid) {
        return attachments.values().stream().flatMap(List::stream).anyMatch(attach -> attach.getUuid().equals(uuid));
    }

    private List<BoardVO> matching(Criteria criteria) {
        List<BoardVO> result = new ArrayList<>();
        for (BoardVO board : boards.values()) {
            String type = criteria.getType();
            String keyword = criteria.getKeyword();
            if (keyword == null || type == null
                    || type.contains("T") && board.getTitle().contains(keyword)
                    || type.contains("C") && board.getContent().contains(keyword)
                    || type.contains("W") && board.getWriter().contains(keyword)) result.add(board);
        }
        result.sort(Comparator.comparing(BoardVO::getBno).reversed());
        return result;
    }

    private List<BoardAttachVO> copy(List<BoardAttachVO> list) {
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }
}
