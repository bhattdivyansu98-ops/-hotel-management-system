const api = {
  async request(method, path, body) {
    const response = await fetch(path, {
      method,
      headers: body ? { "Content-Type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
    if (response.status === 401) {
      window.location.replace("/login.html");
      throw new Error("signed out");
    }
    if (response.status === 204) return null;
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || `${method} ${path} failed`);
    return payload;
  },
  get: (path) => api.request("GET", path),
  post: (path, body) => api.request("POST", path, body),
};

const state = { guests: [], rooms: [], reservations: [], selectedRoom: null };

const money = (value) =>
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 })
    .format(Number(value ?? 0));

function toast(message, isError = false) {
  const el = document.getElementById("toast");
  el.textContent = message;
  el.classList.toggle("error", isError);
  el.hidden = false;
  setTimeout(() => (el.hidden = true), 3600);
}

async function guard(action) {
  try {
    await action();
  } catch (error) {
    toast(error.message, true);
  }
}

async function loadSession() {
  const user = await api.get("/api/auth/me");
  document.getElementById("userName").textContent = `${user.fullName} · ${user.role.toLowerCase()}`;
}

async function loadStats() {
  const stats = await api.get("/api/stats");
  document.getElementById("stats").innerHTML = `
    <div class="stat"><span>Rooms</span><strong>${stats.rooms}</strong></div>
    <div class="stat"><span>Guests</span><strong>${stats.guests}</strong></div>
    <div class="stat"><span>Reservations</span><strong>${stats.reservations}</strong></div>
    <div class="stat"><span>Occupancy today</span><strong>${Math.round(stats.occupancyRate * 100)}%</strong></div>
    <div class="stat"><span>Revenue collected</span><strong>${money(stats.revenue)}</strong></div>`;
}

async function loadHealth() {
  const health = await api.get("/api/health");
  document.getElementById("gatewayBadge").textContent = `payments: ${health.paymentProvider}`;
}

async function loadGuests() {
  state.guests = await api.get("/api/guests");
  document.getElementById("guestList").innerHTML = state.guests
    .map((guest) => `<li>#${guest.id} — ${guest.fullName} · ${guest.email}</li>`)
    .join("");
  document.querySelector("#bookingForm select[name=guestId]").innerHTML = state.guests
    .map((guest) => `<option value="${guest.id}">#${guest.id} ${guest.fullName}</option>`)
    .join("");
}

async function loadReservations() {
  state.reservations = await api.get("/api/reservations");
  const rooms = new Map((await api.get("/api/rooms")).map((room) => [room.id, room]));
  const guests = new Map(state.guests.map((guest) => [guest.id, guest]));

  document.querySelector("#reservationTable tbody").innerHTML = state.reservations
    .map((reservation) => {
      const room = rooms.get(reservation.roomId);
      const guest = guests.get(reservation.guestId);
      const closed = reservation.status === "CANCELLED" || reservation.status === "CHECKED_OUT";
      const due = closed ? 0 : Number(reservation.totalAmount) - Number(reservation.paidAmount);
      return `<tr>
        <td>${reservation.id}</td>
        <td>${guest ? guest.fullName : reservation.guestId}</td>
        <td>${room ? `${room.number} (${room.type})` : reservation.roomId}</td>
        <td>${reservation.checkIn} → ${reservation.checkOut}</td>
        <td><span class="badge ${reservation.status}">${reservation.status}</span></td>
        <td>${money(reservation.totalAmount)}</td>
        <td>${money(reservation.paidAmount)}</td>
        <td class="row-actions">
          ${due > 0 ? `<button data-pay="${reservation.id}" data-due="${due}">Pay</button>` : ""}
          ${reservation.status === "CONFIRMED" ? `<button class="ghost" data-checkin="${reservation.id}">Check in</button>` : ""}
          ${reservation.status === "CHECKED_IN" ? `<button class="ghost" data-checkout="${reservation.id}">Check out</button>` : ""}
          ${closed || reservation.status === "CHECKED_IN" ? "" : `<button class="ghost" data-cancel="${reservation.id}">Cancel</button>`}
          <button class="ghost" data-invoice="${reservation.id}">Invoice</button>
        </td>
      </tr>`;
    })
    .join("");
}

function renderAvailableRooms(rooms) {
  const container = document.getElementById("availableRooms");
  if (rooms.length === 0) {
    container.innerHTML = `<p style="color:var(--muted);font-size:13px">No rooms free for those dates.</p>`;
    return;
  }
  container.innerHTML = rooms
    .map(
      (room) => `<article class="room-card" data-room="${room.id}">
        <h3>Room ${room.number}</h3>
        <p>${room.type} · floor ${room.floor} · up to ${occupancy(room.type)} guests</p>
        <div class="rate">${money(room.nightlyRate ?? baseRate(room.type))} / night</div>
      </article>`
    )
    .join("");
}

const RATES = { SINGLE: 1500, DOUBLE: 2400, DELUXE: 3800, SUITE: 6500 };
const OCCUPANCY = { SINGLE: 1, DOUBLE: 2, DELUXE: 3, SUITE: 4 };
const baseRate = (type) => RATES[type];
const occupancy = (type) => OCCUPANCY[type];

function selectRoom(roomId, card) {
  document.querySelectorAll(".room-card").forEach((el) => el.classList.remove("selected"));
  card.classList.add("selected");
  state.selectedRoom = roomId;
  document.getElementById("selectedRoomLabel").textContent = `Room #${roomId} selected for the searched dates.`;
  document.getElementById("bookButton").disabled = false;
}

function openModal(html) {
  document.getElementById("modalBody").innerHTML = html;
  document.getElementById("modal").hidden = false;
}

function closeModal() {
  document.getElementById("modal").hidden = true;
}

async function showInvoice(reservationId) {
  const invoice = await api.get(`/api/reservations/${reservationId}/invoice`);
  openModal(`
    <h3>Invoice · reservation #${invoice.reservationId}</h3>
    <div class="invoice-row"><span>${invoice.guestName}</span><span>Room ${invoice.roomNumber} (${invoice.roomType})</span></div>
    <div class="invoice-row"><span>${invoice.stay.checkIn} → ${invoice.stay.checkOut}</span><span>${invoice.nights} night(s)</span></div>
    <div class="invoice-row"><span>Room charges</span><span>${money(invoice.roomCharges)}</span></div>
    <div class="invoice-row"><span>Discount</span><span>-${money(invoice.discount)}</span></div>
    <div class="invoice-row"><span>Taxes (12%)</span><span>${money(invoice.taxes)}</span></div>
    <div class="invoice-row"><span>Paid</span><span>${money(invoice.paid)}</span></div>
    <div class="invoice-row total"><span>Balance due</span><span>${money(invoice.balanceDue)}</span></div>
    ${invoice.notes.map((note) => `<p style="color:var(--muted);font-size:12px">${note}</p>`).join("")}`);
}

function showPaymentForm(reservationId, due) {
  openModal(`
    <h3>Take payment · reservation #${reservationId}</h3>
    <form id="paymentForm" class="grid">
      <label>Amount<input name="amount" type="number" step="0.01" max="${due}" value="${due}" required /></label>
      <label>Card token<input name="cardToken" value="tok_visa" required /></label>
      <p style="color:var(--muted);font-size:12px;margin:0">
        Sandbox gateway: any token is captured, tokens starting with <code>tok_fail</code> are declined.
      </p>
      <button type="submit">Charge ${money(due)}</button>
    </form>`);

  document.getElementById("paymentForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    guard(async () => {
      const payment = await api.post(`/api/reservations/${reservationId}/payments`, {
        amount: Number(form.get("amount")),
        cardToken: form.get("cardToken"),
      });
      closeModal();
      if (payment.status === "CAPTURED") {
        toast(`Payment captured (${payment.providerReference})`);
      } else {
        toast(`Payment failed: ${payment.failureReason}`, true);
      }
      await refresh();
    });
  });
}

async function refresh() {
  await loadGuests();
  await loadReservations();
  await loadStats();
}

document.getElementById("searchForm").addEventListener("submit", (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  state.stay = { checkIn: form.get("checkIn"), checkOut: form.get("checkOut") };
  guard(async () => {
    const params = new URLSearchParams(state.stay);
    if (form.get("type")) params.set("type", form.get("type"));
    renderAvailableRooms(await api.get(`/api/rooms/available?${params}`));
    await refresh();
  });
});

document.getElementById("availableRooms").addEventListener("click", (event) => {
  const card = event.target.closest(".room-card");
  if (card) selectRoom(Number(card.dataset.room), card);
});

document.getElementById("guestForm").addEventListener("submit", (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  guard(async () => {
    const guest = await api.post("/api/guests", Object.fromEntries(form.entries()));
    event.target.reset();
    toast(`Registered ${guest.fullName} (#${guest.id})`);
    await refresh();
  });
});

document.getElementById("bookingForm").addEventListener("submit", (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  if (!state.selectedRoom || !state.stay) {
    toast("Search dates and select a room first", true);
    return;
  }
  guard(async () => {
    const reservation = await api.post("/api/reservations", {
      guestId: Number(form.get("guestId")),
      roomId: state.selectedRoom,
      checkIn: state.stay.checkIn,
      checkOut: state.stay.checkOut,
      guests: Number(form.get("guests")),
    });
    toast(`Reservation #${reservation.id} confirmed · ${money(reservation.totalAmount)}`);
    await refresh();
  });
});

document.querySelector("#reservationTable tbody").addEventListener("click", (event) => {
  const button = event.target.closest("button");
  if (!button) return;
  const { pay, due, checkin, checkout, cancel, invoice } = button.dataset;
  if (pay) showPaymentForm(pay, Number(due));
  if (invoice) guard(() => showInvoice(invoice));
  if (checkin) guard(async () => {
    await api.post(`/api/reservations/${checkin}/check-in`);
    toast(`Reservation #${checkin} checked in`);
    await refresh();
  });
  if (checkout) guard(async () => {
    await api.post(`/api/reservations/${checkout}/check-out`);
    toast(`Reservation #${checkout} checked out`);
    await refresh();
  });
  if (cancel) guard(async () => {
    await api.post(`/api/reservations/${cancel}/cancel`);
    toast(`Reservation #${cancel} cancelled`);
    await refresh();
  });
});

document.getElementById("modalClose").addEventListener("click", closeModal);
document.getElementById("modal").addEventListener("click", (event) => {
  if (event.target.id === "modal") closeModal();
});

const today = new Date();
const tomorrow = new Date(today.getTime() + 86400000);
document.querySelector("input[name=checkIn]").value = today.toISOString().slice(0, 10);
document.querySelector("input[name=checkOut]").value = tomorrow.toISOString().slice(0, 10);

document.getElementById("logoutButton").addEventListener("click", () => guard(async () => {
  await api.post("/api/auth/logout");
  window.location.replace("/login.html");
}));

guard(async () => {
  await loadSession();
  await loadHealth();
  await refresh();
});
