export const ALGORITHMS = { BFS: { name: 'Breadth-first', color: '#42978e' }, DFS: { name: 'Depth-first', color: '#b77bcb' }, DIJKSTRA: { name: 'Dijkstra', color: '#d4984b' }, ASTAR: { name: 'A* search', color: '#6684cc' } };
export function blankGrid(width, height) {
  return { width, height, start: {x:0,y:0}, end: {x:width-1,y:height-1}, cells: Array.from({length:width*height}, (_,i) => ({x:i%width,y:Math.floor(i/width),walls:{north:true,east:true,south:true,west:true},visited:false})) };
}
export function carve(grid, {from,to}) {
  const a = grid.cells[from.y*grid.width+from.x], b = grid.cells[to.y*grid.width+to.x];
  const dx=to.x-from.x, dy=to.y-from.y;
  if (!a || !b || Math.abs(dx)+Math.abs(dy)!==1) throw new Error('Invalid wall-removal event');
  const [first,second]=dx===1?['east','west']:dx===-1?['west','east']:dy===1?['south','north']:['north','south'];
  a.walls[first]=false; b.walls[second]=false; a.visited=b.visited=true;
}
export class PlaybackQueue {
  constructor(){this.reset(null);}
  reset(requestId){this.requestId=requestId;this.frames=[];this.cursor=0;this.sequences=new Map();this.mazeId=null;this.receivedComplete=false;}
  push(frame){
    if(frame.requestId!==this.requestId || !this.requestId)return false;
    if(this.receivedComplete)throw new Error('Unexpected event after completion');
    if(frame.mazeId){if(this.mazeId && frame.mazeId!==this.mazeId)throw new Error('Maze changed within one request');this.mazeId=frame.mazeId;}
    if(frame.type==='step'){
      const next=this.sequences.get(frame.algorithm)||0;
      if(frame.sequence!==next)throw new Error('Missing or out-of-order animation step');
      this.sequences.set(frame.algorithm,next+1);
    }
    if(frame.type==='complete')this.receivedComplete=true;
    this.frames.push(frame);return true;
  }
  take(stepBudget){
    const result=[];let steps=0;
    while(this.cursor<this.frames.length){
      const frame=this.frames[this.cursor];
      if(frame.type==='step' && steps>=stepBudget)break;
      if(frame.type==='step')steps++;
      result.push(frame);this.cursor++;
    }
    if(this.cursor===this.frames.length){this.frames=[];this.cursor=0;}
    return result;
  }
  get pending(){return this.frames.length-this.cursor;}
}
export function sortedResults(results){return [...results].sort((a,b)=>a.stats.cellsExplored-b.stats.cellsExplored || a.stats.elapsedNanos-b.stats.elapsedNanos);}
