import assert from 'node:assert/strict';
import {mergeEvents, snapshot} from '../web/state.js';
const first={seq:1,candidate:1,node:'GENERATE',event_type:'entered',time:'original-time'};
const proposal={seq:2,candidate:1,node:'GENERATE',event_type:'model_response'};
const reject={seq:3,candidate:1,node:'VALIDATE',event_type:'rejected'};
let events=mergeEvents([first],[proposal,first,proposal]);
assert.equal(events.length,2);
events=mergeEvents(events,[reject,proposal]);
assert.deepEqual(events.map(x=>x.seq),[1,2,3]);
assert.equal(snapshot(events,1).proposalReady,false);
assert.equal(snapshot(events,2).decision,'proposed');
assert.equal(snapshot(events,3).decision,'rejected');
// 页面刷新恢复持久化事件，重复 SSE 补发不改变内容或时间。
const restored=mergeEvents(JSON.parse(JSON.stringify(events)),events);
assert.deepEqual(restored,events);
assert.equal(restored[0].time,'original-time');
assert.equal(snapshot(events,0).last,undefined);
console.log('Frontend projection: deduplication, reconnect, refresh, rejection and historical cursor passed');
