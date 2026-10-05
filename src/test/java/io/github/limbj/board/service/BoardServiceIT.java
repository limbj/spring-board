package io.github.limbj.board.service;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import io.github.limbj.board.domain.BoardAttachVO;
import io.github.limbj.board.domain.BoardVO;
import io.github.limbj.board.domain.Criteria;
import io.github.limbj.board.domain.ReplyVO;
import io.github.limbj.board.exception.BadRequestException;
import io.github.limbj.board.exception.NotFoundException;
import local.support.ServiceTestDatabase;

/** Real Spring transactions and MyBatis SQL against a private, in-memory H2 database. */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = ServiceTestDatabase.class)
public class BoardServiceIT {
    @Autowired private BoardService boards;
    @Autowired private ReplyService replies;
    @Autowired private DataSource source;
    private JdbcTemplate jdbc;

    @Before public void clearDatabase() {
        jdbc = new JdbcTemplate(source);
        jdbc.update("DELETE FROM tbl_reply");
        jdbc.update("DELETE FROM tbl_attach");
        jdbc.update("DELETE FROM tbl_board");
    }

    @Test public void registersBoardAttachmentsAndCharBooleanFlag() {
        BoardVO board = board("First");
        BoardAttachVO attachment = attachment(UUID.randomUUID().toString(), true);
        board.setAttachList(List.of(attachment));
        boards.register(board);
        assertNotNull(board.getBno());
        assertEquals("First", boards.get(board.getBno()).getTitle());
        assertTrue(boards.getAttachList(board.getBno()).get(0).isFileType());
        assertEquals("1", jdbc.queryForObject("SELECT filetype FROM tbl_attach WHERE uuid = ?", String.class, attachment.getUuid()));
        assertTrue(boards.isAttached(attachment.getUuid()));
        assertEquals(1, replies.register(reply(board.getBno(), "comment")));
        boards.remove(board.getBno());
        assertNull(boards.get(board.getBno()));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM tbl_reply", Integer.class));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM tbl_attach", Integer.class));
    }

    @Test public void searchAndPagingHaveStableOrderAndClampLastPage() {
        List<Long> numbers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            BoardVO board = board("unique-hit-" + i);
            boards.register(board);
            numbers.add(board.getBno());
        }
        boards.register(board("unrelated"));
        Criteria criteria = new Criteria(999, 10);
        criteria.setType("T");
        criteria.setKeyword("unique-hit");
        List<BoardVO> last = boards.getList(criteria);
        assertEquals(12, boards.getTotal(criteria));
        assertEquals(2, criteria.getPageNum());
        assertEquals(2, last.size());
        assertEquals(numbers.get(1), last.get(0).getBno());
        assertEquals(numbers.get(0), last.get(1).getBno());
        criteria.setPageNum(1);
        List<BoardVO> first = boards.getList(criteria);
        assertEquals(numbers.get(11), first.get(0).getBno());
        assertEquals(10, first.size());
    }

    @Test public void missingAttachmentsPreserveRowsAndDuplicateInsertRollsBack() {
        BoardVO original = board("Original");
        original.setAttachList(List.of(attachment(UUID.randomUUID().toString(), false)));
        boards.register(original);
        BoardVO update = board("Updated");
        update.setBno(original.getBno());
        boards.modify(update);
        assertEquals(1, boards.getAttachList(original.getBno()).size());
        BoardVO rejected = board("Rejected");
        String uuid = UUID.randomUUID().toString();
        rejected.setAttachList(List.of(attachment(uuid, false), attachment(uuid, false)));
        assertThrows(RuntimeException.class, () -> boards.register(rejected));
        assertNull(boards.get(rejected.getBno()));
        assertEquals(1, boards.getTotal(new Criteria()));
    }

    @Test public void directServicesEnforceLengthsIdsAndParentExistence() {
        assertThrows(BadRequestException.class, () -> boards.register(board("x".repeat(201))));
        BoardVO content = board("title");
        content.setContent("x".repeat(20001));
        assertThrows(BadRequestException.class, () -> boards.register(content));
        assertThrows(BadRequestException.class, () -> boards.get(0L));
        assertThrows(NotFoundException.class, () -> boards.remove(999L));
        assertThrows(NotFoundException.class, () -> replies.register(reply(999L, "valid")));
        assertThrows(BadRequestException.class, () -> replies.register(reply(1L, "x".repeat(1001))));
        assertThrows(BadRequestException.class, () -> replies.remove(-1L));
        assertEquals(0, boards.getTotal(new Criteria()));
    }

    @Test public void concurrentDeletionDecrementsCountExactlyOnce() throws Exception {
        BoardVO board = board("Shared");
        boards.register(board);
        ReplyVO reply = reply(board.getBno(), "only comment");
        replies.register(reply);
        assertNotNull(reply.getRno());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> { start.await(); return replies.remove(reply.getRno()); });
            Future<Integer> second = executor.submit(() -> { start.await(); return replies.remove(reply.getRno()); });
            start.countDown();
            assertEquals(1, first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS));
            assertEquals(0, boards.get(board.getBno()).getReplyCnt());
            assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM tbl_reply WHERE bno = ?", Integer.class, board.getBno()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test public void concurrentReplyCreationKeepsCountAndAscendingOrder() throws Exception {
        BoardVO board = board("Shared");
        boards.register(board);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                final int index = i;
                tasks.add(executor.submit(() -> replies.register(reply(board.getBno(), "comment-" + index))));
            }
            for (Future<?> task : tasks) task.get(20, TimeUnit.SECONDS);
            assertEquals(12, boards.get(board.getBno()).getReplyCnt());
            var page = replies.getListPage(new Criteria(999, 10), board.getBno());
            assertEquals(2, page.getPageNum());
            assertEquals(2, page.getList().size());
            assertTrue(page.getList().get(0).getRno() < page.getList().get(1).getRno());
            assertEquals(12, page.getReplyCnt());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test public void boardRemovalAndReplyCreationUseSameParentLock() throws Exception {
        BoardVO board = board("Shared");
        boards.register(board);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> create = executor.submit(() -> {
                start.await();
                try { replies.register(reply(board.getBno(), "racing comment")); }
                catch (NotFoundException expected) { /* A board removed first cannot receive replies. */ }
                return null;
            });
            Future<?> remove = executor.submit(() -> { start.await(); boards.remove(board.getBno()); return null; });
            start.countDown();
            create.get(20, TimeUnit.SECONDS);
            remove.get(20, TimeUnit.SECONDS);
            assertNull(boards.get(board.getBno()));
            assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM tbl_reply", Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }

    private BoardVO board(String title) {
        BoardVO board = new BoardVO();
        board.setTitle(title);
        board.setContent("content");
        board.setWriter("tester");
        return board;
    }

    private ReplyVO reply(Long bno, String content) {
        ReplyVO reply = new ReplyVO();
        reply.setBno(bno);
        reply.setReply(content);
        reply.setReplyer("tester");
        return reply;
    }

    private BoardAttachVO attachment(String uuid, boolean image) {
        BoardAttachVO attachment = new BoardAttachVO();
        attachment.setUuid(uuid);
        attachment.setFileName("file.txt");
        attachment.setUploadPath("2026/10/05");
        attachment.setFileType(image);
        return attachment;
    }

}
