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
const friendInput = document.getElementById('friendInput');
const addFriendBtn = document.getElementById('addFriendBtn');
const friendInputChat = document.getElementById('friendInputChat');
const addFriendBtnChat = document.getElementById('addFriendBtnChat');
const friendsList = document.getElementById('friendsList');

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
const savedFriends = JSON.parse(localStorage.getItem('friends') || '[]');
let friends = Array.isArray(savedFriends) ? savedFriends : [];
let currentFriend = localStorage.getItem('currentFriend') || friends[0] || '';

nameInput.value = savedName;
sendLang.value = savedSend;
viewLang.value = savedView;
friendInput.value = '';

function updateMeta() {
  const name = localStorage.getItem('chatName') || 'Anon';
  const targetFriend = currentFriend ? ` with ${currentFriend}` : '';
  welcomeText.textContent = `Hello, ${name}${targetFriend}!`;
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
  const newFriend = friendInput.value.trim();
  if (newFriend) {
    addFriend(newFriend);
    friendInput.value = '';
  }
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

addFriendBtn.addEventListener('click', () => {
  const name = friendInput.value.trim();
  if (name) {
    addFriend(name);
    friendInput.value = '';
  }
});

addFriendBtnChat.addEventListener('click', () => {
  const name = friendInputChat.value.trim();
  if (name) {
    addFriend(name);
    friendInputChat.value = '';
  }
});

friendInput.addEventListener('keypress', (e) => {
  if (e.key === 'Enter') {
    e.preventDefault();
    addFriendBtn.click();
  }
});

friendInputChat.addEventListener('keypress', (e) => {
  if (e.key === 'Enter') {
    e.preventDefault();
    addFriendBtnChat.click();
  }
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
  renderFriends();
  updateMeta();
  fetchMessages();
  msgInput.focus();
}

function sendMessage() {
  const sender = localStorage.getItem('chatName') || 'Anon';
  const lang = sendLang.value;
  const text = msgInput.value.trim();
  const friend = currentFriend || '';
  if (!text) return;

  fetch('/send', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sender, text, lang, friend })
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
  const user = localStorage.getItem('chatName') || '';
  const params = new URLSearchParams({ lang });
  if (user) params.set('user', user);
  if (currentFriend) params.set('friend', currentFriend);

  fetch('/messages?' + params.toString())
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

function renderFriends() {
  if (!friends.length) {
    friendsList.innerHTML = '<div class="friend-empty">No friends yet. Add one above.</div>';
    return;
  }

  friendsList.innerHTML = friends
    .map(friend => {
      const active = friend === currentFriend ? ' friend-active' : '';
      return `<button type="button" class="friend-button${active}" data-friend="${escapeHtml(friend)}">${escapeHtml(friend)}</button>`;
    })
    .join('');

  Array.from(friendsList.querySelectorAll('.friend-button')).forEach(button => {
    button.addEventListener('click', () => {
      setCurrentFriend(button.dataset.friend);
    });
  });
}

function addFriend(name) {
  if (!name) return;
  if (!friends.includes(name)) {
    friends.push(name);
    localStorage.setItem('friends', JSON.stringify(friends));
  }
  setCurrentFriend(name);
}

function setCurrentFriend(name) {
  currentFriend = name;
  localStorage.setItem('currentFriend', currentFriend);
  renderFriends();
  updateMeta();
  fetchMessages();
}

if (savedName) {
  openChat();
}

setInterval(() => {
  if (!chatScreen.classList.contains('hidden')) {
    fetchMessages();
  }
}, 2000);
