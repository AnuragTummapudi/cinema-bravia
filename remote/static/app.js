document.querySelectorAll('[data-icon]').forEach(node => {
  const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('aria-hidden','true');
  const use=document.createElementNS('http://www.w3.org/2000/svg','use');use.setAttribute('href',`/icons.svg#${node.dataset.icon}`);svg.appendChild(use);node.replaceChildren(svg);
});
const $ = id => document.getElementById(id);
const token = document.querySelector('meta[name="remote-token"]').content;
let state = null, settingsOpen = false, toastTimer, feedbackTimer, pollingTimer, sending = false, lastKeyTime = 0, busy = false;
const appNames = {'com.google.android.apps.tv.launcherx':'Google TV Home','com.google.android.youtube.tv':'YouTube','com.netflix.ninja':'Netflix','com.amazon.amazonvideo.livingroom':'Prime Video','com.android.tv.settings':'TV settings','com.sonyliv':'Sony LIV','com.sony.dtv.smartui':'Sony inputs','com.tummapudi.cinema':'Cinema'};
const statusNames = {connected:'Connected',connecting:'Connecting',reconnecting:'Reconnecting',pairing:'Code on your TV',pairing_required:'Pair your television',disconnected:'Disconnected'};

async function api(path, body) {
  const response = await fetch(path, {method: body === undefined ? 'GET':'POST', headers: {'Content-Type':'application/json','X-Remote-Token':token}, body: body === undefined ? undefined : JSON.stringify(body), signal:AbortSignal.timeout(16000)});
  let data;
  try { data = await response.json(); } catch { throw new Error('The local server could not complete the request. Reload this page.'); }
  if (!response.ok) throw new Error(data.error || 'The request could not be completed.');
  return data;
}
function toast(message) {
  $('toast').textContent = message; $('toast').hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => $('toast').hidden = true, 5000);
}
function render(next) {
  state = next;
  const pairingActive = next.status === 'pairing' && next.pairing_expires_in > 0;
  $('status-text').textContent = next.status==='pairing'&&!pairingActive?'Code expired':statusNames[next.status] || 'Disconnected';
  $('status-pill').className = 'status-pill ' + next.status;
  $('device-name').textContent = next.name;
  $('current-app').textContent = next.connected ? (appNames[next.current_app] || '') : '';
  $('connection-panel').hidden = next.connected && !settingsOpen;
  $('connection-toggle').setAttribute('aria-expanded', String(!$('connection-panel').hidden));
  $('connection-message').textContent = next.status === 'pairing' && !pairingActive ? 'The code expired. Pair again to display a new code on your television.' : next.message;
  $('pair-start-form').hidden = pairingActive;
  $('pair-code-form').hidden = !pairingActive;
  $('reconnect').hidden = !next.paired || next.status === 'pairing';
  if (next.status === 'pairing') $('pair-expiry').textContent = next.pairing_expires_in > 0 ? `Code expires in about ${next.pairing_expires_in} seconds.` : 'Code expired. Start pairing again.';
  if (document.activeElement !== $('tv-host')) $('tv-host').value = next.host;
  document.querySelectorAll('[data-control]').forEach(button => {
    const unsupported = next.unsupported_keys?.[button.dataset.key];
    button.disabled = !next.connected || Boolean(unsupported);
    if (unsupported) {button.title = unsupported; button.setAttribute('aria-description',unsupported);}
  });
  const volume = next.volume;
  $('volume-value').textContent = volume ? volume.level : '—';
  $('volume-max').textContent = volume && volume.max > 0 ? `/ ${volume.max}` : '';
  $('mute-label').textContent = volume?.muted ? 'Unmute' : 'Mute';
  document.querySelector('.mute-button').classList.toggle('is-muted', Boolean(volume?.muted));
  document.querySelector('.mute-button').setAttribute('aria-pressed', String(Boolean(volume?.muted)));
  document.querySelector('.mute-button').setAttribute('aria-label',volume?.muted?'Unmute':'Mute');
  $('power-state').textContent = next.is_on === true ? 'TV is on' : next.is_on === false ? 'TV is in standby' : 'TV state unavailable';
  document.querySelector('.power-button span').textContent = next.is_on === false ? 'Wake TV' : next.is_on === true ? 'Standby' : 'Power';
  $('transport-label').querySelector('span').textContent=next.connected?'Encrypted connection':'Local connection';
  const packages={netflix:'com.netflix.ninja',youtube:'com.google.android.youtube.tv',prime:'com.amazon.amazonvideo.livingroom',sonyliv:'com.sonyliv'};
  document.querySelectorAll('[data-app]').forEach(button=>button.classList.toggle('is-current',next.connected&&packages[button.dataset.app]===next.current_app));
}
async function poll() {
  try {render(await api('/api/state'));}
  catch { render({...state,host:state?.host || $('tv-host').value,name:state?.name || 'Sony BRAVIA BF1',paired:state?.paired || false,connected:false,status:'disconnected',message:'The local remote server is unavailable. Start BRAVIA Remote again, then reload this window.'}); $('status-text').textContent = 'Server unavailable'; }
  pollingTimer = setTimeout(poll, document.hidden ? 3000 : 900);
}
async function send(kind, value, source) {
  if (!state?.connected) {toast('Connect your television before using the remote.'); return;}
  if (sending) return;
  sending = true;
  source?.classList.add('pressed');
  try {
    const result = await api('/api/command', {kind,value});
    const label=source?.getAttribute('aria-label')||result.label.replaceAll('_',' ').toLowerCase();
    $('last-action').textContent = kind === 'text' ? 'Text sent' : `Sent: ${label}`;clearTimeout(feedbackTimer);feedbackTimer=setTimeout(()=>$('last-action').textContent='',2500);
    if (kind === 'text') $('tv-text').value = '';
  } catch (error) { toast(error.message); }
  finally { sending = false; setTimeout(() => source?.classList.remove('pressed'), 130); }
}
document.querySelectorAll('[data-key]').forEach(button => button.addEventListener('click', event => {send('key',button.dataset.key,button); if(event.detail > 0) button.blur();}));
document.querySelectorAll('[data-app]').forEach(button => button.addEventListener('click', event => {send('app',button.dataset.app,button); if(event.detail > 0) button.blur();}));
$('text-form').addEventListener('submit', event => {event.preventDefault(); send('text',$('tv-text').value,$('text-form').querySelector('button'));});
$('connection-toggle').addEventListener('click', () => {settingsOpen = !settingsOpen; if (state) render(state);});
async function pairingAction(action) {
  if (busy) return;
  busy = true; $('pair-error').hidden = true;
  const originalStart = $('pair-start').innerHTML, originalFinish = $('pair-finish').innerHTML;
  $('pair-start').textContent = 'Pairing…'; $('pair-finish').textContent = 'Connecting…';
  $('pair-start').disabled = true; $('pair-finish').disabled = true; $('reconnect').disabled = true;
  try {await action();}
  catch(error) {$('pair-error').textContent = error.message; $('pair-error').hidden = false;}
  finally {busy = false; $('pair-start').innerHTML = originalStart; $('pair-finish').innerHTML = originalFinish; $('pair-start').disabled = false; $('pair-finish').disabled = false; $('reconnect').disabled = false;}
}
$('pair-start-form').addEventListener('submit', event => {event.preventDefault(); pairingAction(async () => {render(await api('/api/pair/start',{host:$('tv-host').value.trim()})); $('pair-code').value = ''; $('pair-code').focus();});});
$('pair-code-form').addEventListener('submit', event => {event.preventDefault(); pairingAction(async () => {render(await api('/api/pair/finish',{code:$('pair-code').value.trim()})); $('pair-code').value = ''; settingsOpen = false;});});
$('reconnect').addEventListener('click', () => pairingAction(async () => render(await api('/api/reconnect',{}))));
const keyboard = {ArrowUp:'DPAD_UP',ArrowDown:'DPAD_DOWN',ArrowLeft:'DPAD_LEFT',ArrowRight:'DPAD_RIGHT',Enter:'DPAD_CENTER',Escape:'BACK',Backspace:'BACK',Home:'HOME',' ':'MEDIA_PLAY_PAUSE',h:'HOME',m:'VOLUME_MUTE',i:'TV_INPUT','+':'VOLUME_UP','=':'VOLUME_UP','-':'VOLUME_DOWN'};
document.addEventListener('keydown', event => {
  if(event.key==='Escape'&&settingsOpen&&state?.status!=='pairing'){event.preventDefault();settingsOpen=false;if(state)render(state);$('connection-toggle').focus();return;}
  if (event.isComposing || event.metaKey || event.ctrlKey || event.altKey || event.target.closest('input,textarea,select,[contenteditable="true"]')) return;
  const key = keyboard[event.key];
  if (!key || !state?.connected) return;
  if (state.unsupported_keys?.[key]) {event.preventDefault(); toast(state.unsupported_keys[key]); return;}
  // Preserve native Enter/Space activation for a focused button; never fire twice.
  if (event.target.closest('button,summary,a') && (event.key === 'Enter' || event.key === ' ')) return;
  event.preventDefault();
  if (event.key.startsWith('Arrow') && event.target.closest('button')) event.target.blur();
  if (event.repeat && !['DPAD_UP','DPAD_DOWN','DPAD_LEFT','DPAD_RIGHT','VOLUME_UP','VOLUME_DOWN'].includes(key)) return;
  if (performance.now()-lastKeyTime < 120) return;
  lastKeyTime = performance.now();
  send('key',key,document.querySelector(`[data-key="${key}"]`));
});
document.addEventListener('visibilitychange', () => {clearTimeout(pollingTimer); poll();});
document.querySelectorAll('[data-control]').forEach(button => button.disabled = true);
poll();
