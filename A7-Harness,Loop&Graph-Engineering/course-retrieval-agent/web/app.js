import {empty,accept,derive,phases,label} from './state.mjs';
const $=id=>document.getElementById(id);
function el(tag,text,cls){const n=document.createElement(tag);if(text!==undefined)n.textContent=String(text);if(cls)n.className=cls;return n;}
function clear(id){$(id).replaceChildren();return $(id);}
async function api(path,opts={}){const r=await fetch(path,opts);const data=await r.json();if(!r.ok)throw Error(data.detail||'接口请求失败');return data;}
let state=empty(),detail=null,runId=null,source=null,historyMode=false,timeline=[],position=0,timer=null,busy=false;
function message(e){$('error').textContent=e?.message||String(e||'');}
function closeStream(){if(source)source.close();source=null;}
function pause(){clearTimeout(timer);timer=null;}
function save(){if(runId)localStorage.setItem('course-run',JSON.stringify({runId,historyMode}));}
function modeControls(){const mode=$('mode').value;$('scenario').disabled=mode!=='scripted';$('model').disabled=mode!=='live';$('start').disabled=busy||mode==='replay';if(!runId)$('modeBadge').textContent=label(mode);}
function render(){
 const d=derive(state),s=detail?.summary||{},cfg=detail?.config?.agent||{};
 const end=historyMode?position===timeline.length:!!detail?.complete;
 const summary=end?s:{};
 const mode=historyMode?'replay':detail?.config?.mode||$('mode').value;
 $('modeBadge').textContent=label(mode);
 $('identity').textContent=runId?`run_id：${runId} · 模型：${detail?.config?.mode==='live'?cfg.model:'预设响应'} · 快照：${d.snapshot||summary.corpus_snapshot_id||'等待加载'}`:'尚未选择运行';
 $('runQuestion').textContent=detail?.config?.question?'本次问题：'+detail.config.question:'';
 const last=d.last,phase=last?.phase;
 const done=new Set(d.rows.map(x=>x.event.phase));clear('phases');
 for(const p of phases)$('phases').append(el('span',`${p===phase?'▶ ':done.has(p)?'✓ ':''}${p}`,`phase ${p===phase?'current':done.has(p)?'done':''}`));
 const rounds=summary.decision_rounds??last?.decision_round??0,attempts=summary.tool_attempts??last?.tool_attempts??0;
 const execution=summary.execution_status||((!historyMode&&['INTERRUPTED','CORRUPT'].includes(s.execution_status))?s.execution_status:last?.type==='finished'?last.status:'RUNNING');
 const stats=summary.token_statistics||[];
 const known=stats.length&&stats.every(t=>Number.isInteger(t.input_tokens)&&Number.isInteger(t.output_tokens));
 const metrics=[`执行：${runId?execution:'等待开始'}`,`答案：${end?(summary.answer_status||'未生成'):'待校验'}`,`决策：${rounds}/${cfg.max_decisions??8} · 剩余 ${Math.max(0,(cfg.max_decisions??8)-rounds)}`,`工具：${attempts}/${cfg.max_tool_attempts??10} · 剩余 ${Math.max(0,(cfg.max_tool_attempts??10)-attempts)}`,`模型请求：${summary.model_requests??d.requestCount}`,`已记录耗时：${Number(summary.elapsed_seconds??last?.elapsed_seconds??0).toFixed(2)} 秒`,`Token：${known?stats.reduce((a,t)=>a+t.input_tokens+t.output_tokens,0):'未提供'}`];
 clear('metrics');for(const text of metrics)$('metrics').append(el('span',text,'metric'));
 $('waiting').textContent=detail?.notice||(end&&summary.error_code?'受控结束：'+summary.error_code:!historyMode&&last?.type==='model_request'?'模型请求中，等待真实响应…':'');
 clear('actions');for(const c of d.calls){const card=el('article');card.append(el('h4',c.name||'工具动作'),el('p',`调用 ID：${c.id}`,'meta'),el('p','① 模型提出动作'));card.append(el('pre',JSON.stringify(c.args,null,2)));card.append(el('p',`② Harness：${c.decision}${c.reason?' · '+c.reason:''}`,'status-label'));card.append(el('p',`③ 工具：${c.executed?'已实际执行':'未执行'}${c.duration!==undefined?' · '+c.duration.toFixed(3)+' 秒':''}`));if(c.result){card.append(el('p',c.result.ok?`结果：${c.result.hits?c.result.hits.length+' 个搜索命中':c.result.chunks?c.result.chunks.length+' 个读取片段':'返回文档列表'}`:`错误：${c.result.error?.code} · ${c.result.error?.message}`));const dt=el('details');dt.append(el('summary','完整工具观察'),el('pre',JSON.stringify(c.result,null,2)));card.append(dt);}$('actions').append(card);}
 clear('events');for(const item of d.rows){const e=item.event,dt=el('details');dt.append(el('summary',`#${e.seq} ${e.phase} · ${e.type} · ${e.error_code||e.status} · ${e.elapsed_seconds}s`),el('pre',JSON.stringify(e,null,2)));$('events').append(dt);}
 clear('hits');for(const h of d.hits){const card=el('article');card.append(el('h4',h.title+' / '+h.section_title),el('p',`得分 ${h.score} · 命中 ${h.matched_terms.join('、')}`),el('p',h.snippet,'evidence-text'),el('p',`${h.chunk_id} · 行 ${h.line_start}–${h.line_end}`,'meta'));$('hits').append(card);}if(!d.hits.length)$('hits').append(el('p','尚无搜索命中摘要'));
 const evidence=end?(detail?.evidence||{}):historyMode?Object.fromEntries(Object.keys(d.evidence).filter(id=>detail?.evidence?.[id]).map(id=>[id,detail.evidence[id]])):['INTERRUPTED','CORRUPT'].includes(s.execution_status)?{}:d.evidence;
 clear('evidence');for(const [id,e] of Object.entries(evidence)){const card=el('article');card.id='evidence-'+id;card.tabIndex=-1;card.append(el('h4',`${id} · ${e.title}`),el('p',e.text,'evidence-text'),el('p',`文档 ${e.document_id} · 片段 ${e.chunk_id} · 行 ${e.line_start}–${e.line_end}`,'meta'),el('p',`内容哈希 ${e.content_hash}`,'meta'),el('p',`快照 ${e.corpus_snapshot_id}`,'meta'));$('evidence').append(card);}if(!Object.keys(evidence).length)$('evidence').append(el('p','尚无已读取证据；搜索摘要不能替代。'));
 const answer=end?detail?.answer:d.answer;clear('answer');
 if(answer){$('answer').append(el('p',`答案状态：${answer.answer_status}`,'status-label'));for(const c of answer.claims){const p=el('div',undefined,'claim');p.append(el('span',c.text));for(const id of c.evidence_ids){const b=el('button',id,'citation');b.onclick=()=>{const target=$('evidence-'+id);if(!target){message(Error('本次记录未找到此证据，不能自动补齐'));return;}document.querySelectorAll('.highlight').forEach(n=>n.classList.remove('highlight'));target.classList.add('highlight');target.scrollIntoView({block:'center'});target.focus({preventScroll:true});};p.append(b);}$('answer').append(p);}for(const m of answer.missing_information)$('answer').append(el('p','缺失信息：'+m));}else $('answer').append(el('p','等待结构化答案与引用校验'));
 const errors=d.rows.filter(x=>x.event.type==='answer_rejected').map(x=>x.event.error_code);
 $('validation').textContent=`校验错误：${errors.join('、')||'暂无'} · 已进行修复：${summary.answer_repairs??Math.max(0,d.rows.filter(x=>x.event.phase==='FINALIZE'&&x.event.type==='model_request'&&!x.event.transport_retry).length-1)}`;
 $('replayPosition').textContent=historyMode?`历史事件 ${position}/${timeline.length}`:'';
 for(const id of ['rewind','step','play','pause','speed'])$(id).disabled=!historyMode;
}
async function finalReport(){const id=runId;const latest=await api('/api/runs/'+id);if(runId!==id)return;detail=latest;busy=false;modeControls();if(detail.summary.execution_status==='CORRUPT'){state=empty();message(Error(detail.notice));}render();await refreshHistory();}
async function openRun(id,replayMode=false,justStarted=false){pause();closeStream();runId=id;state=empty();historyMode=replayMode;message('');detail=await api('/api/runs/'+id);if(detail.complete&&!justStarted)historyMode=true;save();
 if(historyMode){if(!detail.complete)throw Error(detail.notice||'尚未完成，不能回放');const data=await api(`/api/runs/${id}/replay`);detail=data;timeline=data.timeline;position=replayMode?0:timeline.length;if(position)for(const item of timeline)accept(state,item);render();return;}
 render();source=new EventSource(`/api/runs/${id}/events?after=0`);
 source.addEventListener('harness',e=>{if(runId!==id)return;const item=JSON.parse(e.data);if(accept(state,item))render();});
 source.addEventListener('complete',async()=>{if(runId!==id)return;closeStream();try{await finalReport();}catch(e){message(e);}});
 source.addEventListener('damaged',()=>{closeStream();state=empty();message(Error('运行产物损坏，无法展示'));render();});
 source.onerror=()=>{$('waiting').textContent='事件连接中断，正在按序号重连…';};
}
async function viewReport(id){await openRun(id,true);position=timeline.length;for(const item of timeline)accept(state,item);render();}
async function refreshHistory(){const rows=await api('/api/runs');busy=rows.some(r=>r.active);modeControls();clear('history');for(const r of rows){const tr=el('tr');tr.append(el('td',(r.time||'未记录时间')+'\n'+r.question),el('td',(r.mode||'未知')+' / '+(r.model||'预设响应')),el('td',r.execution_status+' / '+(r.answer_status||'未生成')));const td=el('td'),open=el('button',r.active?'查看进行中':'查看报告');open.onclick=()=> (r.complete?viewReport(r.run_id):openRun(r.run_id)).catch(message);td.append(open);const replay=el('button','逐步回放');replay.disabled=!r.complete;replay.onclick=()=>openRun(r.run_id,true).catch(message);td.append(replay);tr.append(td);$('history').append(tr);}}
function step(){if(position<timeline.length){accept(state,timeline[position++]);render();}}
function play(){pause();if(position>=timeline.length)return;step();const current=timeline[position-1]?.event.elapsed_seconds||0,next=timeline[position]?.event.elapsed_seconds||current;timer=setTimeout(play,Math.max(80,(next-current)*1000)/Number($('speed').value));}
$('step').onclick=()=>{pause();step();};$('play').onclick=play;$('pause').onclick=pause;$('rewind').onclick=()=>{pause();position=0;state=empty();render();};$('refreshHistory').onclick=()=>refreshHistory().catch(message);$('mode').onchange=modeControls;
$('start').onclick=async()=>{busy=true;modeControls();message('');try{const data={request_id:crypto.randomUUID().replaceAll('-',''),question:$('question').value,mode:$('mode').value,scenario:$('scenario').value,max_decisions:Number($('decisions').value),max_tool_attempts:Number($('tools').value)};if(data.mode==='live')data.model=$('model').value;const started=await api('/api/runs',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});await openRun(started.run_id,false,true);await refreshHistory();}catch(e){busy=false;modeControls();message(e);}};
async function init(){const data=await api('/api/scenarios');for(const [value,name] of Object.entries(data.scenarios)){const option=el('option',name);option.value=value;$('scenario').append(option);}for(const text of data.examples){const b=el('button',text);b.onclick=()=>{$('question').value=text;};$('examples').append(b);}const status=await api('/api/status');$('availability').textContent=`资料集：${status.corpus.available?status.corpus.documents+' 份可见资料':status.corpus.error} ｜ Ollama：${status.ollama.available?'已连接':status.ollama.error}`;for(const m of status.ollama.models){const o=el('option');o.value=m;$('models').append(o);}if(status.default_model)$('model').value=status.default_model;busy=!!status.active_run_id;modeControls();await refreshHistory();let saved;try{saved=JSON.parse(localStorage.getItem('course-run'));}catch{}if(status.active_run_id)await openRun(status.active_run_id);else if(saved?.runId)await openRun(saved.runId,saved.historyMode).catch(message);else render();}
init().catch(message);
