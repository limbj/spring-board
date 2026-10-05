"use strict";
var replyService = (function() {
  function add(reply) { return $.ajax({type:"POST",url:contextPath+"/replies/new",data:JSON.stringify(reply),contentType:"application/json;charset=UTF-8",dataType:"text"}); }
  function getList(param,callback,error) {
    return $.getJSON(contextPath+"/replies/pages/"+param.bno+"/"+(param.page||1)).done(function(data){ if(callback)callback(data.replyCnt,data.list,data.pageNum||param.page||1); }).fail(function(xhr,status){if(error && status!=="abort")error(xhr);});
  }
  function remove(rno) { return $.ajax({type:"DELETE",url:contextPath+"/replies/"+rno,dataType:"text"}); }
  function update(reply) { return $.ajax({type:"PUT",url:contextPath+"/replies/"+reply.rno,data:JSON.stringify(reply),contentType:"application/json;charset=UTF-8",dataType:"text"}); }
  function get(rno) { return $.getJSON(contextPath+"/replies/"+rno); }
  function displayTime(value) {
    var date = new Date(value);
    if (!Number.isFinite(date.getTime())) return "";
    return date.toLocaleString("ko-KR",{year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",hour12:false});
  }
  return {add:add,getList:getList,remove:remove,update:update,get:get,displayTime:displayTime};
})();
