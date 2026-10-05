<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="editing" value="${true}" /><c:set var="pageTitle" value="게시글 수정" /><c:set var="pageScript" value="editor" />
<%@ include file="../includes/header.jsp" %>
<%@ include file="editor.jspf" %>
<%@ include file="../includes/footer.jsp" %>
