package io.github.limbj.board.domain;

import lombok.Data;

/**
 * 업로드 직후 화면에 돌려주는 파일 정보 (아직 게시글에 저장되기 전)
 */
@Data
public class AttachFileDTO {

	private String fileName;
	private String uploadPath;
	private String uuid;
	private boolean image;
}
