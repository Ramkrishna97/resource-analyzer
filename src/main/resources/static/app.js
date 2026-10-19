const $ = s => document.querySelector(s);
const state = {tab:'file',level:'MEDIUM',file:null,result:null};
document.querySelectorAll('.tab').forEach(button=>button.addEventListener('click',()=>{
  document.querySelectorAll('.tab').forEach(b=>b.classList.toggle('active',b===button));
  state.tab=button.dataset.tab; $('#filePanel').classList.toggle('hidden',state.tab!=='file'); $('#urlPanel').classList.toggle('hidden',state.tab!=='url'); clearError();
}));
document.querySelectorAll('.level').forEach(button=>button.addEventListener('click',()=>{
  document.querySelectorAll('.level').forEach(b=>{const on=b===button;b.classList.toggle('selected',on);b.setAttribute('aria-checked',on?'true':'false')});state.level=button.dataset.level;
}));
$('#fileInput').addEventListener('change',e=>setFile(e.target.files[0]));
function setFile(file){if(!file)return;state.file=file;$('#fileTitle').innerHTML='File ready to summarize';$('#fileSub').textContent='Click to choose a different file';$('#fileName').textContent=file.name}
const dropzone=$('#dropzone');dropzone.addEventListener('dragover',e=>{e.preventDefault();dropzone.classList.add('dragover')});dropzone.addEventListener('dragleave',()=>dropzone.classList.remove('dragover'));dropzone.addEventListener('drop',e=>{e.preventDefault();dropzone.classList.remove('dragover');setFile(e.dataTransfer.files[0])});
$('#engineSelect').addEventListener('change',()=>{const ollama=$('#engineSelect').value==='ollama';$('#modelRow').classList.toggle('hidden',!ollama);$('#engineHint').textContent=ollama?'Runs locally via Ollama. Download a model first.':'Works offline. No setup required.'});
let ollamaModels=[];fetch('/api/status').then(r=>r.json()).then(s=>{ollamaModels=s.models||[];const status=$('#ollamaStatus');if(s.ollamaAvailable){status.textContent='Ollama connected';status.classList.add('available');if(s.defaultModel)$('#modelInput').value=s.defaultModel}else status.textContent='Ollama not running'}).catch(()=>$('#ollamaStatus').textContent='Ollama not available');
function clearError(){$('#errorMessage').textContent=''}
$('#submitButton').addEventListener('click',async()=>{
  clearError();const engine=$('#engineSelect').value;let response;
  try{
    $('#submitButton').disabled=true;$('#submitButton span:first-child').textContent='Reading your source…';
    if(state.tab==='file'){
      if(!state.file)throw new Error('Choose a PDF, Word, or Excel file first.');
      const data=new FormData();data.append('file',state.file);data.append('level',state.level);data.append('engine',engine);data.append('model',$('#modelInput').value);
      response=await fetch('/api/analyze/file',{method:'POST',body:data});
    }else{
      const url=$('#urlInput').value.trim();if(!url)throw new Error('Paste a website URL first.');
      response=await fetch('/api/analyze/url',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({url,level:state.level,engine,model:$('#modelInput').value})});
    }
    const data=await response.json();if(!response.ok)throw new Error(data.message||data.error||'Unable to analyze this source.');render(data);
  }catch(error){$('#errorMessage').textContent=error.message||'Something went wrong. Please try again.'}
  finally{$('#submitButton').disabled=false;$('#submitButton span:first-child').textContent='Make my summary'}
});
function escapeHtml(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
function render(data){state.result=data;$('#resultCard').classList.remove('hidden');$('#resultTitle').textContent=data.title||'Summary';$('#resultMeta').textContent=`${data.originalWords.toLocaleString()} source words · ${data.summaryWords.toLocaleString()} summary words · ${data.level.toLowerCase()} detail · ${data.summarizer}`;$('#summaryBody').innerHTML=markdown(data.summary);$('#sourceText').textContent=data.extractedText;$('#sourceCount').textContent=`(${data.originalWords.toLocaleString()} words)`;const links=data.links||[];$('#linksBlock').classList.toggle('hidden',!links.length);$('#linksList').innerHTML=links.map(link=>`<a href="${escapeHtml(link)}" target="_blank" rel="noopener noreferrer">${escapeHtml(link)}</a>`).join('');$('#resultCard').scrollIntoView({behavior:'smooth',block:'nearest'})}
function markdown(text){return String(text).split(/\n\s*\n/).map(block=>{let s=escapeHtml(block.trim());if(!s)return'';if(/^###?\s/.test(s))return `<h3>${s.replace(/^###?\s*/,'')}</h3>`;if(/^(\* |\- |• )/m.test(s))return'<ul>'+s.split('\n').map(l=>`<li>${l.replace(/^(\* |\- |• )/,'')}</li>`).join('')+'</ul>';return `<p>${s.replace(/\n/g,'<br>')}</p>`}).join('')}
$('#exportButton').addEventListener('click',e=>{e.stopPropagation();$('#exportMenu').classList.toggle('hidden')});document.addEventListener('click',e=>{if(!e.target.closest('.export-wrap'))$('#exportMenu').classList.add('hidden')});
$('#exportMenu').addEventListener('click',async e=>{const format=e.target.dataset.export;if(!format||!state.result)return;$('#exportMenu').classList.add('hidden');const d=state.result;try{if(format==='txt')return download(`${d.title.replace(/[^a-z0-9-_ ]/gi,'').slice(0,60)||'summary'}.txt`,`${d.title}\n${d.source}\nDetail: ${d.level}\n\n${d.summary}`,'text/plain');if(format==='docx'){if(!window.docx)await loadScript('https://cdn.jsdelivr.net/npm/docx@8.5.0/build/index.umd.js');const {Document,Packer,Paragraph,HeadingLevel}=window.docx;const children=[new Paragraph({text:d.title,heading:HeadingLevel.TITLE}),new Paragraph({text:`Source: ${d.source} · ${d.level} detail`}),...d.summary.split(/\n+/).filter(Boolean).map(s=>new Paragraph(s))];const blob=await Packer.toBlob(new Document({sections:[{children}]}));download('summary.docx',blob);return}if(format==='pdf'){if(!window.jspdf)await loadScript('https://cdn.jsdelivr.net/npm/jspdf@2.5.2/dist/jspdf.umd.min.js');const pdf=new window.jspdf.jsPDF();pdf.setFont('helvetica','bold');pdf.setFontSize(18);pdf.text(pdf.splitTextToSize(d.title,175),18,22);pdf.setFont('helvetica','normal');pdf.setFontSize(10);let y=37;for(const line of pdf.splitTextToSize(`Source: ${d.source} · ${d.level} detail`,175)){pdf.text(line,18,y);y+=5}y+=8;for(const line of pdf.splitTextToSize(d.summary,175)){if(y>280){pdf.addPage();y=20}pdf.text(line,18,y);y+=5}pdf.save('summary.pdf')}}catch(err){$('#errorMessage').textContent=`Export failed: ${err.message}`}});
function download(name,data,type){const blob=data instanceof Blob?data:new Blob([data],{type});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000)}function loadScript(src){return new Promise((resolve,reject)=>{const s=document.createElement('script');s.src=src;s.onload=resolve;s.onerror=()=>reject(new Error('Could not load the export library. Check your internet connection.'));document.head.append(s)})}
