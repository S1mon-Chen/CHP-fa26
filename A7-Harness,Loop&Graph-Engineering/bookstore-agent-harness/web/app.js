import {mergeEvents, snapshot} from './state.js';
const $ = id => document.getElementById(id);
const nodes = ['LOAD','GENERATE','VALIDATE','APPLY','TEST','APPROVAL','DELIVER'];
const names = {LOAD:'读取任务',GENERATE:'模型建议',VALIDATE:'校验修改',APPLY:'应用候选',TEST:'固定测试',APPROVAL:'教师审批',DELIVER:'本地交付'};
const terminalStates = ['SUCCESS','FAILED','STALLED','MODEL_ERROR','EXECUTION_ERROR','REJECTED','WAITING_APPROVAL','RECOVERABLE','RECOVERY_REQUIRED'];
const modeNames = {live:'真实模型运行',scripted:'预设响应演示',replay:'历史轨迹回放'};
const decisions = {proposed:'模型建议 · 尚未校验',accepted:'Harness 已接受',rejected:'Harness 已拒绝',applied:'候选已应用'};
let token='', detail=null, artifacts={}, events=[], cursor=0, historical=false, stream=null, timer=null, busy=false, tab='candidate', requestKey=null, requestBody=null, selection=0, refreshChain=Promise.resolve();
const missing = value => value === null || value === undefined ? '未提供' : String(value);
async function api(path, options={}) {
  const response = await fetch(path,options); const value = await response.json();
  if(!response.ok) throw new Error(typeof value.detail === 'string' ? value.detail : JSON.stringify(value.detail));
  return value;
}
function error(exc) { $('error').textContent=exc.message; }
function pause() { clearInterval(timer); timer=null; $('play').textContent='自动播放'; }
function disconnect() { stream?.close();stream=null; }
function setBusy(value) {
  busy=value; ['engine','start','mode','scenario','model','budget','simulate','load','history'].forEach(id=>$(id).disabled=value);
  $('start').textContent=value?'运行进行中…':'开始运行 →'; if(!value)syncMode();
}
function syncMode() {
  const mode=$('mode').value;
  $('mode-label').textContent=modeNames[mode];
  $('scenario').disabled=busy||mode!=='scripted';$('model').disabled=busy||mode!=='live';
  $('budget').disabled=busy||mode==='replay';$('simulate').disabled=busy||mode!=='scripted';
  if(mode!=='scripted')$('simulate').checked=false;
  if(mode==='replay')$('deps').textContent='历史回放无需 Ollama 或 Docker';
}
async function dependencies() {
  $('check').disabled=true;$('deps').textContent='正在检查所需依赖…';
  try { const params=new URLSearchParams({mode:$('mode').value,simulate:$('simulate').checked,model:$('model').value});
    const result=await api('/api/status?'+params);$('deps').textContent=Object.entries(result.dependencies).map(([name,value])=>`${name}: ${value.message}`).join(' · ');
  }catch(exc){error(exc);}finally{$('check').disabled=false;}
}
function draw() {
  const view=snapshot(events,cursor), e=view.last, visible=view.visible;
  const finalState=!detail?.active&&cursor===events.length?detail?.summary?.status:null;
  const source=detail?.source_observation||detail?.observation||e?.observation;
  const isHistory=historical||detail?.observation==='historical_observation';
  const mode=isHistory?'replay':detail?.mode||$('mode').value;
  $('mode-label').textContent=modeNames[mode]||modeNames[$('mode').value];
  $('run-id').textContent='RUN ID · '+(detail?.run_id||'—');
  $('status').textContent=finalState|| (e?.event_type==='finished'?e.status:detail?.active?(e?.node||'准备中'):detail?.state==='INTERRUPTED'?'记录未完整结束':e?.node||'等待开始');
  $('status').className='badge'+((finalState|| (e?.event_type==='finished'?e.status:null))&& (finalState||e.status)!=='SUCCESS'?' warn':'');
  $('provenance').textContent=isHistory?`历史记录 · 本次未调用模型或执行工具 · 原观察：${source==='simulated'?'模拟结果':source==='docker'?'Docker 测试':'见原始记录'}`:source==='simulated'?'模拟结果 · 预设模型响应 + 预设工具结果，不能作为真实测试通过的证据':detail?'本次执行 · 候选由 Docker 隔离测试；尚未出现 TEST 结果时，不代表已测试':'尚未执行 · 请选择课堂场景';
  $('provenance').className='notice'+(source==='simulated'?' simulated':'');
  $('candidate').textContent=view.candidate||'—';
  $('remaining').textContent=missing(e?.remaining_candidates??(detail?.max_candidates!=null?Math.max(0,detail.max_candidates-view.candidate):null));
  $('tokens').textContent=view.tokens?`${missing(view.tokens.input)} / ${missing(view.tokens.output)}`:'未提供';
  $('flow').replaceChildren(...nodes.filter(node=>(detail?.engine||$('engine').value)==='langgraph'||!['APPROVAL','DELIVER'].includes(node)).map(node=>{const el=document.createElement('div');el.className='node'+(e?.node===node?' active':visible.some(x=>x.node===node&&(node==='LOAD'||x.candidate===view.candidate))?' seen':'');const title=document.createElement('b');title.textContent=node;el.append(title,document.createTextNode(names[node]));return el;}));
  $('terminals').replaceChildren(...terminalStates.map(state=>{const el=document.createElement('span');el.textContent=state;el.className=(finalState|| (e?.event_type==='finished'?e.status:null))===state?'selected':'';return el;}));
  const looping=e?.node==='GENERATE'&&view.candidate>1;
  $('loop').className='loop'+(looping?' active':'');$('loop').textContent=looping?`↶ 已进入第 ${view.candidate} 轮 GENERATE：上一轮反馈已进入模型请求。`:'TEST 失败 → 反馈 → GENERATE；继续前检查预算和重复失败。';
  const explanations={entered:`正在 ${names[e?.node]||e?.node}。这个节点来自后端进入事件。`,loaded:'任务已读取。Harness 确定本次允许的动作。',model_response:'模型提交了完整代码建议，Harness 尚需校验和测试。',accepted:'路径、长度和语法校验通过。接受建议不等于业务测试通过。',rejected:'请求被拦截：'+(e?.test_summary||'见事件详情'),candidate_staged:source==='simulated'?'候选已保存；本次工具观察为模拟结果。':'候选已保存，将在全新 Docker 副本中执行固定测试。',test_result:'测试观察已返回。失败会成为后续请求的结构化反馈。',finished:'运行结束。'+(e?.status==='STALLED'?'相同代码与相同失败达到重复阈值。':e?.status==='FAILED'?'候选预算已耗尽。':'最终报告已经落盘。'),model_error:'模型调用失败；由有限重试预算控制。',controller_error:'执行或回放校验失败，请检查错误信息。'};
  $('explain').textContent=e?explanations[e.event_type]||e.event_type:'选择一个实验开始。状态来自真实后端事件。';
  $('position').textContent=`${cursor} / ${events.length}`;$('position-label').textContent=`${events.length} 个事件`;$('scrub').max=events.length;$('scrub').value=cursor;
  ['play','next','restart','speed','scrub'].forEach(id=>$(id).disabled=!!detail?.active||!events.length);$('next').disabled=!!detail?.active||cursor>=events.length;
  const proposal=view.proposalReady?artifacts.proposals?.[view.candidate]:null;
  $('decision').textContent=proposal?decisions[view.decision]:'等待候选代码';$('decision').className='badge'+(view.decision==='rejected'?' warn':'');
  $('code-meta').textContent=`候选编号 · ${view.candidate||'—'}　代码哈希 · ${proposal?.code_hash||'—'}`;
  $('reason').textContent=proposal?`请求路径：${typeof proposal.path==='string'?proposal.path:'未提供'}${view.decision!=='proposed'?' · '+(proposal.reason||decisions[view.decision]):''}${proposal.truncated?' · 候选展示已截断':''}`:'';
  $('code').textContent=tab==='initial'?(artifacts.initial||'等待初始代码'):proposal?(tab==='diff'?proposal.diff||'与初始代码无差异':proposal.code??'结构化响应未提供有效源码'):'等待候选代码';
  const test=view.test, result=test?artifacts.tests?.[test.candidate]||test.details||{}:{};
  $('test-kind').textContent=test?(isHistory?'历史记录':source==='simulated'?'模拟结果':'本次真实测试'):'尚未测试';
  const stats=[['通过',result.counts?.passed],['失败',result.counts?.failed],['错误',result.counts?.errors],['退出码',result.exit_code],['执行耗时 ms',result.duration_ms],['输出截断',result.truncated==null?null:result.truncated?'是':'否']];
  $('test-stats').replaceChildren(...stats.map(([label,value])=>{const el=document.createElement('div');const small=document.createElement('span');small.textContent=label;const b=document.createElement('b');b.textContent=missing(value);el.append(small,b);return el;}));
  $('test-summary').textContent=test?`候选 ${test.candidate} · ${result.passed===true?'测试通过':result.error?'执行错误':result.passed===false?'测试失败':'结果见记录'}\n${result.error||''}\n${result.summary||test.test_summary||'未提供'}`:'等待测试观察';
  $('test-output').textContent=test?result.output||'未提供':'未提供';
  $('timeline').replaceChildren(...events.map((event,index)=>{const row=document.createElement('details');row.className='event'+(index===cursor-1?' selected':'');const title=document.createElement('summary');title.textContent=`#${event.seq}　${event.time}　${event.node} / ${event.event_type}　候选 ${event.candidate}　${event.status}　${event.elapsed_ms} ms${event.error_type?'　'+event.error_type:''}`;const reason=document.createElement('p');reason.textContent=event.test_summary||'无错误或拦截原因';const raw=document.createElement('pre');raw.className='light';raw.textContent=JSON.stringify(event,null,2);row.append(title,reason,raw);return row;}));
  if(detail?.operation_error)$('error').textContent=detail.operation_error.error;
  drawGraph();
  $('report').textContent=detail?.summary?JSON.stringify(detail.summary,null,2):'运行结束后，以持久化 summary.json 为准。';
}
async function history() {const list=await api('/api/runs'), selected=$('history').value;$('history').replaceChildren(new Option('选择轨迹…',''));for(const row of list)$('history').add(new Option(`${row.state} · ${row.observation==='simulated'?'模拟':row.mode||'历史'} · ${row.run_id.slice(0,8)}`,row.run_id));$('history').value=selected;}
async function updateArtifacts(id, version) {const value=await api(`/api/runs/${id}/artifacts`);if(version===selection){artifacts=value;if(value.task)$('task').textContent=value.task;draw();}}
function subscribe(id,version) {
  disconnect();const last=events.at(-1)?.seq||0;stream=new EventSource(`/api/runs/${id}/events?after=${last}`);
  stream.onopen=()=>{$('connection').textContent='SSE · 已连接，按序号同步';};
  stream.addEventListener('harness',message=>{if(version!==selection)return;const event=JSON.parse(message.data);events=mergeEvents(events,[event]);cursor=events.length;draw();if(['model_response','accepted','rejected','candidate_staged','test_result'].includes(event.event_type))refreshChain=refreshChain.catch(()=>{}).then(()=>updateArtifacts(id,version)).catch(error);});
  stream.addEventListener('complete',async()=>{if(version!==selection)return;disconnect();try{detail=await api('/api/runs/'+id);events=mergeEvents(events,detail.events);cursor=events.length;await updateArtifacts(id,version);setBusy(false);$('connection').textContent='SSE · 已完整接收；报告已落盘';draw();await loadCheckpoints();await history();}catch(exc){error(exc);}});
  stream.onerror=()=>{$('connection').textContent='SSE · 连接中断，正在按事件序号重连…';};
}
async function openRun(id, replay=false, fresh=false) {
  pause();disconnect();const version=++selection;detail=await api('/api/runs/'+id+(replay?'?historical=true':''));events=mergeEvents([],detail.events);historical=replay||(!detail.active&&!fresh&&!['WAITING_APPROVAL','RECOVERABLE','RECOVERY_REQUIRED'].includes(detail.state));cursor=replay?0:events.length;artifacts={};$('engine').value=detail.engine||'python';localStorage.setItem('classroom-run',id);await updateArtifacts(id,version);setBusy(detail.active);if(historical)$('mode').value='replay';else if(detail.mode)$('mode').value=detail.mode;syncMode();draw();await loadCheckpoints();if(detail.active)subscribe(id,version);else $('connection').textContent='历史记录 · 直接读取持久化事件';
}
async function start() {
  $('error').textContent='';pause();
  const mode=$('mode').value;
  const body={engine:$('engine').value,mode,scenario:$('scenario').value||'success',max_candidates:Number($('budget').value),simulate:$('simulate').checked};
  if(mode==='live')body.model=$('model').value.trim();
  if(mode==='replay'){if(!$('history').value)return error(new Error('请先从历史运行区选择记录'));body.source=$('history').value;body.simulate=false;}
  const serialized=JSON.stringify(body);if(serialized!==requestBody){requestBody=serialized;requestKey=crypto.randomUUID().replaceAll('-','');}
  body.request_id=requestKey;setBusy(true);$('connection').textContent=mode==='replay'?'正在读取历史记录…':'启动前校验参数与依赖…';
  try {const result=await api('/api/runs',{method:'POST',headers:{'Content-Type':'application/json','X-Classroom-Token':token},body:JSON.stringify(body)});requestBody=null;requestKey=null;await openRun(result.run_id,result.replay,true);}
  catch(exc){setBusy(false);error(exc);}
}
function markReplay(){historical=true;$('mode').value='replay';syncMode();}
function advance(){markReplay();cursor=Math.min(cursor+1,events.length);draw();if(cursor===events.length)pause();}
function play(){markReplay();if(timer){pause();return;}if(cursor>=events.length)cursor=0;$('play').textContent='暂停';timer=setInterval(advance,Number($('speed').value));}
$('start').onclick=start;$('mode').onchange=()=>{syncMode();if($('mode').value!=='replay')$('deps').textContent='Docker / Ollama：尚未检查；启动前会检查所需依赖。';};$('simulate').onchange=()=>{$('deps').textContent='执行依赖变化，请重新检查；启动时也会检查。';};$('check').onclick=dependencies;
$('load').onclick=()=>{if(!$('history').value)return error(new Error('请先选择历史记录'));openRun($('history').value,true).catch(error);};$('refresh-history').onclick=()=>history().catch(error);
$('play').onclick=play;$('next').onclick=()=>{pause();advance();};$('restart').onclick=()=>{pause();markReplay();cursor=0;draw();};$('scrub').oninput=()=>{pause();markReplay();cursor=Number($('scrub').value);draw();};$('speed').onchange=()=>{if(timer){pause();play();}};
document.querySelectorAll('[data-tab]').forEach(button=>button.onclick=()=>{tab=button.dataset.tab;document.querySelectorAll('[data-tab]').forEach(b=>b.setAttribute('aria-selected',String(b===button)));draw();});
(async()=>{try{const boot=await api('/api/bootstrap');token=boot.token;$('model').value=boot.model;$('task').textContent=boot.task;const scenes=await api('/api/scenarios');scenes.forEach(s=>$('scenario').add(new Option(s.label,s.id)));syncMode();draw();await loadCheckpoints();await history();const saved=boot.active_run_id||localStorage.getItem('classroom-run');if(saved){try{await openRun(saved,false);}catch(exc){localStorage.removeItem('classroom-run');error(exc);}}}catch(exc){error(exc);}})();

let checkpointRows=[];
function drawGraph() {
  const graph=detail?.graph, state=graph?.state;
  $('graph-panel').hidden=!detail?.graph;
  if(!state)return;
  $('lineage').textContent=`线程 ${state.thread_id} · 父运行 ${state.parent_run_id||'无'} · 分支原因 ${state.branch_reason||'无'}`;
  $('recovery-reason').textContent=detail.operation_error?.error||detail.summary?.recovery_reason||'';
  const waiting=graph.approval_pending&&detail.state==='WAITING_APPROVAL';
  $('approval-panel').hidden=!waiting;
  $('approval-info').textContent=JSON.stringify(state.approval_request,null,2);
  ['approve','reject'].forEach(id=>$(id).disabled=busy||historical||!waiting);
  $('resume').disabled=busy||historical||!['RECOVERABLE','RECOVERY_REQUIRED'].includes(detail.state);
  $('fork').disabled=busy||!checkpointRows.some(c=>c.checkpoint_id===$('checkpoints').value&&c.safe_to_fork);
  if(waiting&&historical)$('recovery-reason').textContent='当前为只读回放。请点击历史区“打开 / 恢复审批”操作原运行。';
}
async function loadCheckpoints(){
  checkpointRows=[];
  if(!detail?.graph)return;
  checkpointRows=await api(`/api/runs/${detail.run_id}/checkpoints`);
  const selected=$('checkpoints').value;
  $('checkpoints').replaceChildren(...checkpointRows.map(c=>new Option(`${c.label} · 候选 ${c.candidate??'—'} · 测试 ${c.test_status||'未测试'} · ${c.time}`,c.checkpoint_id)));
  if(checkpointRows.some(c=>c.checkpoint_id===selected))$('checkpoints').value=selected;
  checkpointInfo();drawGraph();
}
function checkpointInfo(){const c=checkpointRows.find(c=>c.checkpoint_id===$('checkpoints').value);$('checkpoint-detail').textContent=c?`${c.checkpoint_id} · ${c.safe_to_fork?'允许独立分支':'只读历史边界'}`:'';drawGraph();}
async function graphAction(action,body){
  $('error').textContent='';setBusy(true);
  try{const result=await api(`/api/runs/${detail.run_id}/${action}`,{method:'POST',headers:{'Content-Type':'application/json','X-Classroom-Token':token},...(body?{body:JSON.stringify(body)}:{})});await openRun(result.run_id,false,true);}
  catch(exc){setBusy(false);error(exc);drawGraph();}
}
$('checkpoints').onchange=checkpointInfo;
$('approve').onclick=() => graphAction('approval',{run_id:detail.run_id,approval_id:detail.graph.state.approval_request.approval_id,code_hash:detail.graph.state.code_hash,approved:true,note:$('approval-note').value});
$('reject').onclick=() => graphAction('approval',{run_id:detail.run_id,approval_id:detail.graph.state.approval_request.approval_id,code_hash:detail.graph.state.code_hash,approved:false,note:$('approval-note').value});
$('resume').onclick=()=>graphAction('resume');
$('fork').onclick=()=>graphAction('fork',{checkpoint_id:$('checkpoints').value,reason:$('fork-reason').value,max_candidates:Number($('budget').value),model:$('fork-model').value.trim()||null});
$('refresh-graph').onclick=()=>openRun(detail.run_id,false,true).catch(error);
$('inspect-run').onclick=()=>{if($('history').value)openRun($('history').value,false,true).catch(error);};

$('engine').onchange=draw;
