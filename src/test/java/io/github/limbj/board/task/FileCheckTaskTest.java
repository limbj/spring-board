package io.github.limbj.board.task;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.UUID;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import io.github.limbj.board.domain.BoardAttachVO;
import io.github.limbj.board.support.FakeBoardAttachMapper;
import io.github.limbj.board.upload.UploadFileStore;

public class FileCheckTaskTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"),ZoneOffset.UTC);
    private UploadFileStore store;
    @Before public void setup() { store=new UploadFileStore(temp.getRoot().getPath()); }

    @Test public void keepsRegisteredLegacyPathAndFreshOrUnmanagedFiles() throws Exception {
        LocalDate day=LocalDate.of(2026,10,1); String uuid=UUID.randomUUID().toString();
        Path original=create(day,uuid+"_photo.png",true),thumb=create(day,"s_"+uuid+"_photo.png",true);
        Path orphan=create(day,UUID.randomUUID()+"_orphan.txt",true);
        Path fresh=create(day,UUID.randomUUID()+"_draft.txt",false);
        Path unmanaged=create(day,"administrator-notes.txt",true);
        FakeBoardAttachMapper mapper=new FakeBoardAttachMapper();
        BoardAttachVO saved=new BoardAttachVO(); saved.setUuid(uuid); saved.setFileName("photo.png");
        saved.setUploadPath("2026\\10\\01"); saved.setFileType(true); mapper.insert(saved);
        new FileCheckTask(mapper,store,clock).cleanFolder(day);
        assertTrue(Files.exists(original)); assertTrue(Files.exists(thumb));
        assertFalse(Files.exists(orphan)); assertTrue(Files.exists(fresh)); assertTrue(Files.exists(unmanaged));
    }
    @Test public void yesterdayFolderIsNeverDeletedEvenWithOldFileTimestamp() throws Exception {
        LocalDate yesterday=LocalDate.of(2026,10,4); Path file=create(yesterday,UUID.randomUUID()+"_draft.txt",true);
        new FileCheckTask(new FakeBoardAttachMapper(),store,clock).cleanFolder(yesterday);
        assertTrue(Files.exists(file));
    }
    @Test public void fullScanCatchesFoldersMissedDuringServerDowntime() throws Exception {
        Path old=create(LocalDate.of(2026,8,1),UUID.randomUUID()+"_old.txt",true);
        Path current=create(LocalDate.of(2026,10,5),UUID.randomUUID()+"_draft.txt",true);
        new FileCheckTask(new FakeBoardAttachMapper(),store,clock).checkFiles();
        assertFalse(Files.exists(old)); assertTrue(Files.exists(current));
    }
    @Test public void databaseFailureFailsClosedAndKeepsFile() throws Exception {
        LocalDate day=LocalDate.of(2026,10,1); Path file=create(day,UUID.randomUUID()+"_orphan.txt",true);
        FakeBoardAttachMapper mapper=new FakeBoardAttachMapper() {
            @Override public BoardAttachVO findByUuid(String uuid) { throw new IllegalStateException("test database unavailable"); }
        };
        new FileCheckTask(mapper,store,clock).cleanFolder(day);
        assertTrue(Files.exists(file));
    }
    @Test public void recheckCanPreserveNewDbReferenceAndHoldsSharedMonitor() throws Exception {
        LocalDate day=LocalDate.of(2026,10,1); String uuid=UUID.randomUUID().toString();
        Path file=create(day,uuid+"_draft.txt",true);
        FakeBoardAttachMapper mapper=new FakeBoardAttachMapper() {
            int queries;
            @Override public BoardAttachVO findByUuid(String requested) {
                assertTrue(Thread.holdsLock(store));
                if(++queries==1) return null;
                BoardAttachVO attached=new BoardAttachVO(); attached.setUuid(requested); return attached;
            }
        };
        new FileCheckTask(mapper,store,clock).cleanFolder(day);
        assertTrue(Files.exists(file));
    }
    @Test public void cleanupDoesNotFollowDirectoryLinksOutsideRoot() throws Exception {
        Path outside=temp.newFolder("external").toPath();
        Path file=Files.write(Files.createDirectories(outside.resolve("10/01")).resolve(UUID.randomUUID()+"_draft.txt"),new byte[]{1});
        try { Files.createSymbolicLink(store.getRoot().resolve("2026"),outside); }
        catch(Exception exception) { Assume.assumeNoException(exception); }
        new FileCheckTask(new FakeBoardAttachMapper(),store,clock).checkFiles();
        assertTrue(Files.exists(file));
    }
    @Test public void missingFolderIsIgnored() { new FileCheckTask(new FakeBoardAttachMapper(),store,clock).cleanFolder(LocalDate.of(2000,1,1)); }
    private Path create(LocalDate date,String name,boolean old) throws Exception {
        Path file=Files.write(Files.createDirectories(store.getRoot().resolve(store.folderOf(date))).resolve(name),new byte[]{1});
        Files.setLastModifiedTime(file,FileTime.from(old?clock.instant().minusSeconds(72*3600):clock.instant()));
        return file;
    }
}