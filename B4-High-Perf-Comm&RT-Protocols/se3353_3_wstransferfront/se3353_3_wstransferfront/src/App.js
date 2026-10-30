import './App.css';
import $ from 'jquery'

var socket;

function setConnected(connected) {
  $("#connect").prop("disabled", connected);
  $("#disconnect").prop("disabled", !connected);
  if (connected) {
    $("#conversation").show();
  }
  else {
    $("#conversation").hide();
  }
  $("#greetings").html("");
}

function showGreeting(message) {
  $("#greetings").append("<tr><td>" + message + "</td></tr>");
}

function openSocket() {
  if (typeof (WebSocket) == "undefined") {
    alert("您的浏览器不支持WebSocket");
  } else {

    if (socket != null) {
      return;
    }

    //实现化WebSocket对象，指定要连接的服务器地址与端口  建立连接
    var userId = document.getElementById('name').value;
    var socketUrl = "ws://localhost:8080/transfer/" + userId;
    console.log(socketUrl);
    setConnected(true);

    socket = new WebSocket(socketUrl);
    //打开事件
    socket.onopen = function () {
      console.log("websocket已打开");
      //socket.send("这是来自客户端的消息" + location.href + new Date());
    };
    //获得消息事件
    socket.onmessage = function (msg) {
      var serverMsg = "收到服务端信息：" + msg.data;
      console.log(serverMsg);
      //发现消息进入    开始处理前端触发逻辑
      showGreeting(msg.data);
    };
    //关闭事件
    socket.onclose = function () {
      console.log("websocket已关闭");
    };
    //发生了错误事件
    socket.onerror = function () {
      console.log("websocket发生了错误");
    }
  }
}

function closeSocket() {
  if (socket === undefined || socket === null) {
    alert("请先连接");
    return;
  }
  socket.close();
  socket = null;
  setConnected(false);
}

function sendMessage() {
  if (socket === undefined || socket === null) {
    alert("请先连接");
    return;
  }
  if (typeof (WebSocket) == "undefined") {
    console.log("您的浏览器不支持WebSocket");
  } else {
    console.log("您的浏览器支持WebSocket");
    var msg = JSON.stringify({'userId': $("#name").val()});
    console.log(msg);
    // socket.send(msg);
    $.ajax({
      type: "get",
      url: "http://localhost:8080/send",
      dataType: 'jsonp' //【jsonp进行跨域请求 只支持get】
    });
  }
}

$(function () {
  $("form").on('submit', function (e) {
    e.preventDefault();
  });
  $( "#connect" ).click(function() { openSocket(); });
  $( "#disconnect" ).click(function() { closeSocket(); });
  $( "#send" ).click(function() { sendMessage(); });
});

function App() {
  return (
      <div className="App">
        <div id="main-content" className="container">
          <div className="row">
            <div className="col-md-6">
              <form className="form-inline">
                <div className="form-group">
                  <label htmlFor="connect">WebSocket connection:</label>
                  <button id="connect" className="btn btn-default" type="submit">Connect</button>
                  <button id="disconnect" className="btn btn-default" type="submit" disabled="disabled">Disconnect
                  </button>
                </div>
              </form>
            </div>
            <div className="col-md-6">
              <form className="form-inline">
                <div className="form-group">
                  <label htmlFor="name">What is your name?</label>
                  <input type="text" id="name" className="form-control" placeholder="Your name here..."/>
                </div>
                <button id="send" className="btn btn-default" type="submit">Send</button>
              </form>
            </div>
          </div>
          <div className="row">
            <div className="col-md-12">
              <table id="conversation" className="table table-striped">
                <thead>
                <tr>
                  <th>Greetings</th>
                </tr>
                </thead>
                <tbody id="greetings">
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
  );
}

export default App;
