import {ALGORITHMS, blankGrid, carve, PlaybackQueue, sortedResults} from './model.js';
const $=id=>document.getElementById(id);
const rates=[25,100,400,1600,8000];
const queue=new PlaybackQueue();
let socket, reconnectTimer, connectionTimer, connected=false, busy=false, paused=false, currentOperation=null;
let grid=null, mazeId=null, panels=[], results=[], lastTime=0, budget=0, painted=0, retry=0, dirty=true;
let initial=true;
const apiBase=window.MAZE_CONFIG?.apiBase;
const selected=()=>[...document.querySelectorAll('input[name=algorithm]:checked')].map(input=>input.value);
function notice(text,error=false){$('notice').textContent=text;$('notice').classList.toggle('error',error);}
function connection(label,state){$('connection').dataset.state=state;$('connection').querySelector('span').textContent=label;}
function controls(){
  $('generate').disabled=!connected||busy;
  $('run').disabled=!connected||busy||!mazeId||!grid||selected().length===0;
  $('pause').disabled=!busy;
  for(const input of document.querySelectorAll('#generate-form input,#generate-form select,#randomize,input[name=algorithm]'))input.disabled=busy;
  $('run').innerHTML=selected().length===1?'Solve maze <span>→</span>':`Race ${selected().length} algorithms <span>→</span>`;
  $('pause').textContent=paused?'▶ Resume':'Ⅱ Pause';
}
function makePanel(algorithm=null,empty=false){
  const panel={algorithm,explored:[],path:[],stats:null};
  const root=document.createElement('article');root.className='panel';
  if(algorithm){const heading=document.createElement('div');heading.className='panel-head';heading.innerHTML=`<span class="panel-name"><i class="swatch ${algorithm.toLowerCase()}"></i>${ALGORITHMS[algorithm].name}</span><span class="panel-stat">Waiting</span>`;root.append(heading);panel.counter=heading.querySelector('.panel-stat');}
  panel.canvas=document.createElement('canvas');panel.canvas.className='maze-canvas';panel.canvas.setAttribute('role','img');panel.canvas.setAttribute('aria-label',algorithm?`${ALGORITHMS[algorithm].name} maze exploration`:'Maze grid');root.append(panel.canvas);
  if(empty){const overlay=document.createElement('div');overlay.className='empty-overlay';overlay.innerHTML='<div class="empty-icon">⌘</div><b>A little space for discovery.</b><span>Generate a maze to begin.</span>';root.append(overlay);}
  $('panels').append(root);panels.push(panel);dirty=true;
}
function setPanels(algorithms=[],empty=false){$('panels').replaceChildren();panels=[];$('panels').classList.toggle('racing',algorithms.length>1);if(algorithms.length)algorithms.forEach(a=>makePanel(a));else makePanel(null,empty);}
function clearResults(){results=[];$('results').hidden=true;$('results-body').replaceChildren();}
function start(operation,fields){
  if(!connected||busy)return;
  const requestId=crypto.randomUUID();queue.reset(requestId);busy=true;paused=false;budget=0;painted=0;currentOperation=operation;clearResults();
  if(operation==='generate'){grid=blankGrid(fields.width,fields.height);mazeId=null;setPanels();$('stage-label').textContent='CARVING A MAZE';$('grid-meta').textContent=`${fields.width} × ${fields.height} / SEED ${fields.seed}`;}
  else{setPanels(operation==='solve'?[fields.algorithm]:fields.algorithms);$('stage-label').textContent=operation==='race'?'THE RACE IS ON':'FOLLOWING THE SEARCH';}
  notice(operation==='generate'?'Opening one wall at a time…':'Different strategies. The exact same maze.');$('progress').textContent='Waiting for steps…';controls();
  socket.send(JSON.stringify({requestId,operation,...fields}));
}
function generate(){
  const seed=Number($('seed').value),size=Number($('size').value);
  if(!Number.isSafeInteger(seed)||seed< -2147483648||seed>2147483647){notice('Enter a whole-number seed between −2,147,483,648 and 2,147,483,647.',true);return;}
  initial=false;start('generate',{algorithm:$('generator').value,width:size,height:size,seed});
}
function run(){const algorithms=selected();if(!algorithms.length)return;start(algorithms.length===1?'solve':'race',algorithms.length===1?{mazeId,algorithm:algorithms[0]}:{mazeId,algorithms});}
function cancelPlayback(){queue.reset(null);busy=false;paused=false;budget=0;currentOperation=null;controls();}
function fail(message){const wasGenerating=currentOperation==='generate';cancelPlayback();clearResults();if(wasGenerating){mazeId=null;grid=null;setPanels([],true);}else if(grid){setPanels();}$('stage-label').textContent='PLAYBACK INTERRUPTED';$('progress').textContent='Ready to retry';notice(message,true);dirty=true;controls();}
function connect(){
  clearTimeout(reconnectTimer);clearTimeout(connectionTimer);
  if(!apiBase){connection('Not configured','offline');notice('The backend address is missing from this deployment.',true);return;}
  let url;
  try{url=new URL('/ws/maze',apiBase);url.protocol=url.protocol==='https:'?'wss:':'ws:';}
  catch{connection('Not configured','offline');notice('The backend address is invalid.',true);return;}
  const current=new WebSocket(url);socket=current;
  connection(retry?'Reconnecting…':'Waking the lab…','connecting');
  connectionTimer=setTimeout(()=>{if(current===socket && current.readyState!==WebSocket.OPEN)current.close();},25000);
  current.onopen=()=>{if(socket!==current)return;clearTimeout(connectionTimer);connected=true;retry=0;connection('Lab connected','connected');controls();if(initial)generate();else if(!busy)notice(mazeId?'Reconnected. Your maze is ready to explore.':'Connected. Generate a maze to begin.');};
  current.onmessage=event=>{
    if(socket!==current)return;
    try{const frame=JSON.parse(event.data);if(frame.requestId!==queue.requestId)return;
      if(frame.type==='error'){
        if(frame.data.code==='MAZE_NOT_FOUND'){mazeId=null;fail('This maze expired while the server was asleep. Generate it again with the same seed.');}
        else fail(frame.data.message||'The request failed. Please try again.');return;
      }
      queue.push(frame);
    }catch{fail('The stream was interrupted or out of order. Please generate or run again.');current.close();}
  };
  current.onerror=()=>{};
  current.onclose=()=>{
    if(socket!==current)return;clearTimeout(connectionTimer);connected=false;connection('Reconnecting…','offline');
    // A fully received stream can finish playing even if the idle socket closes.
    if(busy&&!queue.receivedComplete)fail('Connection lost. Reconnecting… Your incomplete animation has been cleared.');
    else if(!busy)notice('Reconnecting to the backend. A sleeping server can take about a minute to wake.');
    controls();reconnectTimer=setTimeout(connect,Math.min(1000*2**retry++,10000));
  };
}
function apply(frame){
  if(frame.type==='started')return;
  if(frame.type==='step'){
    if(currentOperation==='generate')carve(grid,frame.data);
    else{const panel=panels.find(p=>p.algorithm===frame.algorithm);if(!panel)throw new Error('Unknown algorithm');panel.explored.push(frame.data);panel.counter.textContent=`${panel.explored.length} explored`;}
    painted++;$('progress').textContent=currentOperation==='generate'?`${painted} / ${grid.width*grid.height-1} walls carved`:`${painted} exploration steps`;dirty=true;
  }else if(frame.type==='algorithm_complete'){
    const panel=panels.find(p=>p.algorithm===frame.algorithm);if(!panel)throw new Error('Unknown algorithm');panel.path=frame.data.finalPath;panel.stats=frame.data.stats;panel.counter.textContent=`${panel.explored.length} explored · done`;results.push({algorithm:frame.algorithm,...frame.data});dirty=true;
  }else if(frame.type==='complete'){
    if(currentOperation==='generate'){grid=frame.data;mazeId=frame.mazeId;notice('Your maze is ready. Choose an explorer, or race a few side by side.');$('stage-label').textContent='MAZE READY';$('progress').textContent=`${grid.width*grid.height} connected cells`;}
    else{showResults();$('stage-label').textContent='EXPLORATION COMPLETE';$('progress').textContent='Every route, revealed';notice('A perfect maze has one route between any two cells. The difference is how much each algorithm explores.');}
    busy=false;paused=false;currentOperation=null;controls();dirty=true;
  }
}
function showResults(){
  const sorted=sortedResults(results);$('results-body').replaceChildren();
  for(const [index,result] of sorted.entries()){
    const row=document.createElement('tr');if(index===0&&sorted.length>1)row.className='best';
    const name=document.createElement('td');name.innerHTML=`<i class="swatch ${result.algorithm.toLowerCase()}"></i>${ALGORITHMS[result.algorithm].name}${index===0&&sorted.length>1?'<span class="best-label">FEWEST EXPLORED</span>':''}`;row.append(name);
    for(const text of [result.stats.cellsExplored.toLocaleString(),`${result.stats.pathLength.toLocaleString()} moves`,`${(result.stats.elapsedNanos/1e6).toFixed(3)} ms`]){const cell=document.createElement('td');cell.textContent=text;row.append(cell);}$('results-body').append(row);
  }$('results').hidden=false;
}
function draw(panel){
  const drawing=grid||blankGrid(Number($('size').value),Number($('size').value));
  const canvas=panel.canvas,cssWidth=canvas.getBoundingClientRect().width;
  if(!cssWidth)return;
  const scale=Math.min(devicePixelRatio||1,2),pixels=Math.round(cssWidth*scale);if(canvas.width!==pixels||canvas.height!==pixels){canvas.width=pixels;canvas.height=pixels;}
  const ctx=canvas.getContext('2d');ctx.setTransform(scale,0,0,scale,0,0);ctx.clearRect(0,0,cssWidth,cssWidth);
  const pad=14,step=(cssWidth-pad*2)/drawing.width,top=(cssWidth-step*drawing.height)/2;
  ctx.fillStyle='#fcfdf8';ctx.fillRect(0,0,cssWidth,cssWidth);
  const fill=(cell,color,inset=0)=>{ctx.fillStyle=color;ctx.fillRect(pad+cell.x*step+inset,top+cell.y*step+inset,step-inset*2,step-inset*2);};
  if(currentOperation==='generate')for(const cell of drawing.cells)if(cell.visited)fill(cell,'#edf2d8');
  panel.explored.forEach((cell,i)=>{ctx.globalAlpha=.18+.5*(i+1)/Math.max(panel.explored.length,1);fill(cell,ALGORITHMS[panel.algorithm].color);});ctx.globalAlpha=1;
  for(const cell of panel.path)fill(cell,'#dae8a5',Math.max(.3,step*.07));
  ctx.strokeStyle='#52664b';ctx.lineWidth=step<5?.65:1.1;ctx.lineCap='square';ctx.beginPath();
  for(const cell of drawing.cells){const x=pad+cell.x*step,y=top+cell.y*step;if(cell.walls.north){ctx.moveTo(x,y);ctx.lineTo(x+step,y);}if(cell.walls.west){ctx.moveTo(x,y);ctx.lineTo(x,y+step);}if(cell.y===drawing.height-1&&cell.walls.south){ctx.moveTo(x,y+step);ctx.lineTo(x+step,y+step);}if(cell.x===drawing.width-1&&cell.walls.east){ctx.moveTo(x+step,y);ctx.lineTo(x+step,y+step);}}ctx.stroke();
  if(panel.path.length>1){ctx.strokeStyle='#36513a';ctx.lineWidth=Math.max(1.2,step*.18);ctx.lineJoin='round';ctx.lineCap='round';ctx.beginPath();panel.path.forEach((cell,i)=>{const x=pad+(cell.x+.5)*step,y=top+(cell.y+.5)*step;i?ctx.lineTo(x,y):ctx.moveTo(x,y);});ctx.stroke();}
  const marker=(cell,color)=>{const radius=Math.max(1,step*.24);ctx.beginPath();ctx.arc(pad+(cell.x+.5)*step,top+(cell.y+.5)*step,radius,0,Math.PI*2);ctx.fillStyle=color;ctx.fill();};marker(drawing.start,'#73954b');marker(drawing.end,'#e18a65');
  canvas.setAttribute('aria-label',`${panel.algorithm?ALGORITHMS[panel.algorithm].name+': ':''}${drawing.width} by ${drawing.height} maze, ${panel.explored.length} cells explored${panel.stats?', final path '+panel.stats.pathLength+' moves':''}`);
}
function tick(time){
  const delta=Math.min((time-lastTime)/1000,.1);lastTime=time;
  if(busy&&!paused){budget=Math.min(budget+delta*rates[Number($('speed').value)],rates[Number($('speed').value)]*.15);const frames=queue.take(Math.floor(budget));budget-=frames.filter(f=>f.type==='step').length;try{frames.forEach(apply);}catch{fail('Unable to replay this stream. Please generate the maze again.');}}
  if(dirty){panels.forEach(draw);dirty=false;}requestAnimationFrame(tick);
}
$('generate-form').addEventListener('submit',event=>{event.preventDefault();generate();});$('run').addEventListener('click',run);
$('size').addEventListener('change',()=>{$('cell-count').textContent=`${(Number($('size').value)**2).toLocaleString()} cells`;if(!grid)dirty=true;});
$('generator').addEventListener('change',()=>{$('generator-note').textContent={DFS:'Long corridors, unexpected turns.',PRIM:'Branching paths that grow from a single cell.',KRUSKAL:'Separate passages, connected into one maze.'}[$('generator').value];});
$('randomize').addEventListener('click',()=>{$('seed').value=crypto.getRandomValues(new Uint32Array(1))[0]%2147483648;});
document.querySelectorAll('input[name=algorithm]').forEach(input=>input.addEventListener('change',controls));
$('speed').addEventListener('input',()=>{$('speed-value').textContent=`${rates[Number($('speed').value)].toLocaleString()} steps/s`;budget=0;});
$('pause').addEventListener('click',()=>{paused=!paused;controls();});
$('reset').addEventListener('click',()=>{initial=false;cancelPlayback();grid=null;mazeId=null;clearResults();setPanels([],true);$('stage-label').textContent='YOUR PLAYGROUND';$('grid-meta').textContent=`${$('size').value} × ${$('size').value} / SEED ${$('seed').value}`;$('progress').textContent='Ready when you are';notice('A clean slate. Generate a new maze when you’re ready.');const old=socket;socket=null;old?.close();connected=false;controls();connect();});
new ResizeObserver(()=>{dirty=true;}).observe($('panels'));
setPanels([],true);controls();requestAnimationFrame(tick);connect();
