<script setup lang="ts">
import {computed,onMounted,onBeforeUnmount,ref,watch} from 'vue'; import axios from 'axios'; import {VueFlow,MarkerType} from '@vue-flow/core'; import {Background} from '@vue-flow/background'; import {Controls} from '@vue-flow/controls';
type Project={id:string,name:string,status:string,revision:number,workspacePath:string}; type Snapshot={project:Project,nodes:any[],edges:any[],runs:any[],events:any[]};
const projects=ref<Project[]>([]), selected=ref(''), snap=ref<Snapshot|null>(null), selectedNode=ref<any>(null), es=ref<EventSource|null>(null);
const consoleLogs=ref<string[]>([]), showConsole=ref(false), consoleEs=ref<EventSource|null>(null), creating=ref(false), running=ref(false);
const autoTimer=ref<number>(0);
const nodeSessions=ref<Record<string,any>>({}), loadingSession=ref('');
const newRequirement=ref(''), creatingReq=ref(false);
const changeDesc=ref(''), submittingChange=ref(false);
const colors:any={PENDING:'#64748b',READY:'#2563eb',DISPATCHING:'#7c3aed',RUNNING:'#f59e0b',WAITING_EXTERNAL:'#d97706',WAITING_HUMAN:'#9333ea',COMPLETED:'#16a34a',FAILED:'#dc2626',BLOCKED:'#475569',SUPERSEDED:'#94a3b8'};

const statusMsg=computed(()=>{
  if(!snap.value||snap.value.project.status==='COMPLETED')return'';
  const gate=snap.value.nodes.find((n:any)=>n.status==='WAITING_HUMAN');
  if(gate)return{text:'等待审批: '+gate.title,type:'gate'};
  const busy=snap.value.nodes.find((n:any)=>n.status==='RUNNING'||n.status==='DISPATCHING'||n.status==='WAITING_EXTERNAL');
  if(busy)return{text:'正在执行: '+busy.title,type:'running'};
  return{text:'就绪',type:'idle'};
});
const gateCount=computed(()=>snap.value?.nodes.filter((n:any)=>n.status==='WAITING_HUMAN').length||0);

const flowNodes=computed(()=>{if(!snap.value)return[];const stages=snap.value.nodes.filter(n=>n.type==='STAGE');const stageIndex=new Map(stages.map((s:any,i:number)=>[s.stageCode,i]));return snap.value.nodes.map((n:any)=>{const si=stageIndex.get(n.stageCode)??0;const children=snap.value!.nodes.filter(x=>x.parentNodeId===n.parentNodeId&&x.type!=='STAGE').sort((a,b)=>a.sortOrder-b.sortOrder);const ci=Math.max(0,children.findIndex(x=>x.id===n.id));return{id:n.id,position:n.type==='STAGE'?{x:si*310,y:20}:{x:si*310,y:150+ci*125},data:{label:n.title,status:n.status,type:n.type,description:n.description,criteria:n.acceptanceCriteria||[],executor:n.executorType,handler:n.handler,agentRole:n.agentRole,stageCode:n.stageCode,nodeId:n.id},style:{background:n.status==='WAITING_HUMAN'?'#9333ea':colors[n.status]||'#334155',color:'white',border:n.type==='GATE'?'3px solid #eab308':'1px solid #94a3b8',borderRadius:n.type==='STAGE'?'20px':'10px',width:'240px',padding:'12px',animation:n.status==='WAITING_HUMAN'?'pulse 1.5s ease-in-out infinite':'none'}}})});
const flowEdges=computed(()=>snap.value?.edges.map((e:any)=>({id:e.id,source:e.source,target:e.target,markerEnd:MarkerType.ArrowClosed,animated:true}))||[]);

async function loadProjects(){projects.value=(await axios.get('/api/projects')).data;if(!selected.value&&projects.value.length)select(projects.value[0].id)}
async function create(){creating.value=true;try{await axios.delete('/api/projects');const r=await axios.post('/api/projects',{name:'商品查询服务',lifecycleCode:'software-delivery',lifecycleVersion:'1.0.0',workspacePath:'../workspace/product-search-demo',requirement:newRequirement.value||'为商品查询API添加品牌模糊搜索和分页功能'});await loadProjects();select(r.data.id);newRequirement.value='';}finally{creating.value=false}}
async function select(id:string){selected.value=id;await refresh();es.value?.close();es.value=new EventSource(`/api/projects/${id}/stream`);es.value.onmessage=refresh;['PROJECT_STARTED','STAGE_EXPANDED','NODE_READY','NODE_STARTED','NODE_WAITING','NODE_COMPLETED','NODE_FAILED','HUMAN_APPROVED','STAGE_COMPLETED','PROJECT_COMPLETED'].forEach(t=>es.value?.addEventListener(t,refresh));}
async function refresh(){if(selected.value)snap.value=(await axios.get(`/api/projects/${selected.value}/graph`)).data}
async function start(){running.value=true;autoTimer.value=window.setInterval(autoKick,6000);await kick();}
async function kick(){try{await axios.post(`/api/projects/${selected.value}/start`);await new Promise(r=>setTimeout(r,500));await refresh();}catch(e){}if(snap.value?.project.status==='COMPLETED')stopAuto();}
async function autoKick(){if(!snap.value||snap.value.project.status==='COMPLETED'){stopAuto();return;}const gate=snap.value.nodes.find((n:any)=>n.status==='WAITING_HUMAN');if(gate){stopAuto();return;}await kick();}
function stopAuto(){if(autoTimer.value){clearInterval(autoTimer.value);autoTimer.value=0;}running.value=false;}
async function approveNode(nodeId:string){await axios.post(`/api/projects/${selected.value}/nodes/${nodeId}/approve`);selectedNode.value=null;await refresh();if(snap.value&&snap.value.project.status!=='COMPLETED')start();}
async function approve(){if(selectedNode.value)await approveNode(selectedNode.value.id);}
async function connectLogStream(){const r=await axios.get('/api/logs',{params:{lines:50}});consoleLogs.value=r.data.lines||[];consoleEs.value?.close();consoleEs.value=new EventSource('/api/logs/stream');consoleEs.value.addEventListener('log',(e:any)=>{consoleLogs.value.push(e.data);if(consoleLogs.value.length>500)consoleLogs.value.splice(0,consoleLogs.value.length-500);});}
function toggleConsole(){showConsole.value=!showConsole.value;if(showConsole.value)connectLogStream();else{consoleEs.value?.close();consoleEs.value=null;}}
function nodeName(id:string|null|undefined):string{if(!id||!snap.value)return'';const n=snap.value.nodes.find((x:any)=>x.id===id);return n?n.title:'';}
function onNodeClick({node:n}:any){selectedNode.value=n&&n.data?{...n.data,id:n.id}:null;}
async function loadNodeSession(nodeId:string){loadingSession.value=nodeId;try{const r=await axios.get(`/api/projects/${selected.value}/nodes/${nodeId}/session`);nodeSessions.value[nodeId]=r.data;}catch(e){nodeSessions.value[nodeId]={error:String(e)};}finally{loadingSession.value='';}}
function nodeRun(nodeId:string):any{return snap.value?.runs.find((r:any)=>r.nodeId===nodeId)||null;}
async function createAndStart(){creatingReq.value=true;try{await create();await new Promise(r=>setTimeout(r,300));await start();}finally{creatingReq.value=false}}
async function submitChange(){if(!changeDesc.value.trim()||!selected.value)return;submittingChange.value=true;try{await axios.post(`/api/projects/${selected.value}/events/change`,{description:changeDesc.value});changeDesc.value='';await refresh();}finally{submittingChange.value=false}}
function statusLabel(s:string):string{const m:any={CREATED:'已创建',WAITING:'就绪',RUNNING:'运行中',COMPLETED:'已完成'};return m[s]||s;}
function eventPayload(e:any):string{try{if(e.type==='CHANGE_REQUESTED'){const p=JSON.parse(e.payload||'{}');return'变更: '+(p.description||e.type)}return'';}catch{return''}}
onMounted(loadProjects);onBeforeUnmount(()=>{es.value?.close();consoleEs.value?.close();stopAuto();});
</script>
<template>
<div class="shell">
  <header>
    <div class="brand"><h1>Hive Delivery</h1><span class="subtitle">AI 驱动的项目交付引擎</span></div>
    <div class="toolbar">
      <select v-model="selected" @change="select(selected)"><option v-for="p in projects" :value="p.id">{{p.name}} · {{statusLabel(p.status)}}</option></select>
      <button class="primary" :disabled="!selected||running" @click="running?stopAuto():start()">{{running?'⏳ 执行中...':'▶ 开始交付'}}</button>
    </div>
  </header>

  <div v-if="snap" class="statusbar"><span class="sb-project">{{snap.project.name}}</span><span class="sb-status" :class="statusMsg?.type">{{statusLabel(snap.project.status)}}</span><span v-if="statusMsg&&statusMsg.type!=='idle'" class="sb-msg">· {{statusMsg.text}}</span><span class="sb-rev">Revision {{snap.project.revision}}</span></div>

  <main v-if="snap" class="main-layout">
    <!-- LEFT: Project sidebar -->
    <aside class="left-panel">
      <div class="panel-section">
        <h3>📋 需求描述</h3>
        <textarea v-model="newRequirement" placeholder="描述你要实现的功能，例如：&#10;为商品查询API添加品牌模糊搜索和分页功能" rows="4"></textarea>
        <button class="primary full" :disabled="creatingReq||!newRequirement.trim()" @click="createAndStart">
          {{creatingReq?'创建中...':'🚀 提交需求并启动'}}
        </button>
      </div>

      <div v-if="snap&&snap.project.status!=='COMPLETED'" class="panel-section">
        <h3>🔄 变更请求</h3>
        <textarea v-model="changeDesc" placeholder="输入变更说明，例如：&#10;需求阶段新增对价格区间的支持" rows="3"></textarea>
        <button class="full" :disabled="submittingChange||!changeDesc.trim()" @click="submitChange" style="background:#7c3aed;color:white;border:none">
          {{submittingChange?'提交中...':'📝 提交变更 (Change Event)'}}
        </button>
        <p style="font-size:11px;color:#94a3b8;margin:6px 0 0">变更事件已持久化；Graph Patch 为下一扩展点</p>
      </div>

      <div class="panel-section">
        <h3>📊 项目信息</h3>
        <div class="info-row"><span>状态</span><span class="pill mini" :class="snap.project.status">{{statusLabel(snap.project.status)}}</span></div>
        <div class="info-row"><span>节点数</span><span>{{snap.nodes.length}}</span></div>
        <div class="info-row"><span>任务运行</span><span>{{snap.runs.length}}</span></div>
        <div v-if="gateCount>0" class="info-row warn"><span>待审批</span><span>{{gateCount}} 个 Gate</span></div>
      </div>

      <div class="panel-section">
        <h3>🕐 事件时间线</h3>
        <div class="event" v-for="e in snap.events.slice(0,25)" :key="e.id" :class="{change: e.type==='CHANGE_REQUESTED'}">
          <div class="event-left"><b>{{nodeName(e.nodeId)||'系统'}}</b><small>{{e.type}}</small><span v-if="eventPayload(e)" class="change-note">{{eventPayload(e)}}</span></div>
          <small class="event-time">{{new Date(e.createdAt).toLocaleTimeString()}}</small>
        </div>
      </div>
    </aside>

    <!-- CENTER: Graph canvas -->
    <section class="canvas"><VueFlow :nodes="flowNodes" :edges="flowEdges" fit-view-on-init @node-click="onNodeClick"><Background/><Controls/></VueFlow></section>

    <!-- RIGHT: Node detail -->
    <aside class="right-panel">
      <template v-if="selectedNode">
        <h2>{{selectedNode.label}}</h2>
        <div class="pill" :style="{background:colors[selectedNode.status]||'#334155'}">{{selectedNode.type}} · {{selectedNode.status}}</div>

        <div v-if="selectedNode.description" class="node-detail"><h4>说明</h4><p>{{selectedNode.description}}</p></div>
        <div v-if="selectedNode.criteria?.length" class="node-detail"><h4>验收标准</h4><ul><li v-for="c in selectedNode.criteria">{{c}}</li></ul></div>
        <div v-if="selectedNode.executor&&selectedNode.executor!=='NONE'" class="node-detail"><h4>执行器</h4><p>{{selectedNode.executor}} · {{selectedNode.handler}} <span v-if="selectedNode.agentRole">({{selectedNode.agentRole}})</span></p></div>

        <div v-if="nodeRun(selectedNode.nodeId)" class="node-detail">
          <h4>任务运行</h4>
          <p>状态: {{nodeRun(selectedNode.nodeId).status}} | 尝试 #{{nodeRun(selectedNode.nodeId).attempt}}</p>
          <p v-if="nodeRun(selectedNode.nodeId).externalSessionId">Session: <code>{{nodeRun(selectedNode.nodeId).externalSessionId.slice(0,12)}}…</code></p>
          <p v-if="nodeRun(selectedNode.nodeId).summary" class="summary-text">{{nodeRun(selectedNode.nodeId).summary}}</p>
          <button class="small" :disabled="loadingSession===selectedNode.nodeId" @click="loadNodeSession(selectedNode.nodeId)">{{loadingSession===selectedNode.nodeId?'加载中...':'🤖 查看 OpenCode I/O'}}</button>
        </div>

        <div v-if="nodeSessions[selectedNode.nodeId]" class="node-detail session-output">
          <h4>📥 输入（Prompt）</h4><pre class="prompt-box">{{nodeSessions[selectedNode.nodeId].prompt||'(无 prompt 数据)'}}</pre>
          <h4>📤 输出（Agent 回复）</h4>
          <div v-if="nodeSessions[selectedNode.nodeId].messages?.length">
            <div v-for="(m,i) in nodeSessions[selectedNode.nodeId].messages" class="msg-block">
              <span class="msg-role" :class="m.role==='user'?'role-user':'role-agent'">{{m.role==='user'?'👤 用户':'🤖 Agent'}}</span>
              <pre class="msg-text">{{m.text}}</pre>
            </div>
          </div>
          <p v-else class="dim">等待 Agent 响应中…</p>
        </div>

        <button v-if="selectedNode.status==='WAITING_HUMAN'" class="primary full pulse-btn mt" @click="approve">✓ 批准 Gate</button>
        <button v-if="selectedNode" class="full mt" style="margin-top:8px" @click="selectedNode=null">✕ 关闭</button>
      </template>
      <template v-else>
        <div class="empty-hint">
          <div class="hint-icon">👆</div>
          <p>点击画布中的节点<br>查看任务详情</p>
        </div>
      </template>
    </aside>
  </main>

  <div v-else class="empty">
    <div class="empty-card">
      <h2>开始一个新的交付项目</h2>
      <p>输入需求描述，点击提交，Hive Delivery 将自动规划并执行交付任务。</p>
      <textarea v-model="newRequirement" placeholder="例如：为商品查询API添加品牌模糊搜索和分页功能&#10;例如：添加商品缓存，提升查询性能" rows="3" style="max-width:500px;margin:12px auto;display:block"></textarea>
      <button class="primary" :disabled="creatingReq||!newRequirement.trim()" @click="createAndStart" style="font-size:16px;padding:12px 32px">
        {{creatingReq?'创建中...':'🚀 创建并启动'}}
      </button>
    </div>
  </div>

  <div class="console-bar" :class="{open:showConsole}">
    <div class="console-header" @click="toggleConsole"><span class="console-title">┤ 控制台日志</span><span class="console-badge">{{consoleLogs.length}}</span><span class="console-toggle">{{showConsole?'▼':'▲'}}</span></div>
    <div v-show="showConsole" class="console-body"><div v-for="(l,i) in consoleLogs" :key="i" class="console-line">{{l}}</div></div>
  </div>
</div>
</template>
