"use strict";
var attachService = (function() {
  function storedPath(path, uuid, name, thumbnail) {
    return path.replace(/\\/g,"/") + "/" + (thumbnail ? "s_" : "") + uuid + "_" + name;
  }
  function makeItem(path, uuid, name, image, removable, existing) {
    image = image === true || image === "true";
    var encoded = encodeURIComponent(storedPath(path,uuid,name,image));
    var item = "<li data-path='"+escapeHtml(path)+"' data-uuid='"+escapeHtml(uuid)+"' data-filename='"+escapeHtml(name)+"' data-type='"+image+"' data-existing='"+!!existing+"'>";
    if (removable) item += "<button type='button' class='file-remove' aria-label='"+escapeHtml(name)+" 첨부 취소' data-file='"+escapeHtml(encoded)+"' data-type='"+(image?"image":"file")+"'>×</button>";
    item += image ? "<img loading='lazy' src='"+contextPath+"/display?fileName="+escapeHtml(encoded)+"' alt='"+escapeHtml(name)+"'>" : "<span class='file-icon' aria-hidden='true'>▤</span>";
    return item + "<span class='file-name' title='"+escapeHtml(name)+"'>"+escapeHtml(name)+"</span></li>";
  }
  function upload(input) {
    var files = Array.from(input.files || []);
    if (!files.length) return null;
    if (files.some(function(file) { return !/\.(png|jpe?g|gif|pdf|txt|docx|xlsx|pptx)$/i.test(file.name) || file.size >= 5*1024*1024 || file.size === 0; })) {
      alert("허용된 이미지·문서 형식의 파일을 선택해 주세요. 파일당 5MB 미만이며 빈 파일은 올릴 수 없습니다.");
      input.value = ""; return null;
    }
    var data = new FormData();
    files.forEach(function(file) { data.append("uploadFile",file); });
    return $.ajax({url:contextPath+"/uploadAjaxAction",type:"POST",data:data,processData:false,contentType:false,dataType:"json"}).always(function(){ input.value=""; });
  }
  function removeUploaded(button) {
    return $.ajax({url:contextPath+"/deleteFile",type:"POST",data:{fileName:decodeURIComponent(button.attr("data-file")),type:button.attr("data-type")},dataType:"text"});
  }
  function appendHiddenInputs(form) {
    form.find("input[name^='attachList[']").remove();
    form.find(".uploadResult li").each(function(i,li) {
      var item=$(li), prefix="attachList["+i+"].";
      ["fileName","uuid","uploadPath","fileType"].forEach(function(field) {
        var attributes={fileName:"data-filename",uuid:"data-uuid",uploadPath:"data-path",fileType:"data-type"};
        $("<input>",{type:"hidden",name:prefix+field,value:item.attr(attributes[field])}).appendTo(form);
      });
    });
  }
  function originalPath(li) {
    return encodeURIComponent(storedPath(li.attr("data-path"),li.attr("data-uuid"),li.attr("data-filename"),false));
  }
  return {makeItem:makeItem,upload:upload,removeUploaded:removeUploaded,appendHiddenInputs:appendHiddenInputs,originalPath:originalPath};
})();
