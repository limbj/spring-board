package io.github.limbj.board.controller;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Map;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import io.github.limbj.board.domain.ReplyVO;
import io.github.limbj.board.support.FakeReplyService;

public class ReplyControllerTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private FakeReplyService service;
    private MockMvc mvc;

    @Before public void setUp() {
        service = new FakeReplyService();
        service.addBoard(7L);
        mvc = MockMvcBuilders.standaloneSetup(new ReplyController(service)).build();
    }

    @Test public void blankAndOverlongRepliesAreRejected() throws Exception {
        String[][] values = { { "  ", "me" }, { "x".repeat(1001), "me" }, { "reply", "x".repeat(51) } };
        for (String[] value : values) {
            mvc.perform(post("/replies/new").contentType(MediaType.APPLICATION_JSON).content(reply(7L, value[0], value[1])))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, service.count());
    }

    @Test public void createdReplyIsListedWithServerPageNumber() throws Exception {
        mvc.perform(post("/replies/new").contentType(MediaType.APPLICATION_JSON).content(reply(7L, "첫 댓글", "me")))
                .andExpect(status().isOk()).andExpect(content().string("success"));
        Map<?, ?> page = page("/replies/pages/7/1");
        assertEquals(1, ((Number) page.get("replyCnt")).intValue());
        assertEquals(1, ((Number) page.get("pageNum")).intValue());
        Map<?, ?> first = (Map<?, ?>) ((List<?>) page.get("list")).get(0);
        assertEquals("첫 댓글", first.get("reply"));
    }

    @Test public void pagesAreNormalizedAndClamped() throws Exception {
        for (int i = 0; i < 11; i++) service.register(newReply(7L, "reply " + i, "me"));
        Map<?, ?> first = page("/replies/pages/7/-1");
        Map<?, ?> last = page("/replies/pages/7/2147483647");
        assertEquals(1, ((Number) first.get("pageNum")).intValue());
        assertEquals(2, ((Number) last.get("pageNum")).intValue());
        assertEquals(1, ((List<?>) last.get("list")).size());
    }

    @Test public void modificationChangesOnlyContent() throws Exception {
        ReplyVO saved = newReply(7L, "Original", "me");
        service.register(saved);
        mvc.perform(put("/replies/" + saved.getRno()).contentType(MediaType.APPLICATION_JSON).content(reply(99L, "Updated", "someone")))
                .andExpect(status().isOk());
        assertEquals("Updated", service.get(saved.getRno()).getReply());
        assertEquals("me", service.get(saved.getRno()).getReplyer());
        assertEquals(7L, service.get(saved.getRno()).getBno().longValue());
    }

    @Test public void unknownRepliesAndParentBoardsAre404() throws Exception {
        mvc.perform(get("/replies/999")).andExpect(status().isNotFound());
        mvc.perform(delete("/replies/999")).andExpect(status().isNotFound());
        mvc.perform(put("/replies/999").contentType(MediaType.APPLICATION_JSON).content(reply(7L, "reply", "me")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/replies/new").contentType(MediaType.APPLICATION_JSON).content(reply(999L, "reply", "me")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/replies/pages/999/1")).andExpect(status().isNotFound());
    }

    @Test public void nonPositiveIdsAre400() throws Exception {
        mvc.perform(get("/replies/0")).andExpect(status().isBadRequest());
        mvc.perform(delete("/replies/-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/replies/pages/0/1")).andExpect(status().isBadRequest());
        mvc.perform(post("/replies/new").contentType(MediaType.APPLICATION_JSON).content(reply(0L, "reply", "me")))
                .andExpect(status().isBadRequest());
    }

    private Map<?, ?> page(String path) throws Exception {
        return json.readValue(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), Map.class);
    }

    private String reply(Long bno, String body, String writer) {
        return json.writeValueAsString(Map.of("bno", bno, "reply", body, "replyer", writer));
    }

    private ReplyVO newReply(Long bno, String body, String writer) {
        ReplyVO reply = new ReplyVO();
        reply.setBno(bno);
        reply.setReply(body);
        reply.setReplyer(writer);
        return reply;
    }
}
