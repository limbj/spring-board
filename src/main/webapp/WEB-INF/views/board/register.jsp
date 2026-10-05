<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="editing" value="${false}" /><c:set var="pageTitle" value="새 글 작성" /><c:set var="pageScript" value="editor" />
<%@ include file="../includes/header.jsp" %>
<%@ include file="editor.jspf" %>
<%@ include file="../includes/footer.jsp" %>
