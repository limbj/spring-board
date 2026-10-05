package io.github.limbj.board.support;

import java.util.ArrayList;
import java.util.List;

import io.github.limbj.board.domain.BoardAttachVO;
import io.github.limbj.board.mapper.BoardAttachMapper;

/** DB 없이 FileCheckTask를 테스트하기 위한 메모리 구현 */
public class FakeBoardAttachMapper implements BoardAttachMapper {

	private final List<BoardAttachVO> rows = new ArrayList<>();

	@Override
	public void insert(BoardAttachVO vo) {
		rows.add(vo);
	}

	@Override
	public List<BoardAttachVO> findByBno(Long bno) {
		List<BoardAttachVO> result = new ArrayList<>();
		for (BoardAttachVO row : rows) {
			if (bno.equals(row.getBno())) {
				result.add(row);
			}
		}
		return result;
	}

	@Override
	public BoardAttachVO findByUuid(String uuid) {
		for (BoardAttachVO row : rows) {
			if (uuid.equals(row.getUuid())) {
				return row;
			}
		}
		return null;
	}

	@Override
	public List<BoardAttachVO> findByUploadPath(String uploadPath) {
		List<BoardAttachVO> result = new ArrayList<>();
		for (BoardAttachVO row : rows) {
			if (uploadPath.equals(row.getUploadPath())) {
				result.add(row);
			}
		}
		return result;
	}

	@Override
	public void deleteAll(Long bno) {
		rows.removeIf(row -> bno.equals(row.getBno()));
	}
}
