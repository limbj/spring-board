package io.github.limbj.board.domain;

import lombok.Data;

/**
 * 게시글에 저장된 첨부파일 (tbl_attach)
 */
@Data
public class BoardAttachVO {

	private String uuid;
	private String uploadPath;
	private String fileName;
	private boolean fileType; // true: 이미지(섬네일 있음)

	private Long bno;
}
