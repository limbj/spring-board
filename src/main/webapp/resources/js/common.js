"use strict";
var contextPath = document.querySelector('meta[name="app-context"]').content;
var csrfToken = document.querySelector('meta[name="csrf-token"]').content;
$.ajaxPrefilter(function(options, originalOptions, xhr) {
  if (!/^(GET|HEAD|OPTIONS)$/i.test(options.type || "GET") && !options.crossDomain) {
    xhr.setRequestHeader("X-CSRF-Token", csrfToken);
  }
});
function escapeHtml(value) {
  return String(value == null ? "" : value).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#39;");
}
function showRequestError(xhr, message) {
  if (xhr && xhr.status === 403) message = "요청 정보가 만료되었습니다. 페이지를 새로고침하고 다시 시도해 주세요.";
  else if (xhr && xhr.status === 400) message = "입력 내용이나 파일 형식·크기를 확인해 주세요.";
  else if (xhr && xhr.status === 404) message = "대상이 없거나 이미 삭제되었습니다.";
  alert(message);
}
