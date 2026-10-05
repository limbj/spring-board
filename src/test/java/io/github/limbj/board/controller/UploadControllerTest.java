package io.github.limbj.board.controller;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.github.limbj.board.domain.AttachFileDTO;
import io.github.limbj.board.support.FakeBoardService;
import io.github.limbj.board.upload.UploadFileStore;
import io.github.limbj.board.upload.UploadFileStoreTest;

public class UploadControllerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private UploadFileStore store;
    private FakeBoardService board;
    private MockMvc mvc;
    private MockHttpSession owner;
    private final JsonMapper json=JsonMapper.builder().build();
    @Before public void setup() {
        store=new UploadFileStore(temp.getRoot().getPath()); board=new FakeBoardService(); owner=new MockHttpSession();
        mvc=MockMvcBuilders.standaloneSetup(new UploadController(store,board)).build();
    }
    @Test public void uploadsUtf8FileAndReturnsServerIssuedMetadata() throws Exception {
        MockMultipartFile file=file("메모.txt","hi".getBytes(StandardCharsets.UTF_8));
        JsonNode item=json.readTree(mvc.perform(multipart("/uploadAjaxAction").file(file).session(owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get(0);
        assertEquals("메모.txt",item.get("fileName").asText());
        assertFalse(item.get("image").asBoolean());
        assertNotNull(store.resolve(item.get("uploadPath").asText()+"/"+item.get("uuid").asText()+"_메모.txt"));
    }
    @Test public void blockedFileRejectsWholeBatchBeforeStorage() throws Exception {
        mvc.perform(multipart("/uploadAjaxAction").file(file("ok.txt",new byte[]{1})).file(file("attack.html",new byte[]{1})).session(owner))
                .andExpect(status().isBadRequest());
        assertEquals(0,fileCount());
    }
    @Test public void laterMalformedImageRollsBackEarlierSuccessfulUploadAndRegistry() throws Exception {
        mvc.perform(multipart("/uploadAjaxAction").file(file("ok.txt","ok".getBytes(StandardCharsets.UTF_8)))
                .file(file("fake.png","html".getBytes(StandardCharsets.UTF_8))).session(owner)).andExpect(status().isBadRequest());
        assertEquals(0,fileCount());
        assertTrue(((java.util.Map<?,?>)owner.getAttribute(UploadFileStore.PENDING_SESSION_ATTRIBUTE)).isEmpty());
    }
    @Test public void laterStorageFailureRollsBackBatch() throws Exception {
        MockMultipartFile broken=new MockMultipartFile("uploadFile","broken.txt","text/plain",new byte[]{1}) {
            @Override public void transferTo(Path path) throws IOException { Files.writeString(path,"partial"); throw new IOException("test failure"); }
        };
        assertEquals(500,new UploadController(store,board).upload(new MockMultipartFile[]{file("ok.txt","ok".getBytes(StandardCharsets.UTF_8)),broken},owner).getStatusCode().value());
        assertEquals(0,fileCount());
    }
    @Test public void excessiveFileCountIsRejected() throws Exception {
        var request=multipart("/uploadAjaxAction");
        request.session(owner);
        for(int i=0;i<11;i++) request.file(file("a"+i+".txt",new byte[]{1}));
        mvc.perform(request).andExpect(status().isBadRequest());
        assertEquals(0,fileCount());
    }
    @Test public void unmanagedAndTraversalPathsAreRejected() throws Exception {
        mvc.perform(get("/display").param("fileName","../../etc/passwd")).andExpect(status().isBadRequest());
        mvc.perform(get("/download").param("fileName","private.env")).andExpect(status().isBadRequest());
        mvc.perform(post("/deleteFile").param("fileName","../../etc/passwd").session(owner)).andExpect(status().isBadRequest());
    }
    @Test public void downloadUsesAttachmentUtf8NameAndNosniff() throws Exception {
        AttachFileDTO dto=store.save(file("보고서 최종.txt","hello".getBytes(StandardCharsets.UTF_8)),owner);
        mvc.perform(get("/download").param("fileName",relative(dto,false))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type","application/octet-stream"))
                .andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("Content-Disposition","attachment; filename*=UTF-8''%EB%B3%B4%EA%B3%A0%EC%84%9C%20%EC%B5%9C%EC%A2%85.txt"));
    }
    @Test public void displayNeverServesTextInlineEvenWhenItContainsHtml() throws Exception {
        AttachFileDTO dto=store.save(file("text.txt","<script>bad</script>".getBytes(StandardCharsets.UTF_8)),owner);
        mvc.perform(get("/display").param("fileName",relative(dto,false))).andExpect(status().isUnsupportedMediaType());
    }
    @Test public void displayReturnsVerifiedRasterAsPng() throws Exception {
        AttachFileDTO dto=store.save(file("photo.png",UploadFileStoreTest.png(8,8)),owner);
        byte[] body=mvc.perform(get("/display").param("fileName",relative(dto,true))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type","image/png")).andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertNotNull(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(body)));
    }
    @Test public void savedAttachmentCannotBeCancelledEvenWithPendingSession() throws Exception {
        AttachFileDTO dto=store.save(file("saved.txt",new byte[]{1}),owner);
        board.addAttach(1L,UploadFileStoreTest.asAttachment(dto));
        mvc.perform(post("/deleteFile").param("fileName",relative(dto,false)).session(owner)).andExpect(status().isForbidden());
        assertTrue(Files.exists(store.pathOf(UploadFileStoreTest.asAttachment(dto),false)));
    }
    @Test public void differentSessionCannotCancelPendingUpload() throws Exception {
        AttachFileDTO dto=store.save(file("draft.txt",new byte[]{1}),owner);
        mvc.perform(post("/deleteFile").param("fileName",relative(dto,false)).session(new MockHttpSession())).andExpect(status().isForbidden());
        assertTrue(Files.exists(store.pathOf(UploadFileStoreTest.asAttachment(dto),false)));
    }
    @Test public void cancelUsesServerImageFlagAndRemovesBothFiles() throws Exception {
        AttachFileDTO dto=store.save(file("photo.png",UploadFileStoreTest.png(8,8)),owner);
        mvc.perform(post("/deleteFile").param("fileName",relative(dto,false)).param("type","file").session(owner)).andExpect(status().isOk());
        assertFalse(Files.exists(store.pathOf(UploadFileStoreTest.asAttachment(dto),false)));
        assertFalse(Files.exists(store.pathOf(UploadFileStoreTest.asAttachment(dto),true)));
    }
    private static MockMultipartFile file(String name,byte[] bytes) { return new MockMultipartFile("uploadFile",name,"application/octet-stream",bytes); }
    private static String relative(AttachFileDTO dto,boolean thumbnail) { return dto.getUploadPath()+"/"+(thumbnail?"s_":"")+dto.getUuid()+"_"+dto.getFileName(); }
    private long fileCount() throws IOException { try(var paths=Files.walk(store.getRoot())) { return paths.filter(Files::isRegularFile).count(); } }
}
