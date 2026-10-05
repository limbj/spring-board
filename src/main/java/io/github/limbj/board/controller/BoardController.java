package io.github.limbj.board.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import io.github.limbj.board.domain.BoardAttachVO;
import io.github.limbj.board.domain.BoardVO;
import io.github.limbj.board.domain.Criteria;
import io.github.limbj.board.domain.InputRules;
import io.github.limbj.board.domain.PageDTO;
import io.github.limbj.board.exception.NotFoundException;
import io.github.limbj.board.service.BoardService;
import io.github.limbj.board.upload.UploadFileStore;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardController {
    private final BoardService service;
    private final UploadFileStore fileStore;

    @GetMapping("/list")
    public void list(Criteria criteria, Model model) {
        criteria.normalize();
        List<BoardVO> boards = service.getList(criteria);
        model.addAttribute("list", boards);
        model.addAttribute("pageMaker", new PageDTO(criteria, service.getTotal(criteria)));
    }

    @GetMapping("/register")
    public void register() { }

    @PostMapping("/register")
    public String register(BoardVO board, HttpSession session, RedirectAttributes redirect) {
        InputRules.board(board, true);
        synchronized (fileStore) {
            board.setAttachList(fileStore.validateAttachments(board.getAttachList(), List.of(), session));
            service.register(board);
            fileStore.releasePending(board.getAttachList(), session);
        }
        redirect.addFlashAttribute("result", board.getBno());
        return "redirect:/board/list";
    }

    @GetMapping({ "/get", "/modify" })
    public void get(@RequestParam("bno") Long bno, @ModelAttribute("cri") Criteria criteria, Model model) {
        InputRules.positiveId(bno, "게시글 번호");
        criteria.normalize();
        BoardVO board = service.get(bno);
        if (board == null) throw new NotFoundException("게시글이 없습니다.");
        model.addAttribute("board", board);
    }

    @PostMapping("/modify")
    public String modify(BoardVO board, @ModelAttribute("cri") Criteria criteria,
            @RequestParam(value = "attachmentsSubmitted", defaultValue = "false") boolean attachmentsSubmitted,
            HttpSession session, RedirectAttributes redirect) {
        InputRules.board(board, false);
        criteria.normalize();
        synchronized (fileStore) {
            if (service.get(board.getBno()) == null) throw new NotFoundException("게시글이 없습니다.");
            List<BoardAttachVO> before = service.getAttachList(board.getBno());
            List<BoardAttachVO> submitted = board.getAttachList();
            if (!attachmentsSubmitted && submitted == null) {
                submitted = before;
            } else if (submitted == null) {
                submitted = List.of();
            }
            board.setAttachList(fileStore.validateAttachments(submitted, before, session));
            if (!service.modify(board)) throw new NotFoundException("게시글이 없습니다.");
            fileStore.releasePending(board.getAttachList(), session);
            fileStore.deleteAll(removedAttachments(before, board.getAttachList()));
        }
        redirect.addFlashAttribute("result", "success");
        return "redirect:/board/list" + criteria.getListLink();
    }

    @PostMapping("/remove")
    public String remove(@RequestParam("bno") Long bno, Criteria criteria, HttpSession session,
            RedirectAttributes redirect) {
        InputRules.positiveId(bno, "게시글 번호");
        criteria.normalize();
        synchronized (fileStore) {
            if (service.get(bno) == null) throw new NotFoundException("게시글이 없습니다.");
            List<BoardAttachVO> attachments = service.getAttachList(bno);
            if (!service.remove(bno)) throw new NotFoundException("게시글이 없습니다.");
            fileStore.deleteAll(attachments);
        }
        redirect.addFlashAttribute("result", "success");
        return "redirect:/board/list" + criteria.getListLink();
    }

    @GetMapping(value = "/getAttachList", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<List<BoardAttachVO>> getAttachList(@RequestParam("bno") Long bno) {
        InputRules.positiveId(bno, "게시글 번호");
        if (service.get(bno) == null) throw new NotFoundException("게시글이 없습니다.");
        return ResponseEntity.ok(service.getAttachList(bno));
    }

    private static List<BoardAttachVO> removedAttachments(List<BoardAttachVO> before, List<BoardAttachVO> after) {
        Set<String> kept = new HashSet<>();
        for (BoardAttachVO attachment : after) kept.add(attachment.getUuid());
        List<BoardAttachVO> removed = new ArrayList<>();
        for (BoardAttachVO attachment : before) {
            if (!kept.contains(attachment.getUuid())) removed.add(attachment);
        }
        return removed;
    }
}
