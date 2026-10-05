package io.github.limbj.board.upload;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import io.github.limbj.board.domain.AttachFileDTO;
import io.github.limbj.board.domain.BoardAttachVO;
import io.github.limbj.board.exception.BadRequestException;

/** Private storage. Board transactions and cleanup share this instance's monitor. */
@Component
public class UploadFileStore {
    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    public static final int MAX_ATTACHMENTS = 10;
    public static final long PENDING_LIFETIME_MILLIS = 48L * 60 * 60 * 1000;
    public static final String PENDING_SESSION_ATTRIBUTE = UploadFileStore.class.getName() + ".pending";
    private static final Logger log = LoggerFactory.getLogger(UploadFileStore.class);
    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "pdf", "txt", "docx", "xlsx", "pptx");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif");
    private static final Pattern STORED_NAME = Pattern.compile(
            "^(?:s_)?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})_([^/\\\\]+)$");
    private static final Pattern INVALID_NAME = Pattern.compile("[:*?\"<>|\\p{Cntrl}]");
    private static final DateTimeFormatter FOLDER = DateTimeFormatter.ofPattern("uuuu/MM/dd").withResolverStyle(ResolverStyle.STRICT);
    private final Path root;

    public UploadFileStore(@Value("${upload.dir}") String directory) {
        root = Paths.get(directory).toAbsolutePath().normalize();
    }
    public Path getRoot() { return root; }
    public String folderOf(LocalDate date) { return date.format(FOLDER); }

    private static String canonicalFolder(String value) {
        if (value == null) return null;
        String normalized = value.replace('\\', '/');
        if (!normalized.matches("[0-9]{4}/[0-9]{2}/[0-9]{2}")) return null;
        try { return LocalDate.parse(normalized, FOLDER).format(FOLDER); }
        catch (RuntimeException exception) { return null; }
    }
    public static LocalDate dateOfFolder(String value) {
        String folder = canonicalFolder(value);
        return folder == null ? null : LocalDate.parse(folder, FOLDER);
    }

    /** HTTP and delete operations can only address UUID files in real date directories. */
    public Path resolve(String relative) {
        if (relative == null) return null;
        String canonical = relative.replace('\\', '/');
        int split = canonical.lastIndexOf('/');
        if (split < 0 || canonicalFolder(canonical.substring(0, split)) == null) return null;
        String name = canonical.substring(split + 1);
        if (uuidOf(name) == null || !validName(originalNameOf(name))) return null;
        try {
            Path file = root.resolve(canonical).normalize();
            return file.startsWith(root) && noLinks(file) ? file : null;
        } catch (InvalidPathException exception) { return null; }
    }
    /** Also catches Windows junctions by comparing existing components with their real paths. */
    private boolean noLinks(Path path) {
        try {
            Path cursor = path.getRoot();
            for (Path part : path) {
                cursor = cursor.resolve(part);
                if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                        && (Files.isSymbolicLink(cursor) || !cursor.toRealPath().equals(cursor.toAbsolutePath().normalize()))) return false;
            }
            return true;
        } catch (IOException | SecurityException exception) { return false; }
    }
    public boolean isSafeDirectory(Path path) {
        return path != null && path.toAbsolutePath().normalize().startsWith(root)
                && noLinks(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }
    public boolean isManagedFile(Path file) {
        if (file == null || !file.toAbsolutePath().normalize().startsWith(root)) return false;
        Path resolved = resolve(root.relativize(file.toAbsolutePath().normalize()).toString());
        return resolved != null && resolved.equals(file.toAbsolutePath().normalize())
                && Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS);
    }
    public boolean isAllowed(MultipartFile file) {
        String name = originalFileName(file);
        return name != null && file.getSize() > 0 && file.getSize() < MAX_FILE_SIZE && EXTENSIONS.contains(extension(name));
    }

    /** File-only helper; HTTP callers must use the session overload to obtain a submission capability. */
    public synchronized AttachFileDTO save(MultipartFile file) throws IOException {
        if (!isAllowed(file)) throw new BadRequestException("허용된 파일 형식과 크기를 확인해 주세요.");
        String folder = folderOf(LocalDate.now());
        Path directory = root.resolve(folder);
        if (!noLinks(directory)) throw new IOException("Unsafe upload directory");
        Files.createDirectories(directory);
        if (!isSafeDirectory(directory)) throw new IOException("Unsafe upload directory");
        String name = originalFileName(file), uuid = UUID.randomUUID().toString();
        Path original = directory.resolve(uuid + "_" + name), thumbnail = directory.resolve("s_" + uuid + "_" + name);
        boolean image = IMAGE_EXTENSIONS.contains(extension(name));
        try {
            Files.createFile(original);
            file.transferTo(original);
            if (Files.size(original) <= 0 || Files.size(original) >= MAX_FILE_SIZE) throw new BadRequestException("파일 크기를 확인해 주세요.");
            if (image) {
                BufferedImage decoded = readImage(original, extension(name));
                // Re-encode raster data so appended active content and metadata are not served.
                if (!ImageIO.write(decoded, "png", original.toFile())) throw new IOException("PNG encoder unavailable");
                double scale = Math.min(1d, Math.min(100d / decoded.getWidth(), 100d / decoded.getHeight()));
                int width = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
                BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = scaled.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(decoded, 0, 0, width, height, null);
                } finally { graphics.dispose(); }
                if (!ImageIO.write(scaled, "png", thumbnail.toFile())) throw new IOException("PNG encoder unavailable");
                readImage(thumbnail, null);
                if (Files.size(original) >= MAX_FILE_SIZE) throw new BadRequestException("변환된 이미지가 너무 큽니다.");
            } else validateDocument(original, extension(name));
            AttachFileDTO result = new AttachFileDTO();
            result.setUuid(uuid); result.setFileName(name); result.setUploadPath(folder); result.setImage(image);
            return result;
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(original); deleteQuietly(thumbnail);
            throw exception;
        }
    }
    public synchronized AttachFileDTO save(MultipartFile file, HttpSession session) throws IOException {
        Map<String, PendingUpload> uploads = pending(session);
        uploads.values().removeIf(UploadFileStore::expired);
        if (uploads.size() >= 20) throw new BadRequestException("먼저 사용하지 않는 업로드를 취소해 주세요.");
        AttachFileDTO result = save(file);
        try {
            uploads.put(result.getUuid(), new PendingUpload(result));
            session.setAttribute(PENDING_SESSION_ATTRIBUTE, uploads);
            return result;
        } catch (RuntimeException exception) {
            uploads.remove(result.getUuid());
            delete(new PendingUpload(result).asAttachment());
            throw exception;
        }
    }

    public synchronized List<BoardAttachVO> validateAttachments(List<BoardAttachVO> submitted,
            List<BoardAttachVO> existing, HttpSession session) {
        if (submitted == null) return new ArrayList<>();
        if (submitted.size() > MAX_ATTACHMENTS) throw new BadRequestException("첨부파일은 최대 10개입니다.");
        Map<String, BoardAttachVO> previous = new LinkedHashMap<>();
        if (existing != null) for (BoardAttachVO item : existing) {
            if (item != null && item.getUuid() != null) previous.put(item.getUuid(), item);
        }
        Map<String, PendingUpload> uploads = pending(session);
        uploads.values().removeIf(UploadFileStore::expired);
        Set<String> seen = new HashSet<>();
        List<BoardAttachVO> accepted = new ArrayList<>();
        for (BoardAttachVO requested : submitted) {
            if (requested == null || requested.getUuid() == null || !seen.add(requested.getUuid())) {
                throw new BadRequestException("잘못되거나 중복된 첨부파일입니다.");
            }
            BoardAttachVO source = previous.get(requested.getUuid());
            if (source == null) {
                PendingUpload issued = uploads.get(requested.getUuid());
                if (issued == null) throw new BadRequestException("이 화면에서 업로드한 파일만 첨부할 수 있습니다.");
                source = issued.asAttachment();
            }
            if (!sameMetadata(requested, source)) throw new BadRequestException("첨부파일 정보가 변경되었습니다.");
            BoardAttachVO canonical = copy(source);
            canonical.setUploadPath(canonicalFolder(source.getUploadPath()));
            if (!isManagedFile(pathOf(canonical, false))
                    || (canonical.isFileType() && !isManagedFile(pathOf(canonical, true)))) {
                throw new BadRequestException("첨부파일을 찾을 수 없습니다. 다시 업로드해 주세요.");
            }
            accepted.add(canonical);
        }
        return accepted;
    }
    public synchronized void releasePending(List<BoardAttachVO> attached, HttpSession session) {
        if (session == null || attached == null) return;
        try {
            Map<String, PendingUpload> uploads = pending(session);
            for (BoardAttachVO item : attached) if (item != null) uploads.remove(item.getUuid());
            session.setAttribute(PENDING_SESSION_ATTRIBUTE, uploads);
        } catch (IllegalStateException ignored) {
            // The committed board remains valid if its HTTP session ended concurrently.
        }
    }
    public synchronized boolean ownsPending(Path requested, HttpSession session) {
        if (requested == null || session == null) return false;
        PendingUpload upload = pending(session).get(uuidOf(requested.getFileName().toString()));
        if (upload == null || expired(upload)) return false;
        BoardAttachVO issued = upload.asAttachment();
        return requested.equals(pathOf(issued, false)) || (issued.isFileType() && requested.equals(pathOf(issued, true)));
    }
    public synchronized boolean deleteUploaded(Path requested, HttpSession session) throws IOException {
        if (!ownsPending(requested, session)) return false;
        String uuid = uuidOf(requested.getFileName().toString());
        boolean removed = deletePair(pending(session).get(uuid).asAttachment());
        pending(session).remove(uuid);
        return removed;
    }
    public synchronized void rollbackUploads(List<AttachFileDTO> uploads, HttpSession session) {
        if (uploads == null) return;
        Map<String, PendingUpload> pendingUploads = null;
        if (session != null) {
            try { pendingUploads = pending(session); }
            catch (IllegalStateException ignored) { /* File rollback still proceeds for an ended session. */ }
        }
        for (AttachFileDTO item : uploads) {
            if (item == null) continue;
            delete(new PendingUpload(item).asAttachment());
            if (pendingUploads != null) pendingUploads.remove(item.getUuid());
        }
    }
    public synchronized void delete(BoardAttachVO attachment) {
        try { deletePair(attachment); }
        catch (IOException exception) { log.warn("Attachment cleanup failed; an orphan may need retry."); }
    }
    public synchronized void deleteAll(List<BoardAttachVO> attachments) {
        if (attachments != null) for (BoardAttachVO item : attachments) delete(item);
    }
    /** Compatibility helper for trusted callers; unmanaged files and links remain inaccessible. */
    public synchronized boolean deleteUploaded(Path file, boolean image) throws IOException {
        if (!isManagedFile(file)) return false;
        String name = file.getFileName().toString();
        String stored = name.startsWith("s_") ? name.substring(2) : name;
        Path original = resolve(root.relativize(file.resolveSibling(stored)).toString());
        Path thumbnail = resolve(root.relativize(file.resolveSibling("s_" + stored)).toString());
        return deleteIfManaged(original) | deleteIfManaged(thumbnail);
    }
    public Path pathOf(BoardAttachVO item, boolean thumbnail) {
        if (item == null || item.getUploadPath() == null || item.getUuid() == null || !validName(item.getFileName())) return null;
        return resolve(item.getUploadPath() + "/" + (thumbnail ? "s_" : "") + item.getUuid() + "_" + item.getFileName());
    }
    public byte[] imageBytes(Path file) throws IOException {
        if (!isManagedFile(file) || Files.size(file) >= MAX_FILE_SIZE
                || !IMAGE_EXTENSIONS.contains(extension(originalNameOf(file.getFileName().toString())))) {
            throw new BadRequestException("이미지만 표시할 수 있습니다.");
        }
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        if (!ImageIO.write(readImage(file, null), "png", output)) throw new IOException("PNG encoder unavailable");
        return output.toByteArray();
    }
    private boolean deletePair(BoardAttachVO item) throws IOException {
        return deleteIfManaged(pathOf(item, false)) | deleteIfManaged(pathOf(item, true));
    }
    private boolean deleteIfManaged(Path file) throws IOException { return isManagedFile(file) && Files.deleteIfExists(file); }
    private void deleteQuietly(Path file) {
        try { deleteIfManaged(file); } catch (IOException exception) { log.warn("Incomplete upload cleanup failed."); }
    }
    @SuppressWarnings("unchecked")
    private Map<String, PendingUpload> pending(HttpSession session) {
        if (session == null) throw new BadRequestException("업로드 세션을 확인해 주세요.");
        Object existing = session.getAttribute(PENDING_SESSION_ATTRIBUTE);
        if (existing instanceof Map<?, ?>) return (Map<String, PendingUpload>) existing;
        Map<String, PendingUpload> result = new LinkedHashMap<>();
        session.setAttribute(PENDING_SESSION_ATTRIBUTE, result);
        return result;
    }
    private static boolean expired(PendingUpload item) {
        return Instant.now().toEpochMilli() - item.createdAt >= PENDING_LIFETIME_MILLIS;
    }
    private static boolean sameMetadata(BoardAttachVO left, BoardAttachVO right) {
        return Objects.equals(left.getUuid(), right.getUuid()) && Objects.equals(left.getFileName(), right.getFileName())
                && Objects.equals(canonicalFolder(left.getUploadPath()), canonicalFolder(right.getUploadPath()))
                && canonicalFolder(left.getUploadPath()) != null && left.isFileType() == right.isFileType();
    }
    private static BoardAttachVO copy(BoardAttachVO source) {
        BoardAttachVO result = new BoardAttachVO();
        result.setUuid(source.getUuid()); result.setFileName(source.getFileName()); result.setUploadPath(source.getUploadPath());
        result.setFileType(source.isFileType()); result.setBno(source.getBno());
        return result;
    }
    private static class PendingUpload implements Serializable {
        private static final long serialVersionUID = 1L;
        final String uuid, fileName, uploadPath;
        final boolean image;
        final long createdAt;
        PendingUpload(AttachFileDTO item) {
            uuid = item.getUuid(); fileName = item.getFileName(); uploadPath = item.getUploadPath();
            image = item.isImage(); createdAt = Instant.now().toEpochMilli();
        }
        BoardAttachVO asAttachment() {
            BoardAttachVO item = new BoardAttachVO();
            item.setUuid(uuid); item.setFileName(fileName); item.setUploadPath(uploadPath); item.setFileType(image);
            return item;
        }
    }
    public static String uuidOf(String storedName) {
        if (storedName == null) return null;
        Matcher matcher = STORED_NAME.matcher(storedName);
        return matcher.matches() ? matcher.group(1) : null;
    }
    public static String originalNameOf(String storedName) {
        if (storedName == null) return null;
        Matcher matcher = STORED_NAME.matcher(storedName);
        return matcher.matches() ? matcher.group(2) : storedName;
    }
    private static String originalFileName(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) return null;
        String name = file.getOriginalFilename().replace('\\', '/');
        name = INVALID_NAME.matcher(name.substring(name.lastIndexOf('/') + 1)).replaceAll("_").trim();
        while (name.endsWith(".")) name = name.substring(0, name.length() - 1);
        return validName(name) ? name : null;
    }
    private static boolean validName(String name) {
        return name != null && !name.isBlank() && !".".equals(name) && !"..".equals(name)
                && !name.contains("/") && !name.contains("\\") && !INVALID_NAME.matcher(name).find()
                && name.getBytes(StandardCharsets.UTF_8).length <= 180;
    }
    private static String extension(String name) {
        int index = name == null ? -1 : name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }
    private static BufferedImage readImage(Path file, String expectedExtension) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) throw new BadRequestException("올바른 이미지 파일이 아닙니다.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BadRequestException("올바른 이미지 파일이 아닙니다.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg", "gif").contains(format)) throw new BadRequestException("허용되지 않은 이미지 형식입니다.");
                if (expectedExtension != null && !expectedExtension.equals(format)
                        && !(Set.of("jpg", "jpeg").contains(expectedExtension) && Set.of("jpg", "jpeg").contains(format))) {
                    throw new BadRequestException("이미지 내용과 확장자가 일치하지 않습니다.");
                }
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 12000 || height > 12000 || (long) width * height > 20_000_000L) {
                    throw new BadRequestException("이미지 해상도가 너무 큽니다.");
                }
                BufferedImage result = reader.read(0);
                if (result == null) throw new BadRequestException("올바른 이미지 파일이 아닙니다.");
                return result;
            } finally { reader.dispose(); }
        } catch (javax.imageio.IIOException exception) { throw new BadRequestException("이미지 파일을 읽을 수 없습니다."); }
    }
    private static void validateDocument(Path file, String extension) throws IOException {
        if ("txt".equals(extension)) {
            byte[] bytes = Files.readAllBytes(file);
            try { StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)); }
            catch (java.nio.charset.CharacterCodingException exception) { throw new BadRequestException("텍스트 파일은 UTF-8 형식이어야 합니다."); }
            for (byte value : bytes) if (value == 0) throw new BadRequestException("올바른 텍스트 파일이 아닙니다.");
        } else if ("pdf".equals(extension)) {
            try (java.io.InputStream input = Files.newInputStream(file)) {
                if (!Arrays.equals(input.readNBytes(5), "%PDF-".getBytes(StandardCharsets.US_ASCII))) throw new BadRequestException("올바른 PDF 파일이 아닙니다.");
            }
        } else {
            String required = switch (extension) {
                case "docx" -> "word/document.xml";
                case "xlsx" -> "xl/workbook.xml";
                case "pptx" -> "ppt/presentation.xml";
                default -> throw new BadRequestException("허용되지 않은 파일입니다.");
            };
            try (ZipFile zip = new ZipFile(file.toFile())) {
                if (zip.getEntry("[Content_Types].xml") == null || zip.getEntry(required) == null) throw new BadRequestException("올바른 Office 파일이 아닙니다.");
                long expanded = 0; int count = 0;
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    expanded += Math.max(0, entry.getSize());
                    if (++count > 2000 || expanded > 100L * 1024 * 1024 || entry.getName().toLowerCase(Locale.ROOT).contains("vbaproject")) {
                        throw new BadRequestException("복잡하거나 매크로가 포함된 Office 파일은 허용되지 않습니다.");
                    }
                }
            } catch (ZipException exception) { throw new BadRequestException("올바른 Office 파일이 아닙니다."); }
        }
    }
}
