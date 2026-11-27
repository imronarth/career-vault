(() => {
    "use strict";

    const THEME_KEY = "cv-theme";

    function getModalClass() {
        if (window.tabler && window.tabler.Modal) {
            return window.tabler.Modal;
        }
        if (window.bootstrap && window.bootstrap.Modal) {
            return window.bootstrap.Modal;
        }
        return null;
    }

    function applyTheme(theme) {
        const resolved = theme === "dark" ? "dark" : "light";
        document.documentElement.setAttribute("data-bs-theme", resolved);
        document.querySelectorAll("[data-theme-toggle]").forEach((btn) => {
            btn.setAttribute("aria-pressed", String(resolved === "dark"));
        });
    }

    const savedTheme = localStorage.getItem(THEME_KEY);
    if (savedTheme === "dark" || savedTheme === "light") {
        applyTheme(savedTheme);
    }

    function initThemeToggle() {
        const currentSaved = localStorage.getItem(THEME_KEY);
        if (currentSaved === "dark" || currentSaved === "light") {
            applyTheme(currentSaved);
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

    function submitConfirmForm(sourceForm, trigger) {
        const isSubmitControl = (trigger.tagName === "BUTTON" && (!trigger.type || trigger.type === "submit"))
            || (trigger.tagName === "INPUT" && (trigger.type === "submit" || trigger.type === "image"));

        if (typeof sourceForm.requestSubmit === "function") {
            sourceForm.requestSubmit(isSubmitControl ? trigger : undefined);
        } else {
            sourceForm.submit();
        }
    }

    function initConfirmModal() {
        document.addEventListener("click", (event) => {
            const trigger = event.target.closest("[data-confirm]");
            if (!trigger) {
                return;
            }

            const message = trigger.getAttribute("data-confirm") || "Are you sure?";
            const modalEl = document.getElementById("confirm-modal");
            const ModalClass = getModalClass();

            if (!modalEl || !ModalClass) {
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
            const sourceForm = trigger.form || trigger.closest("form");
            const actionUrl = trigger.getAttribute("data-confirm-action") || trigger.getAttribute("href");

            if (confirmBtn) {
                const freshBtn = confirmBtn.cloneNode(true);
                confirmBtn.parentNode.replaceChild(freshBtn, confirmBtn);
                freshBtn.addEventListener("click", () => {
                    ModalClass.getOrCreateInstance(modalEl).hide();
                    if (sourceForm) {
                        submitConfirmForm(sourceForm, trigger);
                    } else if (actionUrl) {
                        window.location.assign(actionUrl);
                    }
                });
            }

            ModalClass.getOrCreateInstance(modalEl).show();
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
            const ModalClass = getModalClass();
            if (!currentModalEl || !ModalClass) {
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

            ModalClass.getOrCreateInstance(currentModalEl).show();
        });
    }

    document.addEventListener("DOMContentLoaded", () => {
        initThemeToggle();
        initConfirmModal();
        initPreviewModal();
    });
})();
