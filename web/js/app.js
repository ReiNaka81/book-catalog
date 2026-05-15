const params = new URLSearchParams(window.location.search);
const code = params.get("code");
const error = params.get("error");

const appRoot = document.getElementById("app");
const form = document.getElementById("searchForm");
const queryInput = document.getElementById("query");
const modeSelect = document.getElementById("mode");
const resultDiv = document.getElementById("result");
const resultMeta = document.getElementById("resultMeta");
const authStatus = document.getElementById("authStatus");
const authAction = document.getElementById("authAction");
const adminPanel = document.getElementById("adminPanel");
const adminForm = document.getElementById("adminForm");
const favoritesSection = document.getElementById("favoritesSection");
const favoritesLoading = document.getElementById("favoritesLoading");
const favoritesEmpty = document.getElementById("favoritesEmpty");
const favoritesTable = document.getElementById("favoritesTable");
const favoritesTbody = document.getElementById("favoritesTbody");
const refreshFavoritesButton = document.getElementById("refreshFavorites");

const bookIdInput = document.getElementById("bookId");
const titleInput = document.getElementById("titleAdmin");
const authorInput = document.getElementById("authorAdmin");
const publisherInput = document.getElementById("publisherAdmin");
const priceInput = document.getElementById("priceAdmin");
const isbnInput = document.getElementById("isbnAdmin");
const publishedDateInput = document.getElementById("publishedDateAdmin");

const favoritesEmptyDefaultText = favoritesEmpty?.textContent || "お気に入りはまだ登録されていません";
const API_BASE = String(CONFIG.API_BASE || "").replace(/\/$/, "");

let favoriteItems = [];

if (error) {
    alert("認証エラーが発生しました。もう一度お試しください。");
    history.replaceState({}, "", window.location.pathname);
    initApp();
} else if (code) {
    handleCallback();
} else {
    initApp();
}

function initApp() {
    appRoot?.classList.remove("hidden");
    updateAuthUI();
    updateAdminUI();
    loadFavorites();
}

function parseJwt(token) {
    const base64Url = token.split(".")[1];
    if (!base64Url) {
        throw new Error("invalid token");
    }

    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(
        decodeURIComponent(
            atob(base64)
                .split("")
                .map((char) => "%" + ("00" + char.charCodeAt(0).toString(16)).slice(-2))
                .join("")
        )
    );
}

function getTokenIfValid(key) {
    const token = localStorage.getItem(key);
    if (!token) {
        return null;
    }

    try {
        const payload = parseJwt(token);
        if (payload.exp && payload.exp * 1000 < Date.now()) {
            localStorage.removeItem(key);
            return null;
        }
        return token;
    } catch (error) {
        localStorage.removeItem(key);
        return null;
    }
}

function getAuthToken() {
    return getTokenIfValid("id_token");
}

function getUserDisplayName() {
    const token = getAuthToken();
    if (!token) {
        return "";
    }

    try {
        const payload = parseJwt(token);
        return payload.email || payload.name || payload["cognito:username"] || "ユーザー";
    } catch (error) {
        return "ユーザー";
    }
}

function isAdmin() {
    const token = getAuthToken();
    if (!token) {
        return false;
    }

    try {
        const payload = parseJwt(token);
        const groups = payload["cognito:groups"] || [];
        return Array.isArray(groups) && groups.includes("admin");
    } catch (error) {
        return false;
    }
}

function clearAuthState() {
    localStorage.removeItem("id_token");
    localStorage.removeItem("access_token");
}

function handleAuthExpired() {
    clearAuthState();
    updateAuthUI();
    updateAdminUI();
    loadFavorites();
    alert("認証情報が失効しました。再度ログインしてください。");
}

function updateAuthUI() {
    const signedIn = Boolean(getAuthToken());

    if (authStatus) {
        authStatus.textContent = signedIn ? `ログイン中: ${getUserDisplayName()}` : "ゲスト閲覧中";
    }

    if (authAction) {
        authAction.textContent = signedIn ? "ログアウト" : "ログイン";
        authAction.onclick = signedIn ? logout : redirectToLogin;
    }
}

function updateAdminUI() {
    adminPanel?.classList.toggle("hidden", !isAdmin());
}

function requireAuth() {
    const token = getAuthToken();
    if (!token) {
        redirectToLogin();
        return null;
    }

    return token;
}

function renderResultNotice(message, tone = "neutral") {
    const toneClasses = {
        neutral: "border-stone-300 bg-stone-50 text-stone-500",
        error: "border-rose-200 bg-rose-50 text-rose-700",
    };

    const card = document.createElement("div");
    card.className = `rounded-2xl border border-dashed px-5 py-6 text-sm ${toneClasses[tone] || toneClasses.neutral}`;
    card.textContent = message;

    resultDiv.replaceChildren(card);
}

function createActionButton(label, className, handler) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = className;
    button.textContent = label;
    button.addEventListener("click", handler);
    return button;
}

function appendCell(row, text, className = "") {
    const cell = document.createElement("td");
    cell.className = className;
    cell.textContent = text;
    row.appendChild(cell);
}

async function readMessage(res, fallbackMessage) {
    const data = await res.json().catch(() => ({}));
    return data.message || fallbackMessage;
}

async function rerunSearch() {
    if (!form) {
        return;
    }
    form.requestSubmit();
}

form?.addEventListener("submit", async (event) => {
    event.preventDefault();

    const query = queryInput.value.trim();
    const mode = modeSelect.value;

    if (mode !== "all" && !query) {
        alert("タイトルまたは著者名を入力してください。");
        return;
    }

    const url =
        mode === "all"
            ? `${API_BASE}/all`
            : `${API_BASE}/${mode}?q=${encodeURIComponent(query)}`;

    const headers = {};
    const token = getAuthToken();
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    resultMeta.textContent = "検索中...";
    renderResultNotice("検索中です。");

    try {
        const res = await fetch(url, { headers });
        if (res.status === 401) {
            handleAuthExpired();
            return;
        }
        if (!res.ok) {
            throw new Error("search failed");
        }

        const data = await res.json().catch(() => ({ items: [] }));
        const items = Array.isArray(data.items) ? data.items : [];
        renderResults(items);
    } catch (error) {
        resultMeta.textContent = "取得に失敗しました";
        renderResultNotice("検索結果の取得に失敗しました。時間をおいて再試行してください。", "error");
    }
});

refreshFavoritesButton?.addEventListener("click", (event) => {
    event.preventDefault();
    loadFavorites();
});

adminForm?.addEventListener("submit", async (event) => {
    event.preventDefault();

    const token = requireAuth();
    if (!token) {
        return;
    }

    const book = {
        bookId: bookIdInput.value.trim(),
        title: titleInput.value.trim(),
        author: authorInput.value.trim(),
        publisher: publisherInput.value.trim(),
        isbn: isbnInput.value.trim(),
        publishedDate: publishedDateInput.value,
    };

    const priceValue = priceInput.value.trim();
    const priceNumber = Number(priceValue);
    if (!priceValue || Number.isNaN(priceNumber)) {
        alert("価格には数値を入力してください。");
        return;
    }
    if (priceNumber < 0) {
        alert("価格は 0 以上で入力してください。");
        return;
    }

    const requiredFields = [
        { value: book.bookId, label: "Book ID" },
        { value: book.title, label: "タイトル" },
        { value: book.author, label: "著者" },
        { value: book.publisher, label: "出版社" },
        { value: book.isbn, label: "ISBN" },
        { value: book.publishedDate, label: "発売日" },
    ];

    for (const field of requiredFields) {
        if (!field.value) {
            alert(`${field.label} を入力してください。`);
            return;
        }
    }

    const res = await fetch(`${API_BASE}/admin`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ ...book, price: priceNumber }),
    });

    if (res.status === 401) {
        handleAuthExpired();
        return;
    }

    if (res.ok) {
        alert("書籍を追加しました。");
        adminForm.reset();
        await rerunSearch();
        return;
    }

    alert(await readMessage(res, "書籍の追加に失敗しました。"));
});

function renderResults(items) {
    if (!items.length) {
        resultMeta.textContent = "0 件";
        renderResultNotice("該当する書籍は見つかりませんでした。");
        return;
    }

    resultMeta.textContent = `${items.length} 件`;

    const table = document.createElement("table");
    table.className = "min-w-full overflow-hidden rounded-2xl border border-stone-200 text-sm";

    const thead = document.createElement("thead");
    thead.className = "bg-stone-100";
    const headerRow = document.createElement("tr");
    [
        ["タイトル", "px-4 py-3 text-left font-semibold text-stone-700"],
        ["著者", "px-4 py-3 text-left font-semibold text-stone-700"],
        ["操作", "px-4 py-3 text-right font-semibold text-stone-700"],
    ].forEach(([label, className]) => {
        const cell = document.createElement("th");
        cell.className = className;
        cell.textContent = label;
        headerRow.appendChild(cell);
    });
    thead.appendChild(headerRow);

    const tbody = document.createElement("tbody");
    const signedIn = Boolean(getAuthToken());
    const admin = isAdmin();

    items.forEach((book) => {
        const bookId = String(book?.bookId ?? "");
        const row = document.createElement("tr");
        row.className = "border-t border-stone-200 bg-white";

        appendCell(row, String(book?.title ?? ""), "px-4 py-3 align-top text-stone-800");
        appendCell(row, String(book?.author ?? ""), "px-4 py-3 align-top text-stone-600");

        const actionCell = document.createElement("td");
        actionCell.className = "px-4 py-3";

        const actions = document.createElement("div");
        actions.className = "flex flex-wrap justify-end gap-2";
        actions.appendChild(
            createActionButton(
                signedIn ? "お気に入り" : "ログインして保存",
                "rounded-full border border-lime-700 px-3 py-1.5 text-xs font-medium text-lime-800 transition hover:bg-lime-50",
                () => addFavorite(bookId)
            )
        );

        if (admin) {
            actions.appendChild(
                createActionButton(
                    "削除",
                    "rounded-full border border-rose-300 px-3 py-1.5 text-xs font-medium text-rose-700 transition hover:bg-rose-50",
                    () => deleteBook(bookId)
                )
            );
        }

        actionCell.appendChild(actions);
        row.appendChild(actionCell);
        tbody.appendChild(row);
    });

    table.appendChild(thead);
    table.appendChild(tbody);
    resultDiv.replaceChildren(table);
}

async function addFavorite(bookId) {
    if (!bookId) {
        return;
    }

    const token = requireAuth();
    if (!token) {
        return;
    }

    const res = await fetch(`${API_BASE}/favorites`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ bookId }),
    });

    if (res.status === 401) {
        handleAuthExpired();
        return;
    }

    if (res.ok) {
        alert("お気に入りに追加しました。");
        loadFavorites();
        return;
    }

    alert(await readMessage(res, "お気に入りの追加に失敗しました。"));
}

async function loadFavorites() {
    if (!favoritesSection) {
        return;
    }

    const token = getAuthToken();
    if (!token) {
        favoritesSection.classList.add("hidden");
        favoriteItems = [];
        return;
    }

    favoritesSection.classList.remove("hidden");
    favoritesLoading?.classList.remove("hidden");
    favoritesEmpty?.classList.add("hidden");
    favoritesTable?.classList.add("hidden");

    try {
        const res = await fetch(`${API_BASE}/favorites`, {
            headers: { Authorization: `Bearer ${token}` },
        });

        if (res.status === 401) {
            handleAuthExpired();
            return;
        }
        if (!res.ok) {
            throw new Error("failed to load favorites");
        }

        const data = await res.json().catch(() => ({ items: [] }));
        favoriteItems = Array.isArray(data.items) ? data.items : [];
        renderFavorites();
    } catch (error) {
        favoriteItems = [];
        favoritesLoading?.classList.add("hidden");
        favoritesTable?.classList.add("hidden");
        if (favoritesEmpty) {
            favoritesEmpty.textContent = "お気に入りの読み込みに失敗しました。";
            favoritesEmpty.classList.remove("hidden");
        }
    }
}

function renderFavorites() {
    favoritesLoading?.classList.add("hidden");

    if (!favoriteItems.length) {
        favoritesTable?.classList.add("hidden");
        if (favoritesEmpty) {
            favoritesEmpty.textContent = favoritesEmptyDefaultText;
            favoritesEmpty.classList.remove("hidden");
        }
        return;
    }

    favoritesEmpty?.classList.add("hidden");
    favoritesTable?.classList.remove("hidden");

    if (!favoritesTbody) {
        return;
    }

    favoritesTbody.replaceChildren();

    favoriteItems.forEach((item) => {
        const bookId = String(item?.bookId ?? "");
        const row = document.createElement("tr");
        row.className = "border-t border-stone-200 bg-white";

        appendCell(row, bookId, "px-3 py-2 text-stone-800");

        const actionCell = document.createElement("td");
        actionCell.className = "px-3 py-2 text-center";
        actionCell.appendChild(
            createActionButton(
                "削除",
                "rounded-full border border-rose-300 px-3 py-1 text-xs font-medium text-rose-700 transition hover:bg-rose-50",
                () => removeFavorite(bookId)
            )
        );
        row.appendChild(actionCell);
        favoritesTbody.appendChild(row);
    });
}

async function removeFavorite(bookId) {
    if (!bookId || !confirm("お気に入りから削除しますか？")) {
        return;
    }

    const token = requireAuth();
    if (!token) {
        return;
    }

    const res = await fetch(`${API_BASE}/favorites/${encodeURIComponent(bookId)}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
    });

    if (res.status === 401) {
        handleAuthExpired();
        return;
    }

    if (res.ok) {
        alert("お気に入りを削除しました。");
        loadFavorites();
        return;
    }

    alert(await readMessage(res, "お気に入りの削除に失敗しました。"));
}

async function deleteBook(bookId) {
    if (!bookId || !confirm("本当に削除しますか？")) {
        return;
    }

    const token = requireAuth();
    if (!token) {
        return;
    }

    const res = await fetch(`${API_BASE}/admin/${encodeURIComponent(bookId)}`, {
        method: "DELETE",
        headers: {
            Authorization: `Bearer ${token}`,
        },
    });

    if (res.status === 401) {
        handleAuthExpired();
        return;
    }

    if (res.ok) {
        alert("書籍を削除しました。");
        await rerunSearch();
        await loadFavorites();
        return;
    }

    alert(await readMessage(res, "書籍の削除に失敗しました。"));
}
