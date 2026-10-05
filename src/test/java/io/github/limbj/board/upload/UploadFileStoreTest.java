package io.github.limbj.board.upload;

import static org.junit.Assert.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import io.github.limbj.board.domain.*;
import io.github.limbj.board.exception.BadRequestException;

public class UploadFileStoreTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private UploadFileStore store;
    private static final String UUID = "11111111-2222-3333-4444-555555555555";
    @Before public void setup() { store = new UploadFileStore(temp.getRoot().getPath()); }

    @Test public void resolvesOnlyManagedDatePathsAndCanonicalizesLegacySeparators() {
        assertNull(store.resolve("../secret.txt"));
        assertNull(store.resolve("2026/10/05/../../secret.txt"));
        assertNull(store.resolve("2026/02/30/" + UUID + "_a.txt"));
        assertNull(store.resolve("2026/10/05/plain.txt"));
        assertNull(store.resolve("")); assertNull(store.resolve(null));
        assertEquals(store.getRoot().resolve("2026/10/05/" + UUID + "_a.txt"),
                store.resolve("2026\\10\\05\\" + UUID + "_a.txt"));
    }
    @Test public void dateFolderAlwaysUsesForwardSlashes() {
        assertEquals("2026/10/05", store.folderOf(LocalDate.of(2026,10,5)));
    }
    @Test public void savesNormalizedFilenameAndExactUtf8Text() throws Exception {
        AttachFileDTO dto = store.save(text("fake\\browser\\보고서 1.txt", "hello"));
        assertEquals("보고서 1.txt", dto.getFileName());
        assertFalse(dto.isImage());
        assertEquals("hello", Files.readString(path(dto, false)));
    }
    @Test public void replacesInvalidFilenameCharacters() throws Exception {
        AttachFileDTO dto = store.save(text("what?: \"plan\".txt", "hello"));
        assertEquals("what__ _plan_.txt", dto.getFileName());
    }
    @Test public void rejectsActiveExtensionsEmptyAndLimitSizedFiles() {
        assertFalse(store.isAllowed(text("attack.html", "x")));
        assertFalse(store.isAllowed(text("attack.svg", "x")));
        assertFalse(store.isAllowed(text("run.EXE", "x")));
        assertFalse(store.isAllowed(text("data.zip", "x")));
        assertFalse(store.isAllowed(text("empty.txt", "")));
        assertFalse(store.isAllowed(new MockMultipartFile("f", "big.txt", null, new byte[(int)UploadFileStore.MAX_FILE_SIZE])));
        assertTrue(store.isAllowed(text("ok.txt", "hello")));
    }
    @Test public void createsAndVerifiesRasterOriginalAndPngThumbnail() throws Exception {
        AttachFileDTO dto = store.save(new MockMultipartFile("f","photo.png","image/png",png(200,100)));
        assertTrue(dto.isImage());
        BufferedImage thumbnail = ImageIO.read(path(dto,true).toFile());
        assertEquals(100,thumbnail.getWidth()); assertEquals(50,thumbnail.getHeight());
        assertNotNull(ImageIO.read(path(dto,false).toFile()));
        store.delete(asAttachment(dto));
        assertFalse(Files.exists(path(dto,false))); assertFalse(Files.exists(path(dto,true)));
    }
    @Test public void rejectsFakeImageAndMismatchWithoutLeavingFiles() throws Exception {
        assertThrows(BadRequestException.class, () -> store.save(text("fake.png", "<html>bad</html>")));
        assertThrows(BadRequestException.class, () -> store.save(new MockMultipartFile("f","fake.jpg","image/jpeg",png(1,1))));
        assertEquals(0, files());
    }
    @Test public void rejectsLargeImageDimensionsBeforeDecodingPixels() throws Exception {
        byte[] image = png(1,1);
        java.nio.ByteBuffer.wrap(image,16,8).putInt(20000).putInt(20000);
        CRC32 crc = new CRC32(); crc.update(image,12,17);
        java.nio.ByteBuffer.wrap(image,29,4).putInt((int)crc.getValue());
        assertThrows(BadRequestException.class, () -> store.save(new MockMultipartFile("f","large.png","image/png",image)));
        assertEquals(0, files());
    }
    @Test public void documentExtensionMustMatchBasicContents() throws Exception {
        assertThrows(BadRequestException.class, () -> store.save(text("fake.pdf","not pdf")));
        assertThrows(BadRequestException.class, () -> store.save(text("fake.docx","not zip")));
        assertNotNull(store.save(text("report.pdf","%PDF-1.7\nsample")));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : List.of("[Content_Types].xml", "word/document.xml")) {
                zip.putNextEntry(new ZipEntry(name)); zip.write("<xml/>".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        assertNotNull(store.save(new MockMultipartFile("f", "report.docx", "application/zip", bytes.toByteArray())));
        assertThrows(BadRequestException.class, () -> store.save(new MockMultipartFile("f", "wrong.xlsx", "application/zip", bytes.toByteArray())));
    }
    @Test public void failedTransferRemovesPartialOriginal() throws Exception {
        MockMultipartFile broken = new MockMultipartFile("f","broken.txt","text/plain",new byte[]{1}) {
            @Override public void transferTo(Path path) throws IOException {
                Files.writeString(path,"partial"); throw new IOException("simulated failure");
            }
        };
        assertThrows(IOException.class, () -> store.save(broken));
        assertEquals(0,files());
    }
    @Test public void failedSessionRegistrationRemovesSavedFile() throws Exception {
        MockHttpSession invalidated = new MockHttpSession() {
            private int writes;
            @Override public void setAttribute(String name, Object value) {
                if (++writes == 2) throw new IllegalStateException("simulated invalidated session");
                super.setAttribute(name, value);
            }
        };
        assertThrows(IllegalStateException.class, () -> store.save(text("draft.txt", "hello"), invalidated));
        assertEquals(0, files());
    }
    @Test public void batchRollbackStillRemovesEveryFileAfterSessionEnds() throws Exception {
        MockHttpSession owner = new MockHttpSession();
        AttachFileDTO first = store.save(text("first.txt", "hello"), owner);
        AttachFileDTO second = store.save(text("second.txt", "hello"), owner);
        owner.invalidate();
        store.rollbackUploads(List.of(first, second), owner);
        assertEquals(0, files());
        store.releasePending(List.of(asAttachment(first)), owner);
    }
    @Test public void onlyIssuingSessionCanSubmitAndMetadataIsImmutable() throws Exception {
        MockHttpSession owner = new MockHttpSession();
        AttachFileDTO issued = store.save(text("a.txt","hello"),owner);
        BoardAttachVO requested = asAttachment(issued); requested.setBno(999L);
        List<BoardAttachVO> accepted = store.validateAttachments(List.of(requested),List.of(),owner);
        assertNull(accepted.get(0).getBno());
        assertThrows(BadRequestException.class, () -> store.validateAttachments(List.of(requested),List.of(),new MockHttpSession()));
        requested.setFileName("forged.txt");
        assertThrows(BadRequestException.class, () -> store.validateAttachments(List.of(requested),List.of(),owner));
        issued.setFileName("mutated response.txt");
        BoardAttachVO good = accepted.get(0);
        assertEquals("a.txt",store.validateAttachments(List.of(good),List.of(),owner).get(0).getFileName());
        store.releasePending(List.of(good),owner);
        assertThrows(BadRequestException.class, () -> store.validateAttachments(List.of(good),List.of(),owner));
    }
    @Test public void existingLegacyMetadataIsCanonicalizedAndRetainedWithoutMutatingSource() throws Exception {
        AttachFileDTO dto = store.save(text("old.txt","hello"));
        BoardAttachVO existing = asAttachment(dto); existing.setBno(1L);
        existing.setUploadPath(dto.getUploadPath().replace('/','\\'));
        BoardAttachVO accepted = store.validateAttachments(List.of(existing),List.of(existing),new MockHttpSession()).get(0);
        assertEquals(dto.getUploadPath(),accepted.getUploadPath());
        assertEquals(Long.valueOf(1),accepted.getBno());
        assertTrue(existing.getUploadPath().contains("\\"));
    }
    @Test public void duplicateAndTooManyAttachmentsAreRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        BoardAttachVO item = asAttachment(store.save(text("a.txt","hello"),session));
        assertThrows(BadRequestException.class, () -> store.validateAttachments(List.of(item,item),List.of(),session));
        assertThrows(BadRequestException.class, () -> store.validateAttachments(Collections.nCopies(11,item),List.of(),session));
    }
    @Test public void linksCannotEscapeUploadStorage() throws Exception {
        Path outside = temp.newFolder("outside").toPath();
        Path externalFile = Files.write(Files.createDirectories(outside.resolve("10/05")).resolve(UUID+"_a.txt"),new byte[]{1});
        try { Files.createSymbolicLink(store.getRoot().resolve("2026"),outside); }
        catch (IOException | UnsupportedOperationException | SecurityException exception) { Assume.assumeNoException(exception); }
        assertNull(store.resolve("2026/10/05/"+UUID+"_a.txt"));
        assertTrue(Files.exists(externalFile));
    }
    @Test public void storedNamesSplitUuidAndOriginalFilename() {
        assertEquals(UUID,UploadFileStore.uuidOf("s_"+UUID+"_my_file.png"));
        assertEquals("my_file.png",UploadFileStore.originalNameOf(UUID+"_my_file.png"));
        assertNull(UploadFileStore.uuidOf("plain.png"));
    }
    public static byte[] png(int width,int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"png",out);
        return out.toByteArray();
    }
    public static BoardAttachVO asAttachment(AttachFileDTO dto) {
        BoardAttachVO item = new BoardAttachVO();
        item.setUuid(dto.getUuid()); item.setFileName(dto.getFileName()); item.setUploadPath(dto.getUploadPath()); item.setFileType(dto.isImage());
        return item;
    }
    private static MockMultipartFile text(String name,String value) {
        return new MockMultipartFile("f",name,"text/plain",value.getBytes(StandardCharsets.UTF_8));
    }
    private Path path(AttachFileDTO dto,boolean thumbnail) { return store.pathOf(asAttachment(dto),thumbnail); }
    private long files() throws IOException {
        try(var files=Files.walk(store.getRoot())) { return files.filter(Files::isRegularFile).count(); }
    }
}
