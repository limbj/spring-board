"use strict";
$(function() {
  var form=$("#editor-form"), input=document.getElementById("upload-input"), status=$("#upload-status"), save=$("#save-post");
  if (!form.length) return;
  var editing=form.attr("data-editing")==="true", ready=!editing, failed=false, pending=0, submitting=false;
  function updateState() {
    var blocked=!ready || failed || pending>0 || submitting;
    save.prop("disabled",blocked);
    $(input).prop("disabled",!ready || failed || pending>0 || submitting);
    form.find(".file-remove").prop("disabled",pending>0 || submitting);
    if(pending>0)status.text("첨부파일을 처리하고 있어요. 완료 후 저장할 수 있습니다.");
    else if(ready && !failed)status.text(form.find(".uploadResult li").length ? "첨부파일을 준비했습니다." : "첨부파일을 선택하면 업로드됩니다.");
  }
  function append(list,existing) {
    list.forEach(function(file){ form.find(".uploadResult ul").append(attachService.makeItem(file.uploadPath,file.uuid,file.fileName,existing?file.fileType:file.image,true,existing)); });
  }
  if(editing) {
    status.text("기존 첨부파일을 불러오고 있어요."); updateState();
    $.getJSON(contextPath+"/board/getAttachList",{bno:form.attr("data-bno")}).done(function(list){ append(list,true); ready=true; updateState(); }).fail(function(){failed=true;status.text("첨부파일을 불러오지 못했습니다. 내용을 보존하려면 페이지를 새로고침해 주세요.");updateState();});
  }
  $(input).on("change",function() {
    if(!ready || failed || pending>0) return;
    if(form.find(".uploadResult li").length + (this.files||[]).length>10){alert("한 게시글에는 최대 10개까지 첨부할 수 있습니다.");this.value="";return;}
    var request=attachService.upload(this); if(!request)return;
    pending++;updateState();
    request.done(function(files){append(files,false);}).fail(function(xhr){showRequestError(xhr,"파일 업로드에 실패했습니다.");}).always(function(){pending--;updateState();});
  });
  form.on("click",".file-remove",function() {
    if(pending || submitting)return;
    var button=$(this), item=button.closest("li");
    if(item.attr("data-existing")==="true"){item.remove();updateState();return;}
    pending++;updateState();
    attachService.removeUploaded(button).done(function(){item.remove();}).fail(function(xhr){if(xhr.status===404)item.remove();else showRequestError(xhr,"첨부 취소에 실패했습니다.");}).always(function(){pending--;updateState();});
  });
  form.on("submit",function(event) {
    if(!ready || failed || pending>0 || submitting){event.preventDefault();return;}
    var blank=Array.from(form[0].querySelectorAll("[required]")).find(function(field){return !field.value.trim();});
    if(blank){event.preventDefault();blank.focus();alert("제목, 내용과 작성자를 입력해 주세요.");return;}
    attachService.appendHiddenInputs(form);
    form.find("[name='attachmentsSubmitted']").val("true");
    submitting=true;updateState();
  });
  window.addEventListener("pageshow",function(event){if(event.persisted){submitting=false;updateState();}});
  updateState();
});
