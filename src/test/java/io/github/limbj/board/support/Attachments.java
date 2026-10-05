package io.github.limbj.board.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.limbj.board.domain.BoardAttachVO;
import io.github.limbj.board.upload.UploadFileStore;

/** 테스트용 첨부파일 만들기 */
public final class Attachments {

	private Attachments() {
	}

	public static BoardAttachVO attach(String uuid, String fileName, String uploadPath, boolean image) {
		BoardAttachVO attach = new BoardAttachVO();
		attach.setUuid(uuid);
		attach.setFileName(fileName);
		attach.setUploadPath(uploadPath);
		attach.setFileType(image);
		return attach;
	}

	/** 업로드 폴더에 실제 파일을 만들고 경로를 돌려준다 */
	public static Path createFile(UploadFileStore store, String folder, String storedName) throws IOException {
		Path dir = Files.createDirectories(store.getRoot().resolve(folder));
		return Files.write(dir.resolve(storedName), new byte[] { 1, 2, 3 });
	}
}
