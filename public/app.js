let lastCount = 0;

document.getElementById('sendBtn').addEventListener('click', sendMessage);
document.getElementById('msgInput').addEventListener('keypress', (e) => {
  if (e.key === 'Enter') sendMessage();
});

function sendMessage() {
  const sender = document.getElementById('nameInput').value || 'Anon';
  const lang = document.getElementById('langSelect').value;
  const text = document.getElementById('msgInput').value;
  if (!text.trim()) return;

  fetch('/send', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sender, text, lang })
  }).then(() => {
    document.getElementById('msgInput').value = '';
    fetchMessages();
  });
}

function fetchMessages() {
  const lang = document.getElementById('langSelect').value;
  fetch('/messages?lang=' + lang)
    .then(res => res.json())
    .then(data => {
      const box = document.getElementById('chatBox');
      box.innerHTML = '';
      data.forEach(m => {
        const div = document.createElement('div');
        div.className = 'msg';
        div.innerHTML = `<span class="sender">${m.sender}</span>${m.text}`;
        box.appendChild(div);
      });
      box.scrollTop = box.scrollHeight;
    });
}

setInterval(fetchMessages, 2000);
fetchMessages();
