package io.github.limbj.board.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import io.github.limbj.board.domain.Criteria;
import io.github.limbj.board.domain.InputRules;
import io.github.limbj.board.domain.ReplyPageDTO;
import io.github.limbj.board.domain.ReplyVO;
import io.github.limbj.board.service.ReplyService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/replies")
@RequiredArgsConstructor
public class ReplyController {
    private static final int REPLIES_PER_PAGE = 10;
    private final ReplyService service;

    @PostMapping(value = "/new", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> create(@RequestBody ReplyVO reply) {
        InputRules.reply(reply, true);
        return service.register(reply) == 1 ? ResponseEntity.ok("success")
                : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping(value = "/pages/{bno}/{page}", produces = "application/json;charset=UTF-8")
    public ResponseEntity<ReplyPageDTO> getList(@PathVariable("bno") Long bno, @PathVariable("page") int page) {
        InputRules.positiveId(bno, "게시글 번호");
        return ResponseEntity.ok(service.getListPage(new Criteria(page, REPLIES_PER_PAGE), bno));
    }

    @GetMapping(value = "/{rno}", produces = "application/json;charset=UTF-8")
    public ResponseEntity<ReplyVO> get(@PathVariable("rno") Long rno) {
        InputRules.positiveId(rno, "댓글 번호");
        ReplyVO reply = service.get(rno);
        return reply == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(reply);
    }

    @RequestMapping(value = "/{rno}", method = { RequestMethod.PUT, RequestMethod.PATCH },
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> modify(@PathVariable("rno") Long rno, @RequestBody ReplyVO reply) {
        InputRules.positiveId(rno, "댓글 번호");
        reply.setRno(rno);
        InputRules.reply(reply, false);
        return service.modify(reply) == 1 ? ResponseEntity.ok("success") : ResponseEntity.notFound().build();
    }

    @DeleteMapping(value = "/{rno}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> remove(@PathVariable("rno") Long rno) {
        InputRules.positiveId(rno, "댓글 번호");
        return service.remove(rno) == 1 ? ResponseEntity.ok("success") : ResponseEntity.notFound().build();
    }
}
