<script setup lang="ts">
import {computed,onMounted,onBeforeUnmount,ref,watch} from 'vue'; import axios from 'axios'; import {VueFlow,MarkerType} from '@vue-flow/core'; import {Background} from '@vue-flow/background'; import {Controls} from '@vue-flow/controls';
type Project={id:string,name:string,status:string,revision:number,workspacePath:string}; type Snapshot={project:Project,nodes:any[],edges:any[],runs:any[],events:any[]};
const projects=ref<Project[]>([]), selected=ref(''), snap=ref<Snapshot|null>(null), selectedNode=ref<any>(null), es=ref<EventSource|null>(null);
const consoleLogs=ref<string[]>([]), showConsole=ref(false), consoleEs=ref<EventSource|null>(null), creating=ref(false), running=ref(false);
const autoTimer=ref<number>(0);
const colors:any={PENDING:'#64748b',READY:'#2563eb',DISPATCHING:'#7c3aed',RUNNING:'#f59e0b',WAITING_EXTERNAL:'#d97706',WAITING_HUMAN:'#9333ea',COMPLETED:'#16a34a',FAILED:'#dc2626',BLOCKED:'#475569',SUPERSEDED:'#94a3b8'};

const statusMsg=computed(()=>{
  if(!snap.value||snap.value.project.status==='COMPLETED')return'';
  const gate=snap.value.nodes.find((n:any)=>n.status==='WAITING_HUMAN');
  if(gate)return{text:'等待审批: '+gate.title,type:'gate'};
  const busy=snap.value.nodes.find((n:any)=>n.status==='RUNNING'||n.status==='DISPATCHING'||n.status==='WAITING_EXTERNAL');
  if(busy)return{text:'正在执行: '+busy.title,type:'running'};
  return{text:'就绪，点击运行',type:'idle'};
});
const gateCount=computed(()=>snap.value?.nodes.filter((n:any)=>n.status==='WAITING_HUMAN').length||0);

const flowNodes=computed(()=>{if(!snap.value)return[];const stages=snap.value.nodes.filter(n=>n.type==='STAGE');const stageIndex=new Map(stages.map((s:any,i:number)=>[s.stageCode,i]));return snap.value.nodes.map((n:any)=>{const si=stageIndex.get(n.stageCode)??0;const children=snap.value!.nodes.filter(x=>x.parentNodeId===n.parentNodeId&&x.type!=='STAGE').sort((a,b)=>a.sortOrder-b.sortOrder);const ci=Math.max(0,children.findIndex(x=>x.id===n.id));return{id:n.id,position:n.type==='STAGE'?{x:si*310,y:20}:{x:si*310,y:150+ci*125},data:{label:n.title,status:n.status,type:n.type},style:{background:n.status==='WAITING_HUMAN'?'#9333ea':colors[n.status]||'#334155',color:'white',border:n.type==='GATE'?'3px solid #eab308':'1px solid #94a3b8',borderRadius:n.type==='STAGE'?'20px':'10px',width:'240px',padding:'12px',animation:n.status==='WAITING_HUMAN'?'pulse 1.5s ease-in-out infinite':'none'}}})});
const flowEdges=computed(()=>snap.value?.edges.map((e:any)=>({id:e.id,source:e.source,target:e.target,markerEnd:MarkerType.ArrowClosed,animated:true}))||[]);

async function loadProjects(){projects.value=(await axios.get('/api/projects')).data;if(!selected.value&&projects.value.length)select(projects.value[0].id)}
async function create(){creating.value=true;try{await axios.delete('/api/projects');const r=await axios.post('/api/projects',{name:'商品查询服务演示',lifecycleCode:'software-delivery',lifecycleVersion:'1.0.0',workspacePath:'../workspace/product-search-demo'});await loadProjects();select(r.data.id);}finally{creating.value=false}}
async function select(id:string){selected.value=id;await refresh();es.value?.close();es.value=new EventSource(`/api/projects/${id}/stream`);es.value.onmessage=refresh;['PROJECT_STARTED','STAGE_EXPANDED','NODE_READY','NODE_STARTED','NODE_WAITING','NODE_COMPLETED','NODE_FAILED','HUMAN_APPROVED','STAGE_COMPLETED','PROJECT_COMPLETED'].forEach(t=>es.value?.addEventListener(t,refresh));}
async function refresh(){if(selected.value)snap.value=(await axios.get(`/api/projects/${selected.value}/graph`)).data}
async function start(){running.value=true;autoTimer.value=window.setInterval(autoKick,6000);await kick();}
async function kick(){try{await axios.post(`/api/projects/${selected.value}/start`);await new Promise(r=>setTimeout(r,500));await refresh();}catch(e){}if(snap.value?.project.status==='COMPLETED')stopAuto();}
async function autoKick(){if(!snap.value||snap.value.project.status==='COMPLETED'){stopAuto();return;}const gate = snap.value.nodes.find((n:any)=>n.status==='WAITING_HUMAN');if(gate){stopAuto();return;}await kick();}
function stopAuto(){if(autoTimer.value){clearInterval(autoTimer.value);autoTimer.value=0;}running.value=false;}
async function approveNode(nodeId:string){await axios.post(`/api/projects/${selected.value}/nodes/${nodeId}/approve`);selectedNode.value=null;await refresh();if(snap.value&&snap.value.project.status!=='COMPLETED')start();}
async function approve(){if(selectedNode.value)await approveNode(selectedNode.value.id);}
async function connectLogStream(){const r=await axios.get('/api/logs',{params:{lines:50}});consoleLogs.value=r.data.lines||[];consoleEs.value?.close();consoleEs.value=new EventSource('/api/logs/stream');consoleEs.value.addEventListener('log',(e:any)=>{consoleLogs.value.push(e.data);if(consoleLogs.value.length>500)consoleLogs.value.splice(0,consoleLogs.value.length-500);});}
function toggleConsole(){showConsole.value=!showConsole.value;if(showConsole.value)connectLogStream();else{consoleEs.value?.close();consoleEs.value=null;}}
function nodeName(id:string|null|undefined):string{if(!id||!snap.value)return'';const n=snap.value.nodes.find((x:any)=>x.id===id);return n?n.title:'';}
onMounted(loadProjects);onBeforeUnmount(()=>{es.value?.close();consoleEs.value?.close();stopAuto();});
</script>
<template>
<div class="shell"><header><div><h1>Hive Delivery Graph</h1><p>Java 21 · LangGraph4j · OpenCode</p></div><div class="actions"><select v-model="selected" @change="select(selected)"><option v-for="p in projects" :value="p.id">{{p.name}} · {{p.status}}</option></select><button :disabled="creating" @click="create">{{creating?'创建中...':'创建演示项目'}}</button><button class="primary" :disabled="!selected" @click="running?stopAuto():start()">{{running?'⏳ 运行中...':'▶ 运行 / 继续'}}</button><a href="http://localhost:8080/?instance=delivery-control" target="_blank">Control Studio</a></div></header>
<div v-if="snap" class="meta"><b>{{snap.project.name}}</b><div v-if="statusMsg" class="status-banner" :class="statusMsg.type"><span v-if="gateCount>0" class="gate-dot">!</span>{{statusMsg.text}}</div><span>状态 {{snap.project.status}}</span><span>Revision {{snap.project.revision}}</span></div>
<main v-if="snap"><section class="canvas"><VueFlow :nodes="flowNodes" :edges="flowEdges" fit-view-on-init @node-click="(_,n)=>selectedNode=n.data?{...n.data,id:n.id}:null"><Background/><Controls/></VueFlow></section>
<aside><template v-if="selectedNode"><h2>{{selectedNode.label}}</h2><div class="pill">{{selectedNode.type}} · {{selectedNode.status}}</div><button v-if="selectedNode.status==='WAITING_HUMAN'" class="primary full pulse-btn" @click="approve">✓ 批准 Gate</button></template><template v-else><h2>执行时间线</h2><div class="event" v-for="e in snap.events.slice(0,40)" :key="e.id"><div class="event-left"><b>{{nodeName(e.nodeId)||'系统'}}</b><small>{{e.type}}</small></div><small class="event-time">{{new Date(e.createdAt).toLocaleTimeString()}}</small></div></template></aside></main>
<div v-else class="empty">创建或选择一个项目开始验证。</div>
<div class="console-bar" :class="{open:showConsole}"><div class="console-header" @click="toggleConsole"><span class="console-title">┤ 控制台日志</span><span class="console-badge">{{consoleLogs.length}}</span><span class="console-toggle">{{showConsole?'▼':'▲'}}</span></div><div v-show="showConsole" class="console-body"><div v-for="(l,i) in consoleLogs" :key="i" class="console-line">{{l}}</div></div></div></div>
</template>
