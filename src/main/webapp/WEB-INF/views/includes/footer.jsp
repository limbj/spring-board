<%@ page pageEncoding="UTF-8" %>
</main>
<footer class="site-footer"><div class="shell footer-inner"><span>Spring Board</span><span>이야기와 자료가 함께 쌓이는 공간.</span></div></footer>
<script src="${ctx}/resources/js/attach.js"></script>
<script src="${ctx}/resources/js/reply.js"></script>
<c:if test="${not empty pageScript}"><script src="${ctx}/resources/js/${pageScript}.js"></script></c:if>
</body>
</html>
