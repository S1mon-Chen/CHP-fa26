// 可独立测试的展示投影：按事件序号去重，永不生成后端未提供的事件。
export function mergeEvents(current, incoming) {
  const events = new Map(current.map(event => [event.seq, event]));
  for (const event of incoming) if (!events.has(event.seq)) events.set(event.seq, event);
  return [...events.values()].sort((a, b) => a.seq - b.seq);
}
export function snapshot(events, cursor) {
  const visible = events.slice(0, cursor);
  const last = visible.at(-1);
  const candidate = last?.candidate || 0;
  const round = visible.filter(event => event.candidate === candidate);
  const proposalReady = round.some(event => event.event_type === 'model_response');
  const decision = round.some(event => event.event_type === 'rejected') ? 'rejected'
    : round.some(event => event.event_type === 'candidate_staged') ? 'applied'
    : round.some(event => event.event_type === 'accepted') ? 'accepted' : 'proposed';
  return {visible, last, candidate, proposalReady, decision,
    test: [...visible].reverse().find(event => event.event_type === 'test_result'),
    tokens: [...visible].reverse().find(event => event.event_type === 'model_response')?.tokens};
}
