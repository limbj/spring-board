<%@ page pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="app-context" content="<c:out value='${ctx}' />">
  <meta name="csrf-token" content="<c:out value='${requestScope.csrfToken}' />">
  <title><c:out value="${pageTitle}" default="게시판" /> · Spring Board</title>
  <link rel="stylesheet" href="${ctx}/resources/css/board.css">
  <script src="${ctx}/resources/vendor/jquery/jquery.min.js"></script>
  <script src="${ctx}/resources/js/common.js"></script>
</head>
<body>
<a class="skip-link" href="#main">본문으로 바로가기</a>
<header class="site-header"><div class="shell header-inner">
  <a class="brand" href="${ctx}/board/list"><span class="brand-mark" aria-hidden="true">S</span>Spring Board</a>
  <nav aria-label="주 메뉴"><a href="${ctx}/board/list">게시글</a><a class="button button-small" href="${ctx}/board/register">새 글 작성</a></nav>
</div></header>
<main id="main" class="shell main-content">
