package io.github.limbj.board.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 첫 화면(/)은 게시판 목록으로 보낸다.
 */
@Controller
public class HomeController {

	@GetMapping("/")
	public String home() {
		return "redirect:/board/list";
	}
}
