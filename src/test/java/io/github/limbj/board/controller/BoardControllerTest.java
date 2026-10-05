package io.github.limbj.board.controller;

import static io.github.limbj.board.support.Attachments.attach;
import static io.github.limbj.board.support.Attachments.createFile;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import io.github.limbj.board.domain.AttachFileDTO;
import io.github.limbj.board.domain.BoardAttachVO;
import io.github.limbj.board.domain.BoardVO;
import io.github.limbj.board.domain.PageDTO;
import io.github.limbj.board.support.FakeBoardService;
import io.github.limbj.board.upload.UploadFileStore;

public class BoardControllerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private FakeBoardService service;
    private UploadFileStore store;
    private MockMvc mvc;

    @Before public void setUp() {
        service = new FakeBoardService();
        store = new UploadFileStore(temp.getRoot().getPath());
        InternalResourceViewResolver resolver = new InternalResourceViewResolver();
        resolver.setPrefix("/WEB-INF/views/");
        resolver.setSuffix(".jsp");
        mvc = MockMvcBuilders.standaloneSetup(new BoardController(service, store)).setViewResolvers(resolver).build();
    }

    @Test public void listShowsFilteredLastPageInNewestOrder() throws Exception {
        for (int i = 0; i < 12; i++) service.addBoard("hit " + i);
        for (int i = 0; i < 20; i++) service.addBoard("other " + i);
        MvcResult result = mvc.perform(get("/board/list").param("pageNum", "999").param("amount", "10")
                .param("type", "T").param("keyword", " hit "))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/WEB-INF/views/board/list.jsp")).andReturn();
        @SuppressWarnings("unchecked")
        List<BoardVO> boards = (List<BoardVO>) result.getModelAndView().getModel().get("list");
        PageDTO page = (PageDTO) result.getModelAndView().getModel().get("pageMaker");
        assertEquals(2, boards.size());
        assertEquals(2L, boards.get(0).getBno().longValue());
        assertEquals(1L, boards.get(1).getBno().longValue());
        assertEquals(12, page.getTotal());
        assertEquals(2, page.getCri().getPageNum());
    }

    @Test public void registerAcceptsOnlyAnUploadFromTheSameSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        AttachFileDTO uploaded = store.save(new MockMultipartFile("uploadFile", "a.txt", "text/plain",
                new byte[] { 1, 2, 3 }), session);
        mvc.perform(post("/board/register").session(session)
                .param("title", "제목").param("content", "내용").param("writer", "user00")
                .param("attachList[0].uuid", uploaded.getUuid()).param("attachList[0].fileName", uploaded.getFileName())
                .param("attachList[0].uploadPath", uploaded.getUploadPath()).param("attachList[0].fileType", "false"))
                .andExpect(redirectedUrl("/board/list")).andExpect(flash().attribute("result", 1L));
        assertEquals(1, service.getAttachList(1L).size());
        assertTrue(Files.exists(fileOf(service.getAttachList(1L).get(0))));
    }

    @Test public void forgedAttachmentIsRejectedWithoutRegisteringBoard() throws Exception {
        mvc.perform(post("/board/register").param("title", "제목").param("content", "내용").param("writer", "user00")
                .param("attachList[0].uuid", UUID.randomUUID().toString()).param("attachList[0].fileName", "forged.txt")
                .param("attachList[0].uploadPath", "2026/01/01").param("attachList[0].fileType", "false"))
                .andExpect(status().isBadRequest());
        assertNull(service.lastRegistered);
    }

    @Test public void blankAndOverlongFieldsAreRejected() throws Exception {
        String[][] values = {
                { "  ", "내용", "writer" }, { "x".repeat(201), "내용", "writer" },
                { "title", "x".repeat(20001), "writer" }, { "title", "body", "x".repeat(51) },
                { "title", "\u00a0", "writer" }
        };
        for (String[] value : values) {
            mvc.perform(post("/board/register").param("title", value[0]).param("content", value[1]).param("writer", value[2]))
                    .andExpect(status().isBadRequest());
        }
        assertNull(service.lastRegistered);
    }

    @Test public void invalidOrUnknownBoardNumbersHaveExplicitStatus() throws Exception {
        mvc.perform(get("/board/get").param("bno", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/board/get").param("bno", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/board/get").param("bno", "999")).andExpect(status().isNotFound());
        mvc.perform(post("/board/modify").param("title", "title").param("content", "body"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/board/modify").param("bno", "999").param("title", "title").param("content", "body"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/board/remove").param("bno", "999")).andExpect(status().isNotFound());
    }

    @Test public void missingAttachmentParametersPreserveExistingFiles() throws Exception {
        BoardVO board = service.addBoard("Original");
        BoardAttachVO saved = savedAttachment(board.getBno(), "keep.txt");
        mvc.perform(post("/board/modify").param("bno", board.getBno().toString()).param("title", "Updated").param("content", "body"))
                .andExpect(status().is3xxRedirection());
        assertEquals(1, service.getAttachList(board.getBno()).size());
        assertTrue(Files.exists(fileOf(saved)));
        assertEquals("tester", service.get(board.getBno()).getWriter());
    }

    @Test public void explicitlyEmptyAttachmentListDeletesAllFiles() throws Exception {
        BoardVO board = service.addBoard("Original");
        BoardAttachVO saved = savedAttachment(board.getBno(), "remove.txt");
        mvc.perform(post("/board/modify").param("bno", board.getBno().toString()).param("title", "Updated")
                .param("content", "body").param("attachmentsSubmitted", "true"))
                .andExpect(status().is3xxRedirection());
        assertTrue(service.getAttachList(board.getBno()).isEmpty());
        assertFalse(Files.exists(fileOf(saved)));
    }

    @Test public void savingSelectedAttachmentsDeletesOnlyRemovedFiles() throws Exception {
        BoardVO board = service.addBoard("Original");
        BoardAttachVO keep = savedAttachment(board.getBno(), "keep.txt");
        BoardAttachVO drop = savedAttachment(board.getBno(), "drop.txt");
        mvc.perform(post("/board/modify").param("bno", board.getBno().toString()).param("title", "Updated")
                .param("content", "body").param("attachmentsSubmitted", "true")
                .param("attachList[0].uuid", keep.getUuid()).param("attachList[0].fileName", keep.getFileName())
                .param("attachList[0].uploadPath", keep.getUploadPath()).param("attachList[0].fileType", "false")
                .param("pageNum", "3").param("amount", "10").param("type", "T").param("keyword", "글"))
                .andExpect(redirectedUrl("/board/list?pageNum=3&amount=10&type=T&keyword=%EA%B8%80"));
        assertEquals(1, service.getAttachList(board.getBno()).size());
        assertTrue(Files.exists(fileOf(keep)));
        assertFalse(Files.exists(fileOf(drop)));
    }

    @Test public void modifyingAttachmentsRejectsMetadataChanges() throws Exception {
        BoardVO board = service.addBoard("Original");
        BoardAttachVO keep = savedAttachment(board.getBno(), "keep.txt");
        BoardAttachVO drop = savedAttachment(board.getBno(), "drop.txt");
        mvc.perform(post("/board/modify").param("bno", board.getBno().toString()).param("title", "Updated")
                .param("content", "body").param("attachmentsSubmitted", "true")
                .param("attachList[0].uuid", keep.getUuid()).param("attachList[0].fileName", "forged.txt")
                .param("attachList[0].uploadPath", "../forged").param("attachList[0].fileType", "true")
                .param("pageNum", "3").param("amount", "10").param("type", "T").param("keyword", "글"))
                .andExpect(status().isBadRequest());
        assertEquals("keep.txt", service.getAttachList(board.getBno()).get(0).getFileName());
        assertTrue(Files.exists(fileOf(keep)));
        assertTrue(Files.exists(fileOf(drop)));
        assertEquals("Original", service.get(board.getBno()).getTitle());
    }

    @Test public void removeDeletesBoardAndItsFiles() throws Exception {
        BoardVO board = service.addBoard("Delete");
        BoardAttachVO saved = savedAttachment(board.getBno(), "a.txt");
        mvc.perform(post("/board/remove").param("bno", board.getBno().toString()).param("pageNum", "2"))
                .andExpect(redirectedUrl("/board/list?pageNum=2&amount=10")).andExpect(flash().attribute("result", "success"));
        assertNull(service.get(board.getBno()));
        assertFalse(Files.exists(fileOf(saved)));
    }

    private BoardAttachVO savedAttachment(Long bno, String fileName) throws Exception {
        BoardAttachVO saved = attach(UUID.randomUUID().toString(), fileName, "2026/10/05", false);
        createFile(store, "2026/10/05", saved.getUuid() + "_" + fileName);
        service.addAttach(bno, saved);
        return saved;
    }

    private Path fileOf(BoardAttachVO saved) {
        return store.getRoot().resolve(saved.getUploadPath()).resolve(saved.getUuid() + "_" + saved.getFileName());
    }
}
