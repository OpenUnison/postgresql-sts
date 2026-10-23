const $ = id => document.getElementById(id);
const labels = {TODO:'To do',IN_PROGRESS:'In progress',DONE:'Done'};
let tasks=[], filter='ALL', editing=null;
async function api(path='', options={}) {
  const response=await fetch('/api/tasks'+path,{...options,headers:{'Content-Type':'application/json',...options.headers}});
  if(!response.ok) throw new Error(response.status===400?'Check the task fields and try again.':response.status===404?'That task no longer exists. Refresh the list.':'Unable to save or load tasks. Please try again.');
  return response.status===204?null:response.json();
}
function showError(id,message){$(id).textContent=message;$(id).hidden=!message;}
function node(tag,text,className){const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(className)n.className=className;return n;}
function render(){
  $('total').textContent=tasks.length;$('progress').textContent=tasks.filter(t=>t.status==='IN_PROGRESS').length;$('completed').textContent=tasks.filter(t=>t.status==='DONE').length;
  const term=$('search').value.toLowerCase();const visible=tasks.filter(t=>(filter==='ALL'||t.status===filter)&&(t.title+' '+t.description).toLowerCase().includes(term));
  $('tasks').replaceChildren();
  if(!visible.length){const box=node('div',undefined,'empty');box.append(node('strong',tasks.length?'No matching tasks':'A fresh start.'),node('span',tasks.length?'Try a different search or filter.':'Create your first task and give your day a little direction.'));$('tasks').append(box);return;}
  for(const task of visible){
    const row=node('article',undefined,'task'+(task.status==='DONE'?' done':''));
    const check=document.createElement('input');check.type='checkbox';check.className='check';check.checked=task.status==='DONE';check.setAttribute('aria-label','Mark '+task.title+(check.checked?' as to do':' as done'));
    check.onchange=async()=>{check.disabled=true;try{await api('/'+task.id,{method:'PUT',body:JSON.stringify({...task,status:check.checked?'DONE':'TODO'})});await load();}catch(e){showError('error',e.message);check.checked=task.status==='DONE';}finally{check.disabled=false;}};
    const content=node('div',undefined,'task-content');content.append(node('h3',task.title),node('p',task.description));
    const meta=node('div',undefined,'meta');meta.append(node('span',labels[task.status],'badge '+task.status));
    if(task.dueDate){const today=new Date();const localToday=[today.getFullYear(),String(today.getMonth()+1).padStart(2,'0'),String(today.getDate()).padStart(2,'0')].join('-');meta.append(node('span',new Date(task.dueDate+'T12:00:00').toLocaleDateString(undefined,{month:'short',day:'numeric'}),'due'+(task.dueDate<localToday&&task.status!=='DONE'?' overdue':'')));}
    const edit=node('button','✎','icon');edit.setAttribute('aria-label','Edit '+task.title);edit.onclick=()=>openEditor(task);
    const del=node('button','×','icon');del.setAttribute('aria-label','Delete '+task.title);del.onclick=async()=>{if(!confirm('Delete “'+task.title+'”?'))return;del.disabled=true;try{await api('/'+task.id,{method:'DELETE'});await load();}catch(e){showError('error',e.message);del.disabled=false;}};
    row.append(check,content,meta,edit,del);$('tasks').append(row);
  }
}
async function load(){showError('error','');try{tasks=await api();render();}catch(e){showError('error',e.message);}}
function openEditor(task){editing=task?.id??null;$('dialog-title').textContent=editing?'Edit task':'New task';$('title').value=task?.title??'';$('description').value=task?.description??'';$('status').value=task?.status??'TODO';$('due-date').value=task?.dueDate??'';showError('form-error','');$('editor').showModal();$('title').focus();}
$('new-task').onclick=()=>openEditor(null);$('cancel').onclick=$('close-dialog').onclick=()=>$('editor').close();
$('search').oninput=render;
for(const button of document.querySelectorAll('[data-filter]'))button.onclick=()=>{filter=button.dataset.filter;for(const b of document.querySelectorAll('[data-filter]')){b.classList.toggle('selected',b===button);b.setAttribute('aria-pressed',String(b===button));}render();};
$('task-form').onsubmit=async event=>{event.preventDefault();if(!$('title').value.trim()){showError('form-error','Enter a title.');return;}$('save').disabled=true;try{await api(editing?'/'+editing:'',{method:editing?'PUT':'POST',body:JSON.stringify({title:$('title').value,description:$('description').value,status:$('status').value,dueDate:$('due-date').value||null})});$('editor').close();await load();}catch(e){showError('form-error',e.message);}finally{$('save').disabled=false;}};
load();
