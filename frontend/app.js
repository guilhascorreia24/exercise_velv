// Simple frontend prototype with mocked API
// Structured to match eventual endpoints:
// GET /api/products?page=0&size=20&search=...
// POST /api/products
// DELETE /api/products/{id}

const PRODUCTS_KEY = '__mock_products_v1';

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
let size = 5; // default page size for demo
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

// Mock backend implementation
function seedMockData(){
  if(localStorage.getItem(PRODUCTS_KEY)) return;
  const sample = [
    ['Wireless Headphones','Electronics',79.99],
    ['Gaming Mouse','Electronics',49.90],
    ['Mechanical Keyboard','Electronics',109.50],
    ['Office Chair','Furniture',199.99],
    ['Standing Desk','Furniture',349.00],
    ['Coffee Mug','Kitchen',9.99],
    ['Water Bottle','Outdoors',14.99],
    ['Bluetooth Speaker','Electronics',59.00],
    ['USB-C Cable','Electronics',6.50],
    ['External SSD','Electronics',129.00],
    ['LED Monitor','Electronics',189.99],
    ['Webcam','Electronics',39.99],
    ['Noise Cancelling Earbuds','Electronics',99.00],
    ['Smartwatch','Electronics',149.00],
    ['Desk Lamp','Home',29.99],
    ['Notebook','Stationery',3.99],
    ['Ballpoint Pen','Stationery',1.50],
    ['Backpack','Accessories',45.00],
    ['Running Shoes','Footwear',85.00],
    ['Sunglasses','Accessories',65.00]
  ];
  const list = sample.map((s, i) => ({ id: i+1, name: s[0], category: s[1], price: s[2] }));
  localStorage.setItem(PRODUCTS_KEY, JSON.stringify(list));
}

function readMockProducts(){
  const raw = localStorage.getItem(PRODUCTS_KEY) || '[]';
  return JSON.parse(raw);
}
function writeMockProducts(list){
  localStorage.setItem(PRODUCTS_KEY, JSON.stringify(list));
}

// Mock API: returns a Promise to simulate network
function mockGetProducts({ page=0, size=20, search='' } = {}){
  return new Promise((resolve) => {
    setTimeout(() => {
      let all = readMockProducts();
      if(search && search.trim()){ 
        const q = search.trim().toLowerCase();
        all = all.filter(p => p.name.toLowerCase().includes(q));
      }
      const total = all.length;
      const totalPages = Math.max(1, Math.ceil(total / size));
      const start = page * size;
      const items = all.slice(start, start + size);
      resolve({ items, page, size, totalElements: total, totalPages });
    }, 300);
  });
}

function mockAddProduct(dto){
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if(!dto.name || !dto.category || dto.price == null) return reject({ message: 'Validation failed' });
      const list = readMockProducts();
      const id = list.length ? Math.max(...list.map(p=>p.id)) + 1 : 1;
      const newP = { id, ...dto };
      list.unshift(newP); // add to front
      writeMockProducts(list);
      resolve(newP);
    }, 250);
  });
}

function mockDeleteProduct(id){
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      let list = readMockProducts();
      const idx = list.findIndex(p=>p.id===id);
      if(idx === -1) return reject({ message: 'Not found' });
      list.splice(idx,1);
      writeMockProducts(list);
      resolve();
    }, 200);
  });
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
  pageInfo.textContent = `Page ${page + 1} of ${totalPages} — ${totalElements} items`;
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
    const res = await mockGetProducts({ page, size, search: currentSearch });
    renderProducts(res.items);
    updatePagination(res);
    hideStatus();
  }catch(err){
    showStatus('Failed to load products', 'error');
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
  const price = parseFloat(document.getElementById('price').value);
  showStatus('Adding product...', 'loading');
  try{
    const added = await mockAddProduct({ name, category, price });
    document.getElementById('name').value = '';
    document.getElementById('category').value = '';
    document.getElementById('price').value = '';
    showStatus('Product added', 'loading');
    setTimeout(()=> hideStatus(), 700);
    await load();
  }catch(err){
    showStatus(err.message || 'Add failed', 'error');
  }
}

async function doDelete(id){
  if(!confirm('Delete product #' + id + '?')) return;
  showStatus('Deleting...', 'loading');
  try{
    await mockDeleteProduct(id);
    showStatus('Deleted', 'loading');
    setTimeout(()=> hideStatus(), 600);
    await load();
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
  seedMockData();
  // read default page params from querystring (optional)
  const url = new URL(window.location);
  const qPage = Number(url.searchParams.get('page'));
  const qSize = Number(url.searchParams.get('size'));
  if(!Number.isNaN(qPage)) page = Math.max(0, qPage);
  if(!Number.isNaN(qSize)) size = Math.max(1, Math.min(100, qSize));
  load();
})();
