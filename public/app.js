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

const nameInput = document.getElementById('nameInput');
const sendLang = document.getElementById('sendLang');
const viewLang = document.getElementById('viewLang');
const msgInput = document.getElementById('msgInput');
const sendBtn = document.getElementById('sendBtn');
const chatBox = document.getElementById('chatBox');

function buildSelect(selectElement) {
  selectElement.innerHTML = languages
    .map(l => `<option value="${l.code}">${l.label}</option>`)
    .join('');
}

buildSelect(sendLang);
buildSelect(viewLang);

nameInput.value = localStorage.getItem('chatName') || '';
sendLang.value = localStorage.getItem('sendLang') || 'en';
viewLang.value = localStorage.getItem('viewLang') || 'en';

nameInput.addEventListener('change', () => {
  localStorage.setItem('chatName', nameInput.value);
});

sendLang.addEventListener('change', () => {
  localStorage.setItem('sendLang', sendLang.value);
});

viewLang.addEventListener('change', () => {
  localStorage.setItem('viewLang', viewLang.value);
  fetchMessages();
});

sendBtn.addEventListener('click', sendMessage);
msgInput.addEventListener('keypress', (e) => {
  if (e.key === 'Enter') sendMessage();
});

function sendMessage() {
  const sender = nameInput.value.trim() || 'Anon';
  const lang = sendLang.value;
  const text = msgInput.value.trim();
  if (!text) return;

  localStorage.setItem('chatName', sender);

  fetch('/send', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sender, text, lang })
  }).then(() => {
    msgInput.value = '';
    fetchMessages();
  });
}

function fetchMessages() {
  const lang = viewLang.value;
  fetch('/messages?lang=' + encodeURIComponent(lang))
    .then(res => res.json())
    .then(data => {
      chatBox.innerHTML = '';
      data.forEach(m => {
        const div = document.createElement('div');
        div.className = 'msg';
        div.innerHTML = `<span class="sender">${m.sender}</span>${m.text}`;
        chatBox.appendChild(div);
      });
      chatBox.scrollTop = chatBox.scrollHeight;
    });
}

setInterval(fetchMessages, 2000);
fetchMessages();
