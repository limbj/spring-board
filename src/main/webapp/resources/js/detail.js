"use strict";
$(function() {
  var screen=$("#article-screen"); if(!screen.length)return;
  var bno=Number(screen.attr("data-bno")), page=1, serial=0, request=null, replies=new Map(), replyUpdating=false;
  $.getJSON(contextPath+"/board/getAttachList",{bno:bno}).done(function(list){
    $("#attachment-status").text(list.length ? "이미지를 눌러 크게 보거나 파일을 내려받으세요." : "첨부파일이 없습니다.");
    list.forEach(function(file){screen.find(".uploadResult ul").append(attachService.makeItem(file.uploadPath,file.uuid,file.fileName,file.fileType,false,true));});
  }).fail(function(){$("#attachment-status").text("첨부파일을 불러오지 못했습니다. 새로고침해 주세요.");});
  screen.on("click",".viewable li",function() {
    var item=$(this), path=attachService.originalPath(item);
    if(item.attr("data-type")==="true"){
      $("#full-image").attr({src:contextPath+"/display?fileName="+path,alt:item.attr("data-filename")});
      document.getElementById("image-dialog").showModal();
    } else window.location.assign(contextPath+"/download?fileName="+path);
  });
  $("[data-close-image]").on("click",function(){document.getElementById("image-dialog").close();});
  $("#delete-post").on("submit",function(event){
    if(!confirm("이 게시글을 삭제할까요? 댓글과 첨부파일도 함께 삭제됩니다."))event.preventDefault();
    else $(this).find("button").prop("disabled",true);
  });
  window.addEventListener("pageshow",function(event){if(event.persisted)$("#delete-post button").prop("disabled",false);});
  function renderPages(count) {
    var total=Math.ceil(count/10), start=Math.floor((page-1)/10)*10+1, end=Math.min(start+9,total), html="";
    function button(n,label,current){return "<button type='button' data-page='"+n+"' "+(current?"class='current-page' aria-current='page'":"")+">"+label+"</button>";}
    if(start>1)html+=button(start-1,"이전",false);
    for(var p=start;p<=end;p++)html+=button(p,String(p),p===page);
    if(end<total)html+=button(end+1,"다음",false);
    $("#reply-pages").html(html);
  }
  function showReplies(nextPage) {
    var mySerial=++serial;
    if(request)request.abort();
    $("#reply-status").text("댓글을 불러오고 있어요.");
    request=replyService.getList({bno:bno,page:nextPage},function(count,list,normalizedPage) {
      if(mySerial!==serial)return;
      page=normalizedPage;replies.clear();$("#reply-count").text(count);$("#reply-status").text(list.length?"":"첫 번째 댓글을 남겨 보세요.");
      var html="";
      list.forEach(function(reply) {
        replies.set(String(reply.rno),reply);
        html+="<li data-rno='"+reply.rno+"'><div class='reply-meta'><strong>"+escapeHtml(reply.replyer)+"</strong><span>"+escapeHtml(replyService.displayTime(reply.replyDate||reply.replydate))+"</span></div><p class='reply-body'>"+escapeHtml(reply.reply)+"</p><div class='reply-tools'><button type='button' data-reply-edit>수정</button><button type='button' data-reply-delete>삭제</button></div></li>";
      });
      $("#reply-list").html(html);renderPages(count);
    },function(){if(mySerial===serial)$("#reply-status").text("댓글을 불러오지 못했습니다. 페이지를 새로고침해 주세요.");});
  }
  $("#reply-pages").on("click","button",function(){showReplies(Number($(this).attr("data-page")));});
  $("#reply-form").on("submit",function(event){
    event.preventDefault();var form=$(this),button=form.find("button"),body=form.find("[name='reply']").val().trim(),writer=form.find("[name='replyer']").val().trim();
    if(button.prop("disabled"))return;
    if(!body||!writer){alert("작성자와 댓글 내용을 입력해 주세요.");return;}
    button.prop("disabled",true);
    replyService.add({bno:bno,reply:body,replyer:writer}).done(function(){form.find("[name='reply']").val("");showReplies(1000000000);}).fail(function(xhr){showRequestError(xhr,"댓글을 등록하지 못했습니다.");}).always(function(){button.prop("disabled",false);});
  });
  $("#reply-list").on("click","[data-reply-edit]",function(){
    if(replyUpdating)return;
    var id=$(this).closest("li").attr("data-rno"),reply=replies.get(id);
    if(!reply)return;
    $("#reply-edit-form [name='rno']").val(id);$("#edit-reply-body").val(reply.reply);document.getElementById("reply-dialog").showModal();
  });
  $("[data-close-dialog]").on("click",function(){if(!replyUpdating)document.getElementById("reply-dialog").close();});
  $("#reply-dialog").on("cancel",function(event){if(replyUpdating)event.preventDefault();});
  $("#reply-edit-form").on("submit",function(event){
    event.preventDefault();var form=$(this),body=form.find("[name='reply']").val().trim(),button=form.find("button[type='submit']");
    if(!body||replyUpdating||button.prop("disabled"))return;replyUpdating=true;form.find("button").prop("disabled",true);
    replyService.update({rno:Number(form.find("[name='rno']").val()),reply:body}).done(function(){document.getElementById("reply-dialog").close();showReplies(page);}).fail(function(xhr){showRequestError(xhr,"댓글을 수정하지 못했습니다.");}).always(function(){replyUpdating=false;form.find("button").prop("disabled",false);});
  });
  $("#reply-list").on("click","[data-reply-delete]",function(){
    var button=$(this);if(button.prop("disabled")||!confirm("이 댓글을 삭제할까요?"))return;button.prop("disabled",true);
    replyService.remove(Number(button.closest("li").attr("data-rno"))).done(function(){button.closest("li").remove();showReplies(page);}).fail(function(xhr){showRequestError(xhr,"댓글을 삭제하지 못했습니다.");button.prop("disabled",false);});
  });
  showReplies(1);
});
