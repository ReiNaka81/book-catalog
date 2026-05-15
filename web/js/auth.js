function hasSampleConfig() {
    const values = Object.values(CONFIG || {}).map((value) => String(value));
    return values.some(
        (value) =>
            value.includes("your-domain") ||
            value.includes("your_cognito_client_id") ||
            value.includes("your-api.example.com")
    );
}

function ensureAuthConfig() {
    if (!hasSampleConfig()) {
        return true;
    }

    alert("認証を利用するには web/js/config.js を実環境の値に更新してください。");
    return false;
}

function redirectToLogin() {
    if (!ensureAuthConfig()) {
        return;
    }

    const url =
        `${CONFIG.COGNITO_DOMAIN}/oauth2/authorize` +
        `?response_type=code` +
        `&client_id=${CONFIG.CLIENT_ID}` +
        `&redirect_uri=${encodeURIComponent(CONFIG.REDIRECT_URI)}` +
        `&scope=openid+email+profile+aws.cognito.signin.user.admin` +
        `&prompt=login`;

    window.location.href = url;
}

async function handleCallback() {
    const params = new URLSearchParams(window.location.search);
    const code = params.get("code");
    if (!code) {
        return;
    }

    if (!ensureAuthConfig()) {
        history.replaceState({}, "", window.location.pathname);
        initApp();
        return;
    }

    const body = new URLSearchParams({
        grant_type: "authorization_code",
        client_id: CONFIG.CLIENT_ID,
        code,
        redirect_uri: CONFIG.REDIRECT_URI,
        scope: "openid email profile aws.cognito.signin.user.admin"
    });

    try {
        const res = await fetch(`${CONFIG.COGNITO_DOMAIN}/oauth2/token`, {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body
        });

        const token = await res.json().catch(() => ({}));
        if (!res.ok || !token.id_token || !token.access_token) {
            throw new Error(token.error_description || token.error || "認証トークンの取得に失敗しました");
        }

        localStorage.setItem("id_token", token.id_token);
        localStorage.setItem("access_token", token.access_token);

        history.replaceState({}, "", window.location.pathname);
        initApp();
    } catch (error) {
        localStorage.removeItem("id_token");
        localStorage.removeItem("access_token");
        history.replaceState({}, "", window.location.pathname);
        alert(error.message || "認証に失敗しました。もう一度お試しください。");
        initApp();
    }
}

function logout() {
    localStorage.clear();

    if (hasSampleConfig()) {
        history.replaceState({}, "", window.location.pathname);
        initApp();
        return;
    }

    const url =
        `${CONFIG.COGNITO_DOMAIN}/logout` +
        `?client_id=${CONFIG.CLIENT_ID}` +
        `&logout_uri=${encodeURIComponent(CONFIG.REDIRECT_URI)}`;

    window.location.href = url;
}
