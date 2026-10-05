<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="게시글 목록" />
<%@ include file="../includes/header.jsp" %>
<section class="page-heading"><div><p class="eyebrow">OUR OPEN COMMUNITY</p><h1>이야기가 이어지는 공간</h1><p>오늘의 생각과 유용한 자료를 함께 나눠 보세요.</p></div><a class="button" href="${ctx}/board/register">＋ 새 글 작성</a></section>
<c:if test="${not empty result}"><div class="notice notice-success" role="status"><c:choose><c:when test="${result.toString() eq 'success'}">변경 사항을 반영했습니다.</c:when><c:otherwise>게시글 <c:out value="${result}" />번을 등록했습니다.</c:otherwise></c:choose></div></c:if>
<section class="card board-card" aria-label="게시글 목록">
  <div class="board-toolbar"><p>전체 <strong><c:out value="${pageMaker.total}" /></strong>개의 이야기</p><span class="muted">최신 글부터 보여드려요</span></div>
  <form class="search-form" action="${ctx}/board/list" method="get">
    <label class="sr-only" for="search-type">검색 범위</label>
    <select id="search-type" name="type">
      <option value="TWC" ${empty pageMaker.cri.type or pageMaker.cri.type eq 'TWC' ? 'selected' : ''}>전체</option>
      <option value="T" ${pageMaker.cri.type eq 'T' ? 'selected' : ''}>제목</option>
      <option value="C" ${pageMaker.cri.type eq 'C' ? 'selected' : ''}>내용</option>
      <option value="W" ${pageMaker.cri.type eq 'W' ? 'selected' : ''}>작성자</option>
      <option value="TC" ${pageMaker.cri.type eq 'TC' ? 'selected' : ''}>제목 + 내용</option>
      <option value="TW" ${pageMaker.cri.type eq 'TW' ? 'selected' : ''}>제목 + 작성자</option>
      <option value="WC" ${pageMaker.cri.type eq 'WC' ? 'selected' : ''}>내용 + 작성자</option>
    </select>
    <label class="sr-only" for="search-keyword">검색어</label>
    <input id="search-keyword" type="search" name="keyword" maxlength="100" placeholder="찾고 싶은 이야기를 입력하세요" value="<c:out value='${pageMaker.cri.keyword}' />">
    <input type="hidden" name="pageNum" value="1"><input type="hidden" name="amount" value="<c:out value='${pageMaker.cri.amount}' />">
    <button class="button button-secondary" type="submit">검색</button>
    <c:if test="${not empty pageMaker.cri.keyword}"><a class="text-link" href="${ctx}/board/list">초기화</a></c:if>
  </form>
  <c:choose><c:when test="${empty list}"><div class="empty-state"><span class="empty-mark" aria-hidden="true">＋</span><h2>아직 이야기가 없어요</h2><p>첫 번째 글을 남기거나 다른 검색어로 찾아보세요.</p><a class="button" href="${ctx}/board/register">이야기 작성하기</a></div></c:when><c:otherwise>
  <div class="table-wrap"><table class="board-table"><thead><tr><th class="col-number" scope="col">번호</th><th scope="col">제목</th><th class="col-author" scope="col">작성자</th><th class="col-date" scope="col">작성일</th></tr></thead><tbody>
  <c:forEach var="board" items="${list}">
    <c:url var="readUrl" value="/board/get"><c:param name="bno" value="${board.bno}" /><c:param name="pageNum" value="${pageMaker.cri.pageNum}" /><c:param name="amount" value="${pageMaker.cri.amount}" /><c:param name="type" value="${pageMaker.cri.type}" /><c:param name="keyword" value="${pageMaker.cri.keyword}" /></c:url>
    <tr><td class="muted col-number"><c:out value="${board.bno}" /></td><td><a class="article-link" href="<c:out value='${readUrl}' />"><c:out value="${board.title}" /></a><c:if test="${board.replyCnt > 0}"><span class="reply-count" aria-label="댓글 수"><c:out value="${board.replyCnt}" /></span></c:if></td><td class="col-author muted"><c:out value="${board.writer}" /></td><td class="col-date muted"><fmt:formatDate value="${board.regdate}" pattern="yyyy.MM.dd" /></td></tr>
  </c:forEach>
  </tbody></table></div>
  </c:otherwise></c:choose>
</section>
<c:if test="${pageMaker.total > 0}">
<nav class="pagination" aria-label="게시글 페이지">
  <c:if test="${pageMaker.prev}"><c:url var="prevUrl" value="/board/list"><c:param name="pageNum" value="${pageMaker.startPage - 1}" /><c:param name="amount" value="${pageMaker.cri.amount}" /><c:param name="type" value="${pageMaker.cri.type}" /><c:param name="keyword" value="${pageMaker.cri.keyword}" /></c:url><a href="<c:out value='${prevUrl}' />">이전</a></c:if>
  <c:forEach var="num" begin="${pageMaker.startPage}" end="${pageMaker.endPage}">
    <c:choose><c:when test="${num eq pageMaker.cri.pageNum}"><span class="current-page" aria-current="page"><c:out value="${num}" /></span></c:when><c:otherwise><c:url var="pageUrl" value="/board/list"><c:param name="pageNum" value="${num}" /><c:param name="amount" value="${pageMaker.cri.amount}" /><c:param name="type" value="${pageMaker.cri.type}" /><c:param name="keyword" value="${pageMaker.cri.keyword}" /></c:url><a href="<c:out value='${pageUrl}' />"><c:out value="${num}" /></a></c:otherwise></c:choose>
  </c:forEach>
  <c:if test="${pageMaker.next}"><c:url var="nextUrl" value="/board/list"><c:param name="pageNum" value="${pageMaker.endPage + 1}" /><c:param name="amount" value="${pageMaker.cri.amount}" /><c:param name="type" value="${pageMaker.cri.type}" /><c:param name="keyword" value="${pageMaker.cri.keyword}" /></c:url><a href="<c:out value='${nextUrl}' />">다음</a></c:if>
</nav>
</c:if>
<p class="page-note">회원가입 없이 글과 댓글을 남길 수 있는 열린 게시판입니다.</p>
<%@ include file="../includes/footer.jsp" %>
