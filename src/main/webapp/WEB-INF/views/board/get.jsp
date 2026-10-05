<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="${board.title}" /><c:set var="pageScript" value="detail" />
<%@ include file="../includes/header.jsp" %>
<c:url var="listUrl" value="/board/list"><c:param name="pageNum" value="${cri.pageNum}" /><c:param name="amount" value="${cri.amount}" /><c:param name="type" value="${cri.type}" /><c:param name="keyword" value="${cri.keyword}" /></c:url>
<c:url var="modifyUrl" value="/board/modify"><c:param name="bno" value="${board.bno}" /><c:param name="pageNum" value="${cri.pageNum}" /><c:param name="amount" value="${cri.amount}" /><c:param name="type" value="${cri.type}" /><c:param name="keyword" value="${cri.keyword}" /></c:url>
<a class="back-link" href="<c:out value='${listUrl}' />">← 게시글 목록</a>
<article id="article-screen" class="card article-card" data-bno="<c:out value='${board.bno}' />">
  <header class="article-heading"><p class="eyebrow">OUR STORIES</p><h1><c:out value="${board.title}" /></h1><div class="article-meta"><strong><c:out value="${board.writer}" /></strong><span><fmt:formatDate value="${board.regdate}" pattern="yyyy.MM.dd HH:mm" /></span><span>글 번호 <c:out value="${board.bno}" /></span></div></header>
  <div class="article-body"><c:out value="${board.content}" /></div>
  <section class="article-attachments" aria-label="첨부파일"><h2>첨부파일</h2><p id="attachment-status" class="muted" role="status">첨부파일을 불러오고 있어요.</p><div class="uploadResult viewable"><ul></ul></div></section>
  <div class="article-actions"><a class="button button-secondary" href="<c:out value='${listUrl}' />">목록으로</a><div class="action-group"><a class="button button-secondary" href="<c:out value='${modifyUrl}' />">수정</a>
    <form id="delete-post" action="${ctx}/board/remove" method="post"><input type="hidden" name="_csrf" value="<c:out value='${requestScope.csrfToken}' />"><input type="hidden" name="bno" value="<c:out value='${board.bno}' />"><input type="hidden" name="pageNum" value="<c:out value='${cri.pageNum}' />"><input type="hidden" name="amount" value="<c:out value='${cri.amount}' />"><input type="hidden" name="type" value="<c:out value='${cri.type}' />"><input type="hidden" name="keyword" value="<c:out value='${cri.keyword}' />"><button class="button button-danger" type="submit">삭제</button></form>
  </div></div>
</article>
<section class="card reply-card" aria-label="댓글"><div class="section-heading"><h2>함께 나누는 댓글 <span id="reply-count" class="count-badge"><c:out value="${board.replyCnt}" /></span></h2></div>
  <p id="reply-status" class="muted" role="status">댓글을 불러오고 있어요.</p><ol id="reply-list" class="reply-list"></ol><nav id="reply-pages" class="pagination" aria-label="댓글 페이지"></nav>
  <form id="reply-form" class="reply-form"><div class="field"><label for="reply-writer">작성자</label><input id="reply-writer" name="replyer" maxlength="50" placeholder="표시할 이름" required></div><div class="field"><label for="reply-body">댓글</label><textarea id="reply-body" name="reply" rows="3" maxlength="1000" placeholder="따뜻한 말 한마디를 남겨 주세요." required></textarea></div><div class="form-actions"><button class="button" type="submit">댓글 등록</button></div></form>
</section>
<dialog id="reply-dialog" class="dialog"><form id="reply-edit-form"><h2>댓글 수정</h2><input type="hidden" name="rno"><div class="field"><label for="edit-reply-body">내용</label><textarea id="edit-reply-body" name="reply" rows="5" maxlength="1000" required></textarea></div><div class="form-actions"><button type="button" class="button button-secondary" data-close-dialog>취소</button><button type="submit" class="button">저장</button></div></form></dialog>
<dialog id="image-dialog" class="dialog image-dialog"><button type="button" class="image-close button button-secondary" data-close-image>닫기</button><img id="full-image" alt=""></dialog>
<p class="page-note">열린 게시판의 글과 댓글은 누구나 수정·삭제할 수 있어요.</p>
<%@ include file="../includes/footer.jsp" %>
