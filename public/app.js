const languages = [
  { code: 'en', label: 'English' },
  { code: 'es', label: 'Spanish' },
  { code: 'fr', label: 'French' },
  { code: 'de', label: 'German' },
  { code: 'it', label: 'Italian' },
  { code: 'pt', label: 'Portuguese' },
  { code: 'ja', label: 'Japanese' },
  { code: 'zh', label: 'Chinese' },
  { code: 'ru', label: 'Russian' },
  { code: 'ko', label: 'Korean' }
];

const loginScreen = document.getElementById('loginScreen');
const chatScreen = document.getElementById('chatScreen');
const nameInput = document.getElementById('nameInput');
const sendLang = document.getElementById('sendLang');
const viewLang = document.getElementById('viewLang');
const msgInput = document.getElementById('msgInput');
const sendBtn = document.getElementById('sendBtn');
const chatBox = document.getElementById('chatBox');
const loginBtn = document.getElementById('loginBtn');
const logoutBtn = document.getElementById('logoutBtn');
const welcomeText = document.getElementById('welcomeText');
const languageText = document.getElementById('languageText');

function buildSelect(selectElement) {
  selectElement.innerHTML = languages
    .map(l => `<option value="${l.code}">${l.label}</option>`)
    .join('');
}

buildSelect(sendLang);
buildSelect(viewLang);

const savedName = localStorage.getItem('chatName') || '';
const savedSend = localStorage.getItem('sendLang') || 'en';
const savedView = localStorage.getItem('viewLang') || 'en';

nameInput.value = savedName;
sendLang.value = savedSend;
viewLang.value = savedView;

function updateMeta() {
  welcomeText.textContent = `Hello, ${localStorage.getItem('chatName') || 'Anon'}!`;
  languageText.textContent = `Writing in ${languageLabel(sendLang.value)}, seeing in ${languageLabel(viewLang.value)}`;
}

function languageLabel(code) {
  const lang = languages.find(l => l.code === code);
  return lang ? lang.label : code;
}

loginBtn.addEventListener('click', () => {
  const name = nameInput.value.trim() || 'Anon';
  localStorage.setItem('chatName', name);
  localStorage.setItem('sendLang', sendLang.value);
  localStorage.setItem('viewLang', viewLang.value);
  openChat();
});

logoutBtn.addEventListener('click', () => {
  loginScreen.classList.remove('hidden');
  chatScreen.classList.add('hidden');
});

sendLang.addEventListener('change', () => {
  localStorage.setItem('sendLang', sendLang.value);
  updateMeta();
});

viewLang.addEventListener('change', () => {
  localStorage.setItem('viewLang', viewLang.value);
  updateMeta();
  fetchMessages();
});

sendBtn.addEventListener('click', sendMessage);
msgInput.addEventListener('keypress', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
});

function openChat() {
  loginScreen.classList.add('hidden');
  chatScreen.classList.remove('hidden');
  updateMeta();
  fetchMessages();
  msgInput.focus();
}

function sendMessage() {
  const sender = localStorage.getItem('chatName') || 'Anon';
  const lang = sendLang.value;
  const text = msgInput.value.trim();
  if (!text) return;

  fetch('/send', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sender, text, lang })
  })
    .then(res => {
      if (!res.ok) throw new Error('Send failed');
      msgInput.value = '';
      fetchMessages();
    })
    .catch(() => {
      alert('Unable to send message. Please try again.');
    });
}

function fetchMessages() {
  const lang = viewLang.value;
  fetch('/messages?lang=' + encodeURIComponent(lang))
    .then(res => {
      if (!res.ok) throw new Error('Fetch failed');
      return res.json();
    })
    .then(data => {
      chatBox.innerHTML = '';
      if (data.length === 0) {
        chatBox.innerHTML = '<div class="empty-state">No messages yet. Start the conversation!</div>';
      } else {
        data.forEach(m => {
          const div = document.createElement('div');
          div.className = 'msg';
          div.innerHTML = `
            <div class="msg-header">
              <span class="sender">${escapeHtml(m.sender)}</span>
              <span class="lang-tag">${escapeHtml(m.lang || 'en')}</span>
            </div>
            <div class="msg-body">${escapeHtml(m.text)}</div>
          `;
          chatBox.appendChild(div);
        });
      }
      chatBox.scrollTop = chatBox.scrollHeight;
    })
    .catch(() => {
      chatBox.innerHTML = '<div class="empty-state">Unable to load messages.</div>';
    });
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

if (savedName) {
  openChat();
}

setInterval(() => {
  if (!chatScreen.classList.contains('hidden')) {
    fetchMessages();
  }
}, 2000);
