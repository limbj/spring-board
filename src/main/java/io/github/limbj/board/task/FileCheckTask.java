package io.github.limbj.board.task;

import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.github.limbj.board.mapper.BoardAttachMapper;
import io.github.limbj.board.upload.UploadFileStore;

/** Managed files older than 48 hours only. Registration and cleanup share the same lock. */
@Component
public class FileCheckTask {
    private static final Logger log = LoggerFactory.getLogger(FileCheckTask.class);
    private final BoardAttachMapper mapper;
    private final UploadFileStore store;
    private final Clock clock;
    @Autowired
    public FileCheckTask(BoardAttachMapper mapper, UploadFileStore store) {
        this(mapper, store, Clock.systemDefaultZone());
    }
    FileCheckTask(BoardAttachMapper mapper, UploadFileStore store, Clock clock) {
        this.mapper = mapper; this.store = store; this.clock = clock;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void checkFiles() {
        synchronized (store) {
            if (!store.isSafeDirectory(store.getRoot())) return;
            try (Stream<Path> paths = Files.walk(store.getRoot(), 3)) {
                List<Path> folders = paths.filter(path -> store.isSafeDirectory(path)
                        && UploadFileStore.dateOfFolder(store.getRoot().relativize(path).toString()) != null).toList();
                for (Path folder : folders) cleanFolder(UploadFileStore.dateOfFolder(store.getRoot().relativize(folder).toString()));
            } catch (IOException | RuntimeException exception) {
                log.warn("Orphan cleanup stopped; storage or database could not be checked.");
            }
        }
    }

    void cleanFolder(LocalDate date) {
        synchronized (store) {
            if (date == null || !date.isBefore(LocalDate.now(clock).minusDays(1))) return;
            Path directory = store.getRoot().resolve(store.folderOf(date));
            if (!store.isSafeDirectory(directory)) return;
            Instant cutoff = clock.instant().minusMillis(UploadFileStore.PENDING_LIFETIME_MILLIS);
            try (Stream<Path> children = Files.list(directory)) {
                for (Path file : children.toList()) {
                    if (!store.isManagedFile(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                    if (!Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)) continue;
                    String uuid = UploadFileStore.uuidOf(file.getFileName().toString());
                    // UUID lookup preserves attachments regardless of legacy DB path separators.
                    if (mapper.findByUuid(uuid) != null) continue;
                    // Board transactions hold the monitor until the DB commit completes.
                    if (store.isManagedFile(file) && mapper.findByUuid(uuid) == null) Files.deleteIfExists(file);
                }
            } catch (IOException | RuntimeException exception) {
                log.warn("Orphan cleanup stopped; no unchecked files will be removed.");
            }
        }
    }
}
