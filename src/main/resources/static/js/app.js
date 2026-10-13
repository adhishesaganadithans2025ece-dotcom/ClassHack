/**
 * ClassHack - Core Application JavaScript Utilities
 */

const ClassHack = {
    // API Request Wrapper
    async api(url, options = {}) {
        const defaultHeaders = {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        };

        const config = {
            ...options,
            headers: {
                ...defaultHeaders,
                ...options.headers
            }
        };

        try {
            const response = await fetch(url, config);

            // Handle 401 Unauthorized
            if (response.status === 401) {
                if (!window.location.pathname.endsWith('login.html') && !window.location.pathname.endsWith('index.html') && window.location.pathname !== '/') {
                    window.location.href = '/login.html?expired=true';
                }
                throw new Error("Session expired or unauthorized. Please login.");
            }

            // Handle 403 Forbidden
            if (response.status === 403) {
                if (!window.location.pathname.endsWith('403.html')) {
                    window.location.href = '/403.html';
                }
                throw new Error("Access Denied: You do not have permission for this action.");
            }

            // Parse response
            const contentType = response.headers.get('content-type');
            let data = null;
            if (contentType && contentType.includes('application/json')) {
                data = await response.json();
            } else {
                data = await response.text();
            }

            if (!response.ok) {
                let errorMsg = "An error occurred";
                if (data && typeof data === 'object') {
                    if (data.fieldErrors) {
                        errorMsg = Object.values(data.fieldErrors).join("\n");
                    } else if (data.message) {
                        errorMsg = data.message;
                    }
                } else if (typeof data === 'string' && data.length < 200) {
                    errorMsg = data;
                }
                const err = new Error(errorMsg);
                err.status = response.status;
                err.data = data;
                throw err;
            }

            return data;
        } catch (error) {
            console.error("API Error on " + url, error);
            throw error;
        }
    },

    // Toast Notifications
    toast(message, type = 'info') {
        let container = document.getElementById('toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toast-container';
            container.className = 'toast-container position-fixed top-0 end-0 p-3';
            container.style.zIndex = '1090';
            document.body.appendChild(container);
        }

        const bgClass = type === 'success' ? 'bg-success text-white' :
                        type === 'danger' ? 'bg-danger text-white' :
                        type === 'warning' ? 'bg-warning text-dark' : 'bg-primary text-white';

        const iconClass = type === 'success' ? 'bi-check-circle-fill' :
                          type === 'danger' ? 'bi-exclamation-triangle-fill' :
                          type === 'warning' ? 'bi-exclamation-circle-fill' : 'bi-info-circle-fill';

        const toastId = 'toast-' + Date.now();
        const toastEl = document.createElement('div');
        toastEl.className = `toast align-items-center ${bgClass} border-0 shadow-lg mb-2`;
        toastEl.id = toastId;
        toastEl.setAttribute('role', 'alert');
        toastEl.setAttribute('aria-live', 'assertive');
        toastEl.setAttribute('aria-atomic', 'true');

        toastEl.innerHTML = `
            <div class="d-flex">
                <div class="toast-body d-flex align-items-center gap-2">
                    <i class="bi ${iconClass} fs-5"></i>
                    <span>${ClassHack.escapeHtml(message)}</span>
                </div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
            </div>
        `;

        container.appendChild(toastEl);
        const bsToast = new bootstrap.Toast(toastEl, { delay: 4500 });
        bsToast.show();

        toastEl.addEventListener('hidden.bs.toast', () => {
            toastEl.remove();
        });
    },

    // Check Authentication & Role
    async checkAuth(expectedRole = null) {
        try {
            const user = await ClassHack.api('/api/auth/me');
            if (!user || !user.authenticated) {
                window.location.href = '/login.html';
                return null;
            }

            if (expectedRole && user.role !== expectedRole) {
                window.location.href = '/403.html';
                return null;
            }

            return user;
        } catch (e) {
            window.location.href = '/login.html';
            return null;
        }
    },

    // Logout
    async logout() {
        try {
            await fetch('/api/auth/logout', { method: 'POST' });
        } finally {
            window.location.href = '/login.html?logged_out=true';
        }
    },

    // Helpers
    formatDate(dateString) {
        if (!dateString) return 'N/A';
        const d = new Date(dateString);
        return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    },

    formatCurrency(amount) {
        if (amount == null) return '₹0';
        return '₹' + Number(amount).toLocaleString('en-IN');
    },

    escapeHtml(text) {
        if (text == null) return '';
        return String(text)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    },

    getStatusBadge(status) {
        if (!status) return '';
        const normalized = status.toUpperCase();
        let label = normalized.replace('_', ' ');
        let className = 'badge-upcoming';

        switch (normalized) {
            case 'REGISTRATION_OPEN':
                className = 'badge-registration_open';
                label = 'Registration Open';
                break;
            case 'REGISTRATION_CLOSED':
                className = 'badge-registration_closed';
                label = 'Registration Closed';
                break;
            case 'UPCOMING':
                className = 'badge-upcoming';
                label = 'Upcoming';
                break;
            case 'COMPLETED':
                className = 'badge-completed';
                label = 'Completed';
                break;
            case 'REGISTERED':
                className = 'badge-registered';
                label = 'Registered';
                break;
            case 'SHORTLISTED':
                className = 'badge-shortlisted';
                label = '⭐ Shortlisted';
                break;
            case 'NEXT_ROUND':
                className = 'badge-next_round';
                label = '🚀 Next Round';
                break;
            case 'FINALIST':
                className = 'badge-finalist';
                label = '🥈 Finalist';
                break;
            case 'WINNER':
                className = 'badge-winner';
                label = '🏆 Winner';
                break;
            case 'ELIMINATED':
                className = 'badge-eliminated';
                label = 'Eliminated';
                break;
            case 'WITHDRAWN':
                className = 'badge-withdrawn';
                label = 'Withdrawn';
                break;
        }

        return `<span class="badge-status ${className}">${label}</span>`;
    },

    // Reusable Show/Hide Password Visibility Toggle
    togglePasswordVisibility(inputId, btn) {
        const input = document.getElementById(inputId);
        if (!input) return;
        const icon = btn ? btn.querySelector('i') : null;
        if (input.type === 'password') {
            input.type = 'text';
            if (icon) {
                icon.classList.remove('bi-eye');
                icon.classList.add('bi-eye-slash');
            }
            if (btn) {
                btn.setAttribute('aria-label', 'Hide password');
                btn.setAttribute('title', 'Hide password');
            }
        } else {
            input.type = 'password';
            if (icon) {
                icon.classList.remove('bi-eye-slash');
                icon.classList.add('bi-eye');
            }
            if (btn) {
                btn.setAttribute('aria-label', 'Show password');
                btn.setAttribute('title', 'Show password');
            }
        }
    }
};

// Global helper so onclick="togglePasswordVisibility('id', this)" works seamlessly everywhere
window.togglePasswordVisibility = function(inputId, btn) {
    ClassHack.togglePasswordVisibility(inputId, btn);
};

