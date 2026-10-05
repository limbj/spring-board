package io.github.limbj.board.service;

import java.util.List;

import io.github.limbj.board.domain.BoardAttachVO;
import io.github.limbj.board.domain.BoardVO;
import io.github.limbj.board.domain.Criteria;

public interface BoardService {

	void register(BoardVO board);

	BoardVO get(Long bno);

	boolean modify(BoardVO board);

	boolean remove(Long bno);

	List<BoardVO> getList(Criteria cri);

	int getTotal(Criteria cri);

	List<BoardAttachVO> getAttachList(Long bno);

	/** 게시글에 저장된 첨부파일인지 (저장된 파일은 /deleteFile로 지울 수 없게 막는 데 사용) */
	boolean isAttached(String uuid);
}
