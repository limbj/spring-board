<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="status" value="${requestScope['jakarta.servlet.error.status_code']}" /><c:set var="pageTitle" value="요청 안내" />
<%@ include file="includes/header.jsp" %>
<section class="card error-card"><p class="eyebrow">PLEASE CHECK</p><h1><c:choose><c:when test="${status eq 404}">페이지를 찾을 수 없어요</c:when><c:when test="${status eq 400 or status eq 413}">입력 내용을 확인해 주세요</c:when><c:when test="${status eq 403}">페이지를 새로고침해 주세요</c:when><c:when test="${status eq 405}">지원하지 않는 요청이에요</c:when><c:otherwise>요청을 처리하지 못했어요</c:otherwise></c:choose></h1><p>주소와 입력 내용을 확인하고 다시 시도해 주세요.</p><a class="button" href="${ctx}/board/list">게시글 목록으로</a></section>
<%@ include file="includes/footer.jsp" %>
