async function post(path, body) {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.error || "request failed");
  return payload;
}

function toast(message, isError = false) {
  const el = document.getElementById("toast");
  el.textContent = message;
  el.classList.toggle("error", isError);
  el.hidden = false;
  setTimeout(() => (el.hidden = true), 3600);
}

const loginForm = document.getElementById("loginForm");
const signupForm = document.getElementById("signupForm");
const loginTab = document.getElementById("loginTab");
const signupTab = document.getElementById("signupTab");

function showTab(signup) {
  signupForm.hidden = !signup;
  loginForm.hidden = signup;
  signupTab.classList.toggle("active", signup);
  loginTab.classList.toggle("active", !signup);
}

loginTab.addEventListener("click", () => showTab(false));
signupTab.addEventListener("click", () => showTab(true));

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = Object.fromEntries(new FormData(event.target).entries());
  try {
    await post("/api/auth/login", form);
    window.location.replace("/index.html");
  } catch (error) {
    toast(error.message, true);
  }
});

signupForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = Object.fromEntries(new FormData(event.target).entries());
  try {
    await post("/api/auth/signup", form);
    await post("/api/auth/login", { username: form.username, password: form.password });
    window.location.replace("/index.html");
  } catch (error) {
    toast(error.message, true);
  }
});
