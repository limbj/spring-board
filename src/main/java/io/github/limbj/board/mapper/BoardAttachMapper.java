package io.github.limbj.board.mapper;

import java.util.List;

import io.github.limbj.board.domain.BoardAttachVO;

public interface BoardAttachMapper {

	void insert(BoardAttachVO vo);

	List<BoardAttachVO> findByBno(Long bno);

	BoardAttachVO findByUuid(String uuid);

	/** 특정 날짜 폴더에 저장된 첨부파일 (FileCheckTask에서 사용) */
	List<BoardAttachVO> findByUploadPath(String uploadPath);

	void deleteAll(Long bno);
}
