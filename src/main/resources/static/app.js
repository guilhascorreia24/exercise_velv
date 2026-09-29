// Product Catalog frontend - talks to the Spring Boot REST API:
// GET    /api/products?page=0&size=20&search=...
// POST   /api/products
// DELETE /api/products/{id}

const API_URL = '/api/products';
const DEFAULT_SIZE = 20;
const MAX_SIZE = 100; // backend caps page size at 100

// DOM refs
const productsBody = document.getElementById('productsBody');
const prevBtn = document.getElementById('prevBtn');
const nextBtn = document.getElementById('nextBtn');
const pageInfo = document.getElementById('pageInfo');
const statusEl = document.getElementById('status');
const searchInput = document.getElementById('searchInput');
const searchBtn = document.getElementById('searchBtn');
const addForm = document.getElementById('addForm');

// State
let page = 0;
let size = DEFAULT_SIZE;
let totalPages = 0;
let totalElements = 0;
let currentSearch = '';

// Utilities
function showStatus(text, type='loading'){
  statusEl.textContent = text;
  statusEl.className = 'status ' + (type||'');
  statusEl.classList.remove('hidden');
}
function hideStatus(){
  statusEl.className = 'status hidden';
}
function flashStatus(text, ms=1500){
  showStatus(text, 'success');
  setTimeout(()=> hideStatus(), ms);
}

// API client
class ApiError extends Error {
  constructor(status, body){
    super(formatApiError(status, body));
    this.status = status;
    this.body = body;
  }
}

// Turns the backend error payload { timestamp, status, message, errors? } into readable text
function formatApiError(status, body){
  if(!body || !body.message) return `Request failed (HTTP ${status})`;
  if(body.errors){
    return body.message + ': ' + Object.values(body.errors).join('; ');
  }
  return body.message;
}

async function request(url, options = {}){
  const headers = { 'Accept': 'application/json' };
  if(options.body) headers['Content-Type'] = 'application/json';

  let res;
  try{
    res = await fetch(url, { ...options, headers });
  }catch(err){
    throw new Error('Network error: could not reach the server');
  }

  if(!res.ok){
    let body = null;
    try{ body = await res.json(); }catch(_){ /* non-JSON error body */ }
    throw new ApiError(res.status, body);
  }
  return res.status === 204 ? null : res.json();
}

function getProducts({ page=0, size=DEFAULT_SIZE, search='' } = {}){
  const params = new URLSearchParams({ page, size });
  if(search && search.trim()) params.set('search', search.trim());
  return request(`${API_URL}?${params}`);
}

function addProduct(dto){
  return request(API_URL, { method: 'POST', body: JSON.stringify(dto) });
}

function deleteProduct(id){
  return request(`${API_URL}/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

// Render
function renderProducts(items){
  productsBody.innerHTML = '';
  if(!items.length){
    const tr = document.createElement('tr');
    tr.innerHTML = `<td colspan="5">No products found.</td>`;
    productsBody.appendChild(tr);
    return;
  }
  for(const p of items){
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${p.id}</td>
      <td>${escapeHtml(p.name)}</td>
      <td>${escapeHtml(p.category)}</td>
      <td>${formatPrice(p.price)}</td>
      <td><button class="delete" data-id="${p.id}">Delete</button></td>
    `;
    productsBody.appendChild(tr);
  }
}

function updatePagination(info){
  page = info.page;
  size = info.size;
  totalPages = info.totalPages;
  totalElements = info.totalElements;
  const shownPages = Math.max(1, totalPages);
  pageInfo.textContent = `Page ${page + 1} of ${shownPages} — ${totalElements} items`;
  prevBtn.disabled = page <= 0;
  nextBtn.disabled = page >= totalPages - 1;
}

// helpers
function escapeHtml(s){
  return String(s).replace(/[&<>"']/g, (c)=> ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":"&#39;"}[c]));
}
function formatPrice(v){
  return typeof v === 'number' ? v.toFixed(2) : v;
}

// App actions
async function load(){
  showStatus('Loading...', 'loading');
  try{
    const res = await getProducts({ page, size, search: currentSearch });
    // e.g. after deleting the last item of the last page: step back to the previous page
    if(!res.items.length && res.page > 0 && res.totalElements > 0){
      page = Math.max(0, res.totalPages - 1);
      return load();
    }
    renderProducts(res.items);
    updatePagination(res);
    hideStatus();
  }catch(err){
    showStatus(err.message || 'Failed to load products', 'error');
  }
}

async function doSearch(){
  currentSearch = searchInput.value || '';
  page = 0;
  await load();
}

async function doAdd(e){
  e.preventDefault();
  const name = document.getElementById('name').value.trim();
  const category = document.getElementById('category').value.trim();
  const priceRaw = document.getElementById('price').value;
  const price = priceRaw === '' ? null : Number(priceRaw);
  showStatus('Adding product...', 'loading');
  try{
    const added = await addProduct({ name, category, price });
    addForm.reset();
    await load();
    flashStatus(`Product #${added.id} "${added.name}" added`, 2500);
  }catch(err){
    showStatus(err.message || 'Add failed', 'error');
  }
}

async function doDelete(id){
  if(!confirm('Delete product #' + id + '?')) return;
  showStatus('Deleting...', 'loading');
  try{
    await deleteProduct(id);
    await load();
    flashStatus(`Product #${id} deleted`);
  }catch(err){
    showStatus(err.message || 'Delete failed', 'error');
  }
}

// wire events
prevBtn.addEventListener('click', ()=>{ if(page>0){ page--; load(); }});
nextBtn.addEventListener('click', ()=>{ if(page<totalPages-1){ page++; load(); }});
searchBtn.addEventListener('click', doSearch);
searchInput.addEventListener('keydown', (e)=>{ if(e.key === 'Enter'){ e.preventDefault(); doSearch(); }});
addForm.addEventListener('submit', doAdd);

productsBody.addEventListener('click', (e)=>{
  if(e.target.matches('button.delete')){
    const id = Number(e.target.dataset.id);
    doDelete(id);
  }
});

// init
(function init(){
  // optional initial paging from the querystring, e.g. /?page=2&size=50
  const params = new URL(window.location).searchParams;
  const qPage = Number.parseInt(params.get('page'), 10);
  const qSize = Number.parseInt(params.get('size'), 10);
  if(Number.isInteger(qPage)) page = Math.max(0, qPage);
  if(Number.isInteger(qSize)) size = Math.max(1, Math.min(MAX_SIZE, qSize));
  load();
})();
