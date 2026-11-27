(() => {
    "use strict";

    const THEME_KEY = "cv-theme";

    function applyTheme(theme) {
        const resolved = theme === "dark" ? "dark" : "light";
        document.documentElement.setAttribute("data-bs-theme", resolved);
        document.querySelectorAll("[data-theme-toggle]").forEach((btn) => {
            btn.setAttribute("aria-pressed", String(resolved === "dark"));
        });
    }

    function initThemeToggle() {
        const saved = localStorage.getItem(THEME_KEY);
        if (saved === "dark" || saved === "light") {
            applyTheme(saved);
        }

        document.addEventListener("click", (event) => {
            const toggle = event.target.closest("[data-theme-toggle]");
            if (!toggle) {
                return;
            }
            const current = document.documentElement.getAttribute("data-bs-theme") === "dark" ? "dark" : "light";
            const next = current === "dark" ? "light" : "dark";
            localStorage.setItem(THEME_KEY, next);
            applyTheme(next);
        });
    }

    function initConfirmModal() {
        document.addEventListener("click", (event) => {
            const trigger = event.target.closest("[data-confirm]");
            if (!trigger) {
                return;
            }

            const message = trigger.getAttribute("data-confirm") || "Are you sure?";
            const modalEl = document.getElementById("confirm-modal");

            if (!modalEl || typeof bootstrap === "undefined" || !bootstrap.Modal) {
                if (!window.confirm(message)) {
                    event.preventDefault();
                }
                return;
            }

            event.preventDefault();

            const msgEl = document.getElementById("confirm-modal-message");
            if (msgEl) {
                msgEl.textContent = message;
            }

            const confirmBtn = document.getElementById("confirm-modal-submit");
            const sourceForm = trigger.closest("form");
            const actionUrl = trigger.getAttribute("data-confirm-action");

            if (confirmBtn) {
                const freshBtn = confirmBtn.cloneNode(true);
                confirmBtn.parentNode.replaceChild(freshBtn, confirmBtn);
                freshBtn.addEventListener("click", () => {
                    bootstrap.Modal.getOrCreateInstance(modalEl).hide();
                    if (sourceForm) {
                        sourceForm.submit();
                    } else if (actionUrl) {
                        window.location.assign(actionUrl);
                    }
                });
            }

            bootstrap.Modal.getOrCreateInstance(modalEl).show();
        });
    }

    function initPreviewModal() {
        const modalEl = document.getElementById("preview-modal");
        if (modalEl) {
            modalEl.addEventListener("hidden.bs.modal", () => {
                const bodyEl = document.getElementById("preview-modal-body");
                if (bodyEl) {
                    bodyEl.innerHTML = "";
                }
            });
        }

        document.addEventListener("click", (event) => {
            const trigger = event.target.closest("[data-preview-url]");
            if (!trigger) {
                return;
            }

            const url = trigger.getAttribute("data-preview-url");
            if (!url) {
                return;
            }

            event.preventDefault();

            const title = trigger.getAttribute("data-preview-title") || "Document Preview";
            const contentType = (trigger.getAttribute("data-preview-type") || "").toLowerCase();
            const downloadUrl = trigger.getAttribute("data-download-url") || "";

            const currentModalEl = document.getElementById("preview-modal");
            if (!currentModalEl || typeof bootstrap === "undefined" || !bootstrap.Modal) {
                window.open(url, "_blank", "noopener");
                return;
            }

            const titleEl = document.getElementById("preview-modal-title");
            if (titleEl) {
                titleEl.textContent = title;
            }

            const downloadEl = document.getElementById("preview-modal-download");
            if (downloadEl) {
                if (downloadUrl) {
                    downloadEl.setAttribute("href", downloadUrl);
                    downloadEl.classList.remove("d-none");
                } else {
                    downloadEl.classList.add("d-none");
                }
            }

            const bodyEl = document.getElementById("preview-modal-body");
            if (bodyEl) {
                bodyEl.innerHTML = "";
                const isImage = contentType.startsWith("image/")
                    || /\.(png|jpe?g|webp|gif)(\?.*)?$/i.test(url);

                if (isImage) {
                    const img = document.createElement("img");
                    img.src = url;
                    img.alt = title;
                    img.className = "img-fluid rounded mx-auto d-block cv-preview-image";
                    bodyEl.appendChild(img);
                } else {
                    const frame = document.createElement("iframe");
                    frame.src = url;
                    frame.title = title;
                    frame.className = "cv-preview-frame";
                    bodyEl.appendChild(frame);
                }
            }

            bootstrap.Modal.getOrCreateInstance(currentModalEl).show();
        });
    }

    document.addEventListener("DOMContentLoaded", () => {
        initThemeToggle();
        initConfirmModal();
        initPreviewModal();
    });
})();
