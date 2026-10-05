package io.github.limbj.board.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import io.github.limbj.board.domain.BoardVO;
import io.github.limbj.board.domain.Criteria;

public interface BoardMapper {

	List<BoardVO> getListWithPaging(Criteria cri);

	int getTotalCount(Criteria cri);

	/** seq_board에서 번호를 먼저 받아 board.bno에 채운 뒤 저장 */
	void insert(BoardVO board);

	BoardVO read(Long bno);

	BoardVO readForUpdate(Long bno);

	int update(BoardVO board);

	int delete(Long bno);

	int updateReplyCnt(@Param("bno") Long bno, @Param("amount") int amount);
}
