package io.github.limbj.board.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import io.github.limbj.board.domain.Criteria;
import io.github.limbj.board.domain.ReplyVO;

public interface ReplyMapper {

	int insert(ReplyVO vo);

	ReplyVO read(Long rno);

	ReplyVO readForUpdate(Long rno);

	int update(ReplyVO reply);

	int delete(Long rno);

	/** 게시글 삭제 시 해당 게시글의 댓글 전체 삭제 */
	int deleteAll(Long bno);

	List<ReplyVO> getListWithPaging(@Param("cri") Criteria cri, @Param("bno") Long bno);

	int getCountByBno(Long bno);
}
