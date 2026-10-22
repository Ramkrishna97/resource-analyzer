const $ = s => document.querySelector(s);
const state = {tab:'file',level:'MEDIUM',file:null,result:null,combine:false,sources:[],results:[]};
document.querySelectorAll('.tab').forEach(button=>button.addEventListener('click',()=>{
  document.querySelectorAll('.tab').forEach(b=>b.classList.toggle('active',b===button));
  state.tab=button.dataset.tab; $('#filePanel').classList.toggle('hidden',state.tab!=='file'); $('#urlPanel').classList.toggle('hidden',state.tab!=='url'); clearError();
}));
document.querySelectorAll('.level').forEach(button=>button.addEventListener('click',()=>{
  document.querySelectorAll('.level').forEach(b=>{const on=b===button;b.classList.toggle('selected',on);b.setAttribute('aria-checked',on?'true':'false')});state.level=button.dataset.level;
}));
$('#fileInput').addEventListener('change',e=>setFile(e.target.files[0]));
function setFile(file){if(!file)return;state.file=file;$('#fileTitle').innerHTML='File ready to summarize';$('#fileSub').textContent='Click to choose a different file';$('#fileName').textContent=file.name;if(state.combine)addSource({type:'file',file,level:state.level})}
const dropzone=$('#dropzone');dropzone.addEventListener('dragover',e=>{e.preventDefault();dropzone.classList.add('dragover')});dropzone.addEventListener('dragleave',()=>dropzone.classList.remove('dragover'));dropzone.addEventListener('drop',e=>{e.preventDefault();dropzone.classList.remove('dragover');setFile(e.dataTransfer.files[0])});
$('#engineSelect').addEventListener('change',async()=>{
  const ollama=$('#engineSelect').value==='ollama';
  $('#modelRow').classList.toggle('hidden',!ollama);
  $('#engineHint').textContent=ollama?'Choose a model installed on this computer. Ollama loads it when you summarize.':'Releasing loaded Ollama models?';
  if(ollama){refreshModels();return}
  try{
    const response=await fetch('/api/ollama/unload',{method:'POST'});
    const data=await response.json();
    if(!response.ok)throw new Error(data.message||'Could not release Ollama models.');
    $('#engineHint').textContent=data.unloaded?`Offline ready. Released ${data.unloaded} Ollama model${data.unloaded===1?'':'s'}.`:'Works offline. No Ollama model is loaded.';
  }catch(error){$('#engineHint').textContent='Works offline. Ollama models may still be loaded.';$('#errorMessage').textContent=error.message}
});
async function refreshModels(){
  const select=$('#modelSelect'),status=$('#ollamaStatus');status.textContent='Checking…';status.classList.remove('available');
  try{
    const response=await fetch('/api/status',{cache:'no-store'});if(!response.ok)throw new Error('Status unavailable');
    const info=await response.json(),models=info.models||[],previous=select.value;
    select.replaceChildren();
    if(models.length){for(const name of models){const option=document.createElement('option');option.value=name;option.textContent=name;select.append(option)}select.value=models.includes(previous)?previous:models.includes(info.defaultModel)?info.defaultModel:models[0]}
    else {const option=document.createElement('option');option.value='';option.textContent='No installed models found';select.append(option)}
    status.textContent=info.ollamaAvailable?(models.length?`${models.length} available`:'No models installed'):'Start Ollama to use a model';
    if(info.ollamaAvailable&&models.length)status.classList.add('available');
  }catch(error){select.replaceChildren(new Option('Unable to load models',''));status.textContent='Ollama unavailable'}
}
$('#refreshModels').addEventListener('click',refreshModels);
refreshModels();
function selectedModel(){const model=$('#modelSelect').value;if($('#engineSelect').value==='ollama'&&!model)throw new Error('No Ollama model is installed. Run “ollama pull gemma3:1b”, then refresh the model list.');return model}
function clearError(){$('#errorMessage').textContent=''}
$('#combineToggle').addEventListener('change',e=>{state.combine=e.target.checked;$('#sourceList').classList.toggle('hidden',!state.combine);if(state.combine&&state.file&&!state.sources.length)addSource({type:'file',file:state.file,level:state.level});$('#submitButton span:first-child').textContent=state.combine?'Combine and summarize':'Make my summary'});
function addSource(source){source.id=crypto.randomUUID();state.sources.push(source);renderSources()}
function renderSources(){const list=$('#sourceList');list.innerHTML='';state.sources.forEach((s,index)=>{const row=document.createElement('div');row.className='source-row'+(state.results.some(r=>r.sourceId===s.id)?' processed':'')+(state.previewSourceId===s.id?' preview-selected':'');const label=s.type==='file'?s.file.name:s.value||'Web page URL';row.innerHTML=`<button type="button" class="drag-handle" draggable="true" aria-label="Drag to reorder source ${index+1}" title="Drag to reorder">&#8942;&#8942;</button><span class="source-index">${String(index+1).padStart(2,'0')}</span><span class="source-kind">${s.type==='file'?'FILE':'URL'}</span><span class="source-label" title="${escapeHtml(label)}">${escapeHtml(label)}</span><select aria-label="Detail level for source ${index+1}"><option value="LOW">Light</option><option value="MEDIUM" ${s.level==='MEDIUM'?'selected':''}>Balanced</option><option value="HIGH">Essential</option><option value="RECOMMENDED">Recommended</option></select><button type="button" class="remove-source" aria-label="Remove source">×</button>`;row.querySelector('select').value=s.level;row.querySelector('select').onchange=e=>changeSourceLevel(s,e.target.value);row.querySelector('.remove-source').onclick=()=>{if(s.pendingRequest)s.pendingRequest.abort();state.sources=state.sources.filter(x=>x.id!==s.id);state.results=state.results.filter(r=>r.sourceId!==s.id);if(state.previewSourceId===s.id)state.previewSourceId=null;renderSources();if(state.results.length)renderBatch(state.results,false);else $('#resultCard').classList.add('hidden')};makeReorderable(row,row.querySelector(".drag-handle"),s.id,()=>state.sources.map(x=>x.id),moveSource);row.onclick=e=>{if(e.target.closest('select,button'))return;if(state.results.some(r=>r.sourceId===s.id))showPreview(s.id)};list.append(row)});if(state.combine&&!state.sources.some(s=>s.type==='url')){const add=document.createElement('button');add.className='add-url-source';add.textContent='+ Add current URL';add.onclick=()=>{const url=$('#urlInput').value.trim();if(!url){$('#errorMessage').textContent='Enter a URL before adding it to the collection.';return}addSource({type:'url',value:url,level:state.level})};list.append(add)}}
function moveItem(items,from,to){const [item]=items.splice(from,1);items.splice(to,0,item)}
async function changeSourceLevel(source,level){
  const previous=source.level;
  source.level=level;renderSources();
  if(!state.results.some(result=>result.sourceId===source.id))return;
  if(source.pendingRequest)source.pendingRequest.abort();
  const controller=new AbortController();source.pendingRequest=controller;
  showPreview(source.id,true);$('#exportButton').disabled=true;clearError();
  try{
    const recommended=level==='RECOMMENDED',engine=recommended?'offline':$('#engineSelect').value;
    let response;
    if(source.type==='file'){
      const form=new FormData();form.append('file',source.file);form.append('level',recommended?'LOW':level);form.append('engine',engine);form.append('model',recommended?'':selectedModel());form.append('recommended',recommended);
      response=await fetch('/api/analyze/file',{method:'POST',body:form,signal:controller.signal});
    }else{
      response=await fetch('/api/analyze/url',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({url:source.value,level:recommended?'LOW':level,engine,model:recommended?'':selectedModel(),recommended}),signal:controller.signal});
    }
    const result=await response.json();
    if(!response.ok)throw new Error(result.message||result.error||'Could not update this preview.');
    if(source.pendingRequest!==controller)return;
    source.pendingRequest=null;
    result.sourceId=source.id;result.recommended=recommended;
    const index=state.results.findIndex(item=>item.sourceId===source.id);
    if(index>=0)state.results[index]=result;
    if(state.previewSourceId===source.id)showPreview(source.id);else if(!state.previewSourceId)renderBatch(state.results,false);
  }catch(error){
    if(error.name!=='AbortError'&&source.pendingRequest===controller){source.pendingRequest=null;source.level=previous;renderSources();showPreview(source.id);$('#errorMessage').textContent=error.message||'Could not update this preview.'}
  }finally{if(source.pendingRequest===controller)source.pendingRequest=null;$('#exportButton').disabled=state.sources.some(item=>item.pendingRequest)}
}
function moveSource(fromId,toId){const from=state.sources.findIndex(s=>s.id===fromId),to=state.sources.findIndex(s=>s.id===toId);if(from<0||to<0||from===to)return;moveItem(state.sources,from,to);if(state.results.length&&state.results.every(r=>r.sourceId)){state.results.sort((a,b)=>state.sources.findIndex(s=>s.id===a.sourceId)-state.sources.findIndex(s=>s.id===b.sourceId));renderBatch(state.results,false)}renderSources()}
function moveResult(fromId,toId){const from=state.results.findIndex(r=>r.sourceId===fromId),to=state.results.findIndex(r=>r.sourceId===toId);if(from<0||to<0||from===to)return;moveItem(state.results,from,to);state.sources.sort((a,b)=>{const ai=state.results.findIndex(r=>r.sourceId===a.id),bi=state.results.findIndex(r=>r.sourceId===b.id);return (ai<0?Infinity:ai)-(bi<0?Infinity:bi)});renderSources();renderBatch(state.results,false)}
function makeReorderable(row,handle,id,order,move){handle.addEventListener('dragstart',event=>{event.dataTransfer.effectAllowed='move';event.dataTransfer.setData('text/plain',id);row.classList.add('dragging')});handle.addEventListener('dragend',()=>{row.classList.remove('dragging');document.querySelectorAll('.drag-target').forEach(el=>el.classList.remove('drag-target'))});row.addEventListener('dragover',event=>{if(!event.dataTransfer.types.includes('text/plain'))return;event.preventDefault();event.dataTransfer.dropEffect='move';row.classList.add('drag-target')});row.addEventListener('dragleave',()=>row.classList.remove('drag-target'));row.addEventListener('drop',event=>{event.preventDefault();row.classList.remove('drag-target');const fromId=event.dataTransfer.getData('text/plain');if(order().includes(fromId))move(fromId,id)})}
$('#submitButton').addEventListener('click',()=>analyzeCurrent(false));
$('#recommendedButton').addEventListener('click',()=>analyzeCurrent(true));
async function analyzeCurrent(recommended){
  clearError();
  if(recommended){
    document.querySelector('.level[data-level="LOW"]').click();
    if(state.combine){state.sources.forEach(source=>source.level='RECOMMENDED');renderSources()}
    if($('#engineSelect').value!=='offline'){$('#engineSelect').value='offline';$('#engineSelect').dispatchEvent(new Event('change'))}
  }
  const engine=recommended?'offline':$('#engineSelect').value;
  let response,submittedSourceIds=[],submittedRecommended=[];
  try{
    $('#submitButton').disabled=true;$('#recommendedButton').disabled=true;
    $('#submitButton span:first-child').textContent='Reading your source...';
    $('#recommendedButton span:first-child').textContent='Preparing recommended summary...';
    if(state.combine){
      if(state.tab==='url'&&$('#urlInput').value.trim()&&!state.sources.some(s=>s.type==='url'&&s.value===$('#urlInput').value.trim()))addSource({type:'url',value:$('#urlInput').value.trim(),level:recommended?'RECOMMENDED':state.level});
      if(!state.sources.length)throw new Error('Add at least one source to combine.');
      submittedSourceIds=state.sources.map(source=>source.id);
      submittedRecommended=state.sources.map(source=>recommended||source.level==='RECOMMENDED');
      const model=engine==='ollama'&&state.sources.some(source=>source.level!=='RECOMMENDED')?selectedModel():'',form=new FormData();
      const manifest=state.sources.map((source,index)=>{
        const sourceRecommended=recommended||source.level==='RECOMMENDED';
        const item={type:source.type,level:sourceRecommended?'LOW':source.level,engine:sourceRecommended?'offline':engine,model:sourceRecommended?'':model,recommended:sourceRecommended};
        if(source.type==='file'){item.key=`source_${index}`;form.append(item.key,source.file)}else item.value=source.value;
        return item;
      });
      form.append('manifest',JSON.stringify(manifest));
      response=await fetch('/api/analyze/batch',{method:'POST',body:form});
    }else if(state.tab==='file'){
      if(!state.file)throw new Error('Choose a PDF, Word, or Excel file first.');
      const data=new FormData();data.append('file',state.file);data.append('level',recommended?'LOW':state.level);data.append('engine',engine);data.append('model',selectedModel());data.append('recommended',recommended);
      response=await fetch('/api/analyze/file',{method:'POST',body:data});
    }else{
      const url=$('#urlInput').value.trim();if(!url)throw new Error('Paste a website URL first.');
      response=await fetch('/api/analyze/url',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({url,level:recommended?'LOW':state.level,engine,model:selectedModel(),recommended})});
    }
    const data=await response.json();
    if(!response.ok)throw new Error(data.message||data.error||'Unable to analyze this source.');
    state.recommended=recommended;
    if(Array.isArray(data)){data.forEach((result,index)=>{result.sourceId=submittedSourceIds[index];result.recommended=submittedRecommended[index]});state.results=data;state.previewSourceId=null;renderBatch(data)}else render(data);
  }catch(error){$('#errorMessage').textContent=error.message||'Something went wrong. Please try again.'}
  finally{
    $('#submitButton').disabled=false;$('#recommendedButton').disabled=false;
    $('#submitButton span:first-child').textContent=state.combine?'Combine and summarize':'Make my summary';
    $('#recommendedButton span:first-child').innerHTML='&#9733; &nbsp; Recommended';
  }
}
function escapeHtml(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
function imageGallery(images){return (images||[]).map((item,index)=>{
  const labels=[item.alt&&`Alt: ${item.alt}`,item.title&&`Title: ${item.title}`,item.caption&&`Caption: ${item.caption}`].filter(Boolean);
  return `<figure class="image-item"><a href="${escapeHtml(item.url)}" target="_blank" rel="noopener noreferrer"><img src="${escapeHtml(item.url)}" alt="${escapeHtml(item.alt||item.caption||`Image ${index+1}`)}" loading="lazy"></a><figcaption>${labels.length?labels.map(label=>`<span class="image-tag">${escapeHtml(label)}</span>`).join(''):`<span class="image-tag">Image ${index+1}</span>`}</figcaption></figure>`;
}).join('')}
function render(data){
  data.recommended=state.recommended;state.result=data;state.results=[];state.previewSourceId=null;$('#backToCombined').classList.add('hidden');$('.layout').classList.add('has-results');
  $('#sourceDetails').classList.remove('hidden');$('#resultCard').classList.remove('hidden');
  $('#resultTitle').textContent=data.title||'Summary';
  $('#resultMeta').textContent=`${state.recommended?'Recommended | ':''}${data.originalWords.toLocaleString()} source words | ${data.summaryWords.toLocaleString()} summary words | ${data.level.toLowerCase()} detail | ${data.summarizer}`;
  $('#summaryBody').innerHTML=markdown(data.summary,state.recommended?data.images:[]);
  $('#sourceText').textContent=data.extractedText;$('#sourceCount').textContent=`(${data.originalWords.toLocaleString()} words)`;
  const links=data.links||[],images=data.images||[];
  $('#linksBlock').classList.toggle('hidden',!links.length);
  $('#linksList').innerHTML=links.map(link=>`<a href="${escapeHtml(link)}" target="_blank" rel="noopener noreferrer">${escapeHtml(link)}</a>`).join('');
  $('#imagesBlock').classList.toggle('hidden',state.recommended||!images.length);$('#imagesCount').textContent=`(${images.length})`;
  $('#imagesList').innerHTML=imageGallery(images);
  $('#resultCard').scrollIntoView({behavior:'smooth',block:'nearest'});
}
function showPreview(sourceId,loading=false){
  const result=state.results.find(item=>item.sourceId===sourceId);
  if(!result)return;
  state.previewSourceId=sourceId;renderSources();
  $('#resultCard').classList.remove('hidden');$('#backToCombined').classList.remove('hidden');
  $('#resultTitle').textContent=result.title||'Source preview';
  $('#resultMeta').textContent=`${result.recommended?'Recommended | ':''}${result.originalWords.toLocaleString()} source words | ${result.summaryWords.toLocaleString()} summary words | ${result.level.toLowerCase()} detail${loading?' | Updating preview...':''}`;
  $('#summaryBody').innerHTML=markdown(result.summary,result.recommended?result.images:[]);
  $('#sourceDetails').classList.remove('hidden');$('#sourceText').textContent=result.extractedText;$('#sourceCount').textContent=`(${result.originalWords.toLocaleString()} words)`;
  const links=result.links||[],images=result.images||[];
  $('#linksBlock').classList.toggle('hidden',!links.length);
  $('#linksList').innerHTML=links.map(link=>`<a href="${escapeHtml(link)}" target="_blank" rel="noopener noreferrer">${escapeHtml(link)}</a>`).join('');
  $('#imagesBlock').classList.toggle('hidden',result.recommended||!images.length);$('#imagesCount').textContent=`(${images.length})`;
  $('#imagesList').innerHTML=imageGallery(images);
  $('#resultCard').scrollIntoView({behavior:'smooth',block:'nearest'});
}
$('#backToCombined').addEventListener('click',()=>renderBatch(state.results));
function renderBatch(results,scroll=true){
  state.previewSourceId=null;$('#backToCombined').classList.add('hidden');renderSources();
  $('.layout').classList.add('has-results');
  $('#resultCard').classList.remove('hidden');
  $('#resultTitle').textContent=`Combined summary - ${results.length} sources`;
  $('#resultMeta').textContent=`${results.reduce((n,r)=>n+r.originalWords,0).toLocaleString()} source words | individually summarized | drag handles to reorder`;
  $('#sourceDetails').classList.add('hidden');$('#sourceText').textContent='';$('#sourceCount').textContent='';
  $('#linksBlock').classList.add('hidden');$('#imagesBlock').classList.add('hidden');
  $('#summaryBody').innerHTML=results.map((r,i)=>`<section class="combined-section" data-source-id="${escapeHtml(r.sourceId||'')}"><div class="combined-heading"><button type="button" class="drag-handle result-drag-handle" draggable="true" aria-label="Drag to reorder result ${i+1}" title="Drag to reorder">&#8942;&#8942;</button><span>${String(i+1).padStart(2,'0')}</span><div><h3>${escapeHtml(r.title)}</h3><a href="${escapeHtml(r.source)}" target="_blank" rel="noopener noreferrer">${escapeHtml(r.source)}</a><small>${r.recommended?'recommended':r.level.toLowerCase()} detail | ${r.summaryWords.toLocaleString()} words</small></div></div><div class="combined-copy">${markdown(r.summary,r.recommended?r.images:[])}</div>${(!r.recommended&&(r.images||[]).length)?`<details class="images-block combined-images"><summary>Images from this source (${r.images.length})</summary><div class="image-gallery">${imageGallery(r.images)}</div></details>`:''}</section>`).join('');
  $('#summaryBody').querySelectorAll('.combined-section').forEach(row=>{const id=row.dataset.sourceId;if(id)makeReorderable(row,row.querySelector('.drag-handle'),id,()=>state.results.map(r=>r.sourceId),moveResult)});
  if(scroll)$('#resultCard').scrollIntoView({behavior:'smooth',block:'nearest'});
}

function inline(text){
  const parts=String(text).split(/(https?:\/\/[^\s<>]+)/g);
  return parts.map(part=>{
    if(/^https?:\/\//.test(part)){
      const url=part.replace(/[.,;:!?)]*$/,'');
      const trailing=part.slice(url.length);
      return `<a href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(url)}</a>${escapeHtml(trailing)}`;
    }
    return escapeHtml(part).replace(/\*\*([^*]+)\*\*/g,'<strong>$1</strong>');
  }).join('');
}
function markdown(text,images=[]){
  const lines=String(text||'').replace(/\r/g,'').split('\n');
  let out='',i=0;
  const listLine=line=>/^\s*(?:[-*\u2022]|\d+[.)])\s+/.test(line);
  const tableLine=line=>/^\s*\|.*\|\s*$/.test(line);
  while(i<lines.length){
    const line=lines[i].trim();
    if(!line){i++;continue}
    const imageMatch=line.match(/^\[\[IMAGE:(\d+)]]$/);
    if(imageMatch){
      const index=Number(imageMatch[1]),image=images[index];
      if(image)out+=`<figure class="inline-image"><img src="${escapeHtml(image.url)}" alt="${escapeHtml(image.alt||imageLabel(image,index))}" loading="lazy"><figcaption>${escapeHtml(imageLabel(image,index))}</figcaption></figure>`;
      i++;continue;
    }
    const heading=line.match(/^(#{1,6})\s+(.+)$/);
    if(heading){const tag=heading[1].length<=2?'h2':'h3';out+=`<${tag}>${inline(heading[2])}</${tag}>`;i++;continue}
    if(tableLine(line)){
      const rows=[];
      while(i<lines.length&&tableLine(lines[i])){
        const cells=lines[i].trim().replace(/^\||\|$/g,'').split('|').map(c=>c.trim());
        if(!cells.every(c=>/^:?-{2,}:?$/.test(c)))rows.push(cells);
        i++;
      }
      out+='<div class="table-scroll"><table>'+rows.map((row,index)=>'<tr>'+row.map(cell=>`<${index===0?'th':'td'}>${inline(cell)}</${index===0?'th':'td'}>`).join('')+'</tr>').join('')+'</table></div>';
      continue;
    }
    if(listLine(line)){
      const ordered=/^\d+[.)]\s+/.test(line),items=[];
      while(i<lines.length&&listLine(lines[i]))items.push(lines[i++].trim().replace(/^(?:[-*\u2022]|\d+[.)])\s+/,''));
      const tag=ordered?'ol':'ul';out+=`<${tag}>`+items.map(item=>`<li>${inline(item)}</li>`).join('')+`</${tag}>`;
      continue;
    }
    const paragraph=[];
    while(i<lines.length&&lines[i].trim()&&!/^(#{1,6})\s+/.test(lines[i].trim())&&!listLine(lines[i])&&!tableLine(lines[i])&&!/^\[\[IMAGE:\d+]]$/.test(lines[i].trim()))paragraph.push(lines[i++].trim());
    if(paragraph.length)out+=`<p>${paragraph.map(inline).join(' ')}</p>`;
  }
  return out||'<p>No summary text was returned.</p>';
}
$('#exportButton').addEventListener('click',e=>{e.stopPropagation();$('#exportMenu').classList.toggle('hidden')});document.addEventListener('click',e=>{if(!e.target.closest('.export-wrap'))$('#exportMenu').classList.add('hidden')});
function exportResults(){return state.results.length?state.results:state.result?[state.result]:[]}
function imageLabel(image,index){return image.caption||image.alt||image.title||`Image ${index+1}`}
function textExport(results){return results.map((result,index)=>{
  const heading=results.length>1?`SOURCE ${index+1}: ${result.title}`:result.title;
  if(result.recommended){
    const content=result.summary.replace(/\[\[IMAGE:(\d+)]]/g,(_,number)=>{const image=(result.images||[])[Number(number)];return image?`[Image: ${imageLabel(image,Number(number))}]\n${image.url}`:''});
    return `${heading}\nSource: ${result.source}\nDetail: ${result.level}\n\n${content}`;
  }
  const images=(result.images||[]).map((image,i)=>`- ${imageLabel(image,i)}\n  ${image.url}`).join('\n');
  return `${heading}\nSource: ${result.source}\nDetail: ${result.level}\n\n${result.summary}${images?`\n\nIMAGES\n${images}`:''}`;
}).join('\n\n'+'='.repeat(70)+'\n\n')}
const exportImageCache=new Map();
async function imageForExport(image){
  if(exportImageCache.has(image.url))return exportImageCache.get(image.url);
  const task=(async()=>{
    const location=image.url.startsWith('data:image/')?image.url:`/api/image?url=${encodeURIComponent(image.url)}`;
    const response=await fetch(location);
    if(!response.ok){let detail='';try{detail=(await response.json()).message||''}catch{}throw new Error(`Could not fetch image: ${detail||imageLabel(image,0)}`)}
    const blob=await response.blob();
    let bitmap;
    try{bitmap=await createImageBitmap(blob)}catch{
      const objectUrl=URL.createObjectURL(blob);
      try{bitmap=await new Promise((resolve,reject)=>{const element=new Image();element.onload=()=>resolve(element);element.onerror=()=>reject(new Error('Unsupported image format'));element.src=objectUrl})}
      finally{URL.revokeObjectURL(objectUrl)}
    }
    const scale=Math.min(1,1800/Math.max(bitmap.width,bitmap.height));
    const width=Math.max(1,Math.round(bitmap.width*scale)),height=Math.max(1,Math.round(bitmap.height*scale));
    const canvas=document.createElement('canvas');canvas.width=width;canvas.height=height;
    canvas.getContext('2d').drawImage(bitmap,0,0,width,height);
    if(bitmap.close)bitmap.close();
    const dataUrl=canvas.toDataURL('image/png');
    const binary=atob(dataUrl.split(',')[1]),bytes=new Uint8Array(binary.length);
    for(let i=0;i<binary.length;i++)bytes[i]=binary.charCodeAt(i);
    return{dataUrl,bytes,width,height};
  })();
  exportImageCache.set(image.url,task);
  try{return await task}catch(error){exportImageCache.delete(image.url);throw error}
}
async function docxSummary(text,docx,images=[]){
  const {Paragraph,HeadingLevel,Table,TableRow,TableCell,ImageRun}=docx;
  const elements=[],lines=String(text||'').replace(/\r/g,'').split('\n');
  for(let i=0;i<lines.length;){
    const line=lines[i].trim();
    if(!line){i++;continue}
    const imageMatch=line.match(/^\[\[IMAGE:(\d+)]]$/);
    if(imageMatch){
      const index=Number(imageMatch[1]),image=images[index];
      if(image){const data=await imageForExport(image),scale=Math.min(1,520/data.width,350/data.height);elements.push(new Paragraph({children:[new ImageRun({data:data.bytes,transformation:{width:Math.max(1,Math.round(data.width*scale)),height:Math.max(1,Math.round(data.height*scale))}})]}));elements.push(new Paragraph({text:imageLabel(image,index)}))}
      i++;continue;
    }
    const heading=line.match(/^(#{1,6})\s+(.+)$/);
    if(heading){elements.push(new Paragraph({text:heading[2],heading:heading[1].length<=2?HeadingLevel.HEADING_1:HeadingLevel.HEADING_2}));i++;continue}
    if(/^\|.*\|$/.test(line)){
      const rows=[];
      while(i<lines.length&&/^\|.*\|$/.test(lines[i].trim())){
        const cells=lines[i++].trim().replace(/^\||\|$/g,'').split('|').map(value=>value.trim());
        if(!cells.every(value=>/^:?-{2,}:?$/.test(value)))rows.push(new TableRow({children:cells.map(value=>new TableCell({children:[new Paragraph(value)]}))}));
      }
      if(rows.length)elements.push(new Table({rows,width:{size:100,type:'pct'}}));
      continue;
    }
    if(/^[-*\u2022]\s+/.test(line))elements.push(new Paragraph({text:line.replace(/^[-*\u2022]\s+/,''),bullet:{level:0}}));
    else elements.push(new Paragraph({text:line,spacing:{after:150}}));
    i++;
  }
  return elements;
}
async function exportDocx(results){
  if(!window.docx)await loadScript('https://cdn.jsdelivr.net/npm/docx@8.5.0/build/index.umd.js');
  const {Document,Packer,Paragraph,HeadingLevel,ImageRun}=window.docx,children=[];
  for(let i=0;i<results.length;i++){
    const result=results[i];
    children.push(new Paragraph({text:results.length>1?`Source ${i+1}: ${result.title}`:result.title,heading:HeadingLevel.TITLE}));
    children.push(new Paragraph({text:`Source: ${result.source} | ${result.level} detail`}),...await docxSummary(result.summary,window.docx,result.recommended?result.images:[]));
    if(!result.recommended&&(result.images||[]).length){
      children.push(new Paragraph({text:'Images',heading:HeadingLevel.HEADING_1}));
      for(let j=0;j<result.images.length;j++){
        const image=result.images[j],data=await imageForExport(image),scale=Math.min(1,520/data.width,350/data.height);
        children.push(new Paragraph({children:[new ImageRun({data:data.bytes,transformation:{width:Math.max(1,Math.round(data.width*scale)),height:Math.max(1,Math.round(data.height*scale))}})]}));
        children.push(new Paragraph({text:imageLabel(image,j)}));
      }
    }
    if(i<results.length-1)children.push(new Paragraph({text:'',pageBreakBefore:true}));
  }
  download('briefly-summary.docx',await Packer.toBlob(new Document({sections:[{children}]})));
}
async function exportPdf(results){
  if(!window.jspdf)await loadScript('https://cdn.jsdelivr.net/npm/jspdf@2.5.2/dist/jspdf.umd.min.js');
  const pdf=new window.jspdf.jsPDF(),left=18,maxWidth=pdf.internal.pageSize.getWidth()-36;
  let y=20;
  function line(text,size=10,bold=false){
    pdf.setFont('helvetica',bold?'bold':'normal');pdf.setFontSize(size);
    for(const part of pdf.splitTextToSize(String(text),maxWidth)){
      if(y>278){pdf.addPage();y=20}
      pdf.text(part,left,y);y+=size*.52;
    }
    y+=3;
  }
  async function drawImage(image,index){
    const data=await imageForExport(image);
    // Browser pixels at 96 DPI give small source images a sensible print size.
    const millimetersPerPixel=25.4/96;
    const naturalWidth=data.width*millimetersPerPixel,naturalHeight=data.height*millimetersPerPixel;
    const scale=Math.min(1,maxWidth/naturalWidth,(520*millimetersPerPixel)/naturalHeight);
    const width=naturalWidth*scale,height=naturalHeight*scale;
    const caption=imageLabel(image,index);
    pdf.setFont('helvetica','normal');pdf.setFontSize(9);
    const captionLines=pdf.splitTextToSize(caption,maxWidth);
    const captionHeight=captionLines.length*5+8;
    if(y+height+captionHeight>280){pdf.addPage();y=20}
    pdf.addImage(data.dataUrl,'PNG',left,y,width,height,undefined,'FAST');
    y+=height+5;
    line(caption,9);
  }
  for(let i=0;i<results.length;i++){
    const result=results[i];if(i){pdf.addPage();y=20}
    line(results.length>1?`Source ${i+1}: ${result.title}`:result.title,17,true);
    line(`Source: ${result.source} | ${result.level} detail`,9);y+=5;
    const summaryLines=String(result.summary).replace(/\r/g,'').split('\n');
    for(let index=0;index<summaryLines.length;){
      const content=summaryLines[index].trim();
      if(!content){y+=2;index++;continue}
      const imageMatch=content.match(/^\[\[IMAGE:(\d+)]]$/);
      if(imageMatch){
        const imageIndex=Number(imageMatch[1]),image=(result.images||[])[imageIndex];
        if(image)await drawImage(image,imageIndex);
        index++;continue;
      }
      if(/^\|.*\|$/.test(content)){
        const rows=[];
        while(index<summaryLines.length&&/^\|.*\|$/.test(summaryLines[index].trim())){
          const cells=summaryLines[index++].trim().replace(/^\||\|$/g,'').split('|').map(value=>value.trim());
          if(!cells.every(value=>/^:?-{2,}:?$/.test(value)))rows.push(cells);
        }
        if(!rows.length)continue;
        const columns=Math.max(...rows.map(row=>row.length)),cellWidth=maxWidth/columns;
        rows.forEach((row,rowIndex)=>{
          const wrapped=Array.from({length:columns},(_,column)=>pdf.splitTextToSize(row[column]||'',cellWidth-6));
          const height=Math.max(9,...wrapped.map(parts=>parts.length*4+5));
          if(y+height>282){pdf.addPage();y=20}
          pdf.setFont('helvetica',rowIndex===0?'bold':'normal');pdf.setFontSize(9);
          for(let column=0;column<columns;column++){
            const x=left+column*cellWidth;
            if(rowIndex===0){pdf.setFillColor(239,245,239);pdf.rect(x,y,cellWidth,height,'F')}
            pdf.rect(x,y,cellWidth,height);
            wrapped[column].forEach((part,lineIndex)=>pdf.text(part,x+3,y+5+lineIndex*4));
          }
          y+=height;
        });
        y+=5;continue;
      }
      if(/^#{1,6}\s+/.test(content))line(content.replace(/^#{1,6}\s+/,''),13,true);
      else line(content,10);
      index++;
    }
    if(!result.recommended&&(result.images||[]).length){
      y+=5;line('Images',14,true);
      for(let j=0;j<result.images.length;j++)await drawImage(result.images[j],j);
    }
  }
  pdf.save('briefly-summary.pdf');
}
$('#exportMenu').addEventListener('click',async event=>{
  const format=event.target.dataset.export,results=exportResults();
  if(!format||!results.length)return;
  $('#exportMenu').classList.add('hidden');clearError();
  $('#exportButton').disabled=true;$('#exportButton').textContent='Preparing...';
  try{
    if(format==='txt')download('briefly-summary.txt',textExport(results),'text/plain');
    else if(format==='docx')await exportDocx(results);
    else if(format==='pdf')await exportPdf(results);
  }catch(error){$('#errorMessage').textContent=`Export failed: ${error.message}`}
  finally{$('#exportButton').disabled=false;$('#exportButton').innerHTML='&#8595; &nbsp; Export <span>&#8964;</span>'}
});
function download(name,data,type){const blob=data instanceof Blob?data:new Blob([data],{type});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000)}function loadScript(src){return new Promise((resolve,reject)=>{const s=document.createElement('script');s.src=src;s.onload=resolve;s.onerror=()=>reject(new Error('Could not load the export library. Check your internet connection.'));document.head.append(s)})}
