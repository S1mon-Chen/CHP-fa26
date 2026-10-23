export const phases=['INIT','DECIDE','VALIDATE_TOOL','EXECUTE_TOOL','OBSERVE','FINALIZE','VALIDATE_ANSWER'];
export function empty(){return {rows:new Map()};}
export function accept(state,item){
 const seq=item.event.seq;
 if(!Number.isInteger(seq)||seq<1||state.rows.has(seq))return false;
 state.rows.set(seq,item);return true;
}
export function derive(state){
 const rows=[...state.rows.values()].sort((a,b)=>a.event.seq-b.event.seq);
 const calls=new Map(),hits=new Map(),evidence={};let answer=null,requestCount=0,rejections=0,snapshot=null;
 for(const {event:e,data:d} of rows){
  if(e.type==='model_request')requestCount++;
  if(e.type==='snapshot_loaded')snapshot=d?.corpus_snapshot_id;
  if(e.type==='answer_rejected')rejections++;
  if(e.type==='answer_accepted'){try{answer=JSON.parse(d.answer_content);}catch{answer=null;}}
  if(e.call_id){
   const key=e.decision_round+':'+e.call_id;
   if(!calls.has(key))calls.set(key,{id:e.call_id,round:e.decision_round,executed:false,decision:'等待校验',result:null});
   const c=calls.get(key);
   if(e.type==='action_proposed'){c.name=d?.original_call?.function?.name;c.args=d?.original_call?.function?.arguments;}
   if(e.type==='tool_accepted')c.decision='允许';
   if(e.type==='tool_rejected'||e.type==='call_error'){c.reason=e.error_code;if(!c.executed)c.decision='拒绝';}
   if(e.type==='tool_started'){c.executed=true;c.started=e.elapsed_seconds;}
   if(e.type==='tool_finished')c.duration=Math.max(0,e.elapsed_seconds-c.started);
   if(e.type==='tool_observation'){
    c.result=d?.result;
    for(const h of d?.result?.hits||[])hits.set(h.chunk_id,h);
    for(const chunk of d?.result?.chunks||[])if(chunk.evidence_id)evidence[chunk.evidence_id]=chunk;
   }
  }
 }
 return {rows,calls:[...calls.values()],hits:[...hits.values()],evidence,answer,requestCount,rejections,snapshot,last:rows.at(-1)?.event};
}
export function label(mode){return {live:'真实模型与真实工具',scripted:'预设模型响应与真实工具',replay:'历史轨迹回放'}[mode]||'尚未运行';}
