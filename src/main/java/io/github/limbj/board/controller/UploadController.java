package io.github.limbj.board.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.github.limbj.board.domain.AttachFileDTO;
import io.github.limbj.board.exception.BadRequestException;
import io.github.limbj.board.service.BoardService;
import io.github.limbj.board.upload.UploadFileStore;

@Controller
public class UploadController {
    private static final Logger log = LoggerFactory.getLogger(UploadController.class);
    private final UploadFileStore fileStore;
    private final BoardService boardService;
    public UploadController(UploadFileStore fileStore, BoardService boardService) {
        this.fileStore = fileStore; this.boardService = boardService;
    }

    @PostMapping(value = "/uploadAjaxAction", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<AttachFileDTO>> upload(@RequestParam("uploadFile") MultipartFile[] files, HttpSession session) {
        if (files == null || files.length == 0 || files.length > UploadFileStore.MAX_ATTACHMENTS) return ResponseEntity.badRequest().build();
        for (MultipartFile file : files) if (!fileStore.isAllowed(file)) return ResponseEntity.badRequest().build();
        synchronized (fileStore) {
            List<AttachFileDTO> saved = new ArrayList<>();
            try {
                for (MultipartFile file : files) saved.add(fileStore.save(file, session));
                return ResponseEntity.ok(saved);
            } catch (BadRequestException exception) {
                fileStore.rollbackUploads(saved, session);
                return ResponseEntity.badRequest().build();
            } catch (IOException | RuntimeException exception) {
                fileStore.rollbackUploads(saved, session);
                log.error("Upload storage failed; request batch was rolled back.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
    }

    @GetMapping("/display")
    @ResponseBody
    public ResponseEntity<byte[]> display(@RequestParam("fileName") String name) throws IOException {
        synchronized (fileStore) {
            Path file = fileStore.resolve(name);
            if (file == null) return ResponseEntity.badRequest().build();
            if (!fileStore.isManagedFile(file)) return ResponseEntity.notFound().build();
            try {
                return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header("X-Content-Type-Options", "nosniff")
                        .body(fileStore.imageBytes(file));
            } catch (BadRequestException exception) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
            }
        }
    }

    @GetMapping(value = "/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseBody
    public ResponseEntity<Resource> download(@RequestParam("fileName") String name) {
        synchronized (fileStore) {
            Path file = fileStore.resolve(name);
            if (file == null) return ResponseEntity.badRequest().build();
            if (!fileStore.isManagedFile(file)) return ResponseEntity.notFound().build();
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(UploadFileStore.originalNameOf(file.getFileName().toString())))
                    .header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(file));
        }
    }

    @PostMapping("/deleteFile")
    @ResponseBody
    public ResponseEntity<String> deleteFile(@RequestParam("fileName") String name,
            @RequestParam(value = "type", required = false) String ignoredType, HttpSession session) throws IOException {
        synchronized (fileStore) {
            Path file = fileStore.resolve(name);
            String uuid = file == null ? null : UploadFileStore.uuidOf(file.getFileName().toString());
            if (uuid == null) return ResponseEntity.badRequest().build();
            if (boardService.isAttached(uuid) || !fileStore.ownsPending(file, session)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            return fileStore.deleteUploaded(file, session) ? ResponseEntity.ok("deleted") : ResponseEntity.notFound().build();
        }
    }

    static String attachmentHeader(String name) {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A");
        return "attachment; filename*=UTF-8''" + encoded;
    }
}