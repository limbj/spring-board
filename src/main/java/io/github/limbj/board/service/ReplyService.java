package io.github.limbj.board.service;

import io.github.limbj.board.domain.Criteria;
import io.github.limbj.board.domain.ReplyPageDTO;
import io.github.limbj.board.domain.ReplyVO;

public interface ReplyService {

	int register(ReplyVO vo);

	ReplyVO get(Long rno);

	int modify(ReplyVO vo);

	int remove(Long rno);

	ReplyPageDTO getListPage(Criteria cri, Long bno);
}
