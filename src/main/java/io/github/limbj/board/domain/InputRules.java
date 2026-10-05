package io.github.limbj.board.domain;

import io.github.limbj.board.exception.BadRequestException;

/** HTTP and direct service calls use identical field limits. */
public final class InputRules {
    private InputRules() { }

    public static void positiveId(Long id, String field) {
        if (id == null || id < 1) throw new BadRequestException(field + "는 양수여야 합니다.");
    }

    public static void board(BoardVO board, boolean registration) {
        if (board == null) throw new BadRequestException("게시글 정보가 필요합니다.");
        if (!registration) positiveId(board.getBno(), "게시글 번호");
        board.setTitle(required(board.getTitle(), 200, "제목").strip());
        required(board.getContent(), 20000, "내용");
        if (registration) board.setWriter(required(board.getWriter(), 50, "작성자").strip());
    }

    public static void reply(ReplyVO reply, boolean registration) {
        if (reply == null) throw new BadRequestException("댓글 정보가 필요합니다.");
        if (registration) {
            positiveId(reply.getBno(), "게시글 번호");
            reply.setReplyer(required(reply.getReplyer(), 50, "작성자").strip());
        } else {
            positiveId(reply.getRno(), "댓글 번호");
        }
        required(reply.getReply(), 1000, "댓글");
    }

    private static String required(String value, int max, String label) {
        if (value == null || value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new BadRequestException(label + "을 입력해 주세요.");
        }
        if (value.length() > max) throw new BadRequestException(label + "은 " + max + "자 이내로 입력해 주세요.");
        return value;
    }
}
