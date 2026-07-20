/**
 * App.js - Lịch Kíp SDV Web
 */
(() => {
    'use strict';

    // ── State ────────────────────────────────────────
    const today = new Date(); today.setHours(0,0,0,0);
    let currentMonth = today.getMonth(); // 0-indexed
    let currentYear = today.getFullYear();

    // ── Bảng màu tùy chọn ─────────────────────────────
    const LIGHT_COLORS = {
        day: [
            { bg: '#fbbf24', text: '#78350f' },
            { bg: '#f87171', text: '#7f1d1d' },
            { bg: '#34d399', text: '#064e3b' },
            { bg: '#60a5fa', text: '#1e3a8a' },
            { bg: '#a78bfa', text: '#4c1d95' },
            { bg: '#f472b6', text: '#9d174d' },
            { bg: '#fb923c', text: '#7c2d12' },
            { bg: '#2dd4bf', text: '#115e59' }
        ],
        night: [
            { bg: '#a78bfa', text: '#4c1d95' },
            { bg: '#818cf8', text: '#312e81' },
            { bg: '#38bdf8', text: '#0369a1' },
            { bg: '#34d399', text: '#064e3b' },
            { bg: '#94a3b8', text: '#1e293b' },
            { bg: '#f87171', text: '#7f1d1d' },
            { bg: '#fda4af', text: '#881337' },
            { bg: '#2dd4bf', text: '#115e59' }
        ],
        ho: [
            { bg: '#ec4899', text: '#ffffff' },
            { bg: '#ef4444', text: '#ffffff' },
            { bg: '#f59e0b', text: '#ffffff' },
            { bg: '#10b981', text: '#ffffff' },
            { bg: '#3b82f6', text: '#ffffff' },
            { bg: '#8b5cf6', text: '#ffffff' },
            { bg: '#f97316', text: '#ffffff' },
            { bg: '#14b8a6', text: '#ffffff' }
        ]
    };

    const DARK_COLORS = {
        day: [
            { bg: '#fb923c', text: '#ffffff' },
            { bg: '#f87171', text: '#ffffff' },
            { bg: '#34d399', text: '#ffffff' },
            { bg: '#60a5fa', text: '#ffffff' },
            { bg: '#a78bfa', text: '#ffffff' },
            { bg: '#f472b6', text: '#ffffff' },
            { bg: '#fbbf24', text: '#1f2937' },
            { bg: '#2dd4bf', text: '#ffffff' }
        ],
        night: [
            { bg: '#8b5cf6', text: '#ffffff' },
            { bg: '#6366f1', text: '#ffffff' },
            { bg: '#38bdf8', text: '#ffffff' },
            { bg: '#10b981', text: '#ffffff' },
            { bg: '#64748b', text: '#ffffff' },
            { bg: '#f87171', text: '#ffffff' },
            { bg: '#fb7185', text: '#ffffff' },
            { bg: '#14b8a6', text: '#ffffff' }
        ],
        ho: [
            { bg: '#f472b6', text: '#ffffff' },
            { bg: '#fca5a5', text: '#ffffff' },
            { bg: '#fde047', text: '#ffffff' },
            { bg: '#86efac', text: '#ffffff' },
            { bg: '#93c5fd', text: '#ffffff' },
            { bg: '#c084fc', text: '#ffffff' },
            { bg: '#fdba74', text: '#ffffff' },
            { bg: '#99f6e4', text: '#ffffff' }
        ]
    };

    // ── Preferences (localStorage) ───────────────────
    const STORAGE_KEY = 'lichkip_prefs';
    const defaults = {
        crew: 'A',
        theme: 'light', // Mặc định là ban ngày (Light Mode)
        
        // Màu sắc cho Light Mode (Mặc định đậm đà, rõ ràng)
        dayColorLight: '#fbbf24',
        dayTextLight: '#78350f',
        nightColorLight: '#a78bfa',
        nightTextLight: '#4c1d95',
        hoColorLight: '#ec4899',
        
        // Màu sắc cho Dark Mode (Nhạt, dịu mắt, tránh chói)
        dayColorDark: '#fb923c',
        dayTextDark: '#ffffff',
        nightColorDark: '#8b5cf6',
        nightTextDark: '#ffffff',
        hoColorDark: '#f472b6',
        
        hoBorderWidth: 2,
        mergeMonths: true,
        hideHolidayShift: false
    };

    function loadPrefs() {
        try {
            const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
            return { ...defaults, ...saved };
        } catch { return { ...defaults }; }
    }
    function savePrefs(prefs) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs));
    }

    let prefs = loadPrefs();

    // ── DOM Elements ─────────────────────────────────
    const $ = id => document.getElementById(id);
    const monthTitle = $('monthTitle');
    const calendarGrid = $('calendarGrid');
    const dayHeaders = $('dayHeaders');
    const hoStatsEl = $('hoStats');

    // ── Init ─────────────────────────────────────────
    function init() {
        applyTheme();
        renderDayHeaders();
        renderCrewSelector();
        renderCalendar();
        renderHOStats();
        setupSettings();
        setupNavigation();
        setupThemeToggle();
        setupHeaderScrollBehavior();
    }

    // ── Apply Colors to CSS vars ─────────────────────
    function applyColors() {
        const root = document.documentElement.style;
        const isDark = prefs.theme === 'dark';
        
        const dayColor = isDark ? prefs.dayColorDark : prefs.dayColorLight;
        const dayText = isDark ? prefs.dayTextDark : prefs.dayTextLight;
        const nightColor = isDark ? prefs.nightColorDark : prefs.nightColorLight;
        const nightText = isDark ? prefs.nightTextDark : prefs.nightTextLight;
        const hoColor = isDark ? prefs.hoColorDark : prefs.hoColorLight;

        root.setProperty('--day-color', dayColor);
        root.setProperty('--day-text', dayText);
        root.setProperty('--night-color', nightColor);
        root.setProperty('--night-text', nightText);
        root.setProperty('--ho-border', hoColor);
        
        const widthMap = { 1: '1px', 2: '2px', 3: '3px' };
        root.setProperty('--ho-border-width', widthMap[prefs.hoBorderWidth] || '2px');

        // Update legend dots
        document.querySelectorAll('.legend-dot.day').forEach(d => d.style.background = dayColor);
        document.querySelectorAll('.legend-dot.night').forEach(d => d.style.background = nightColor);
        document.querySelectorAll('.legend-dot.ho').forEach(d => d.style.borderColor = hoColor);
    }

    // ── Apply Theme (Light/Dark) ──────────────────────
    function applyTheme() {
        const isDark = prefs.theme === 'dark';
        document.body.classList.toggle('dark-theme', isDark);
        
        // Render icon
        const toggleBtn = $('btnThemeToggle');
        if (toggleBtn) {
            if (isDark) {
                // Biểu tượng mặt trời (khi click sẽ đổi sang Light)
                toggleBtn.innerHTML = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>`;
            } else {
                // Biểu tượng mặt trăng (khi click sẽ đổi sang Dark)
                toggleBtn.innerHTML = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>`;
            }
        }
        
        applyColors();
        updateColorSwatchesUI();
    }

    function setupThemeToggle() {
        $('btnThemeToggle').addEventListener('click', () => {
            prefs.theme = prefs.theme === 'dark' ? 'light' : 'dark';
            savePrefs(prefs);
            applyTheme();
            renderCalendar();
            renderHOStats();
        });
    }

    // ── Day Headers ──────────────────────────────────
    function renderDayHeaders() {
        dayHeaders.innerHTML = '';
        const headers = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
        headers.forEach((h, i) => {
            const el = document.createElement('div');
            el.className = 'day-header' + (i >= 5 ? ' weekend' : '');
            el.textContent = h;
            dayHeaders.appendChild(el);
        });
    }

    // ── Crew Selector ────────────────────────────────
    function renderCrewSelector() {
        document.querySelectorAll('.crew-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.crew === prefs.crew);
            btn.addEventListener('click', () => {
                prefs.crew = btn.dataset.crew;
                savePrefs(prefs);
                document.querySelectorAll('.crew-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                renderCalendar();
                renderHOStats();
            });
        });
    }

    // ── HO Stats ─────────────────────────────────────
    function renderHOStats() {
        const stats = ShiftCalculator.getHOStats(prefs.crew, currentYear);
        if (prefs.crew === 'HC') {
            hoStatsEl.innerHTML = `<span class="ho-label">Hành Chính</span><span class="ho-value">Thứ 2 - Thứ 6</span>`;
        } else {
            hoStatsEl.innerHTML =
                `<span><span class="ho-label">HO ${currentYear}</span></span>` +
                `<span><span class="ho-value">${stats.remaining}</span> / ${stats.total} ngày còn lại</span>`;
        }
    }

    // ── Month Title ──────────────────────────────────
    const MONTH_NAMES = ['Tháng 1','Tháng 2','Tháng 3','Tháng 4','Tháng 5','Tháng 6',
                         'Tháng 7','Tháng 8','Tháng 9','Tháng 10','Tháng 11','Tháng 12'];

    function updateMonthTitle() {
        if (prefs.mergeMonths) {
            const nm = currentMonth === 11 ? 0 : currentMonth + 1;
            const ny = currentMonth === 11 ? currentYear + 1 : currentYear;
            if (currentYear === ny) {
                monthTitle.textContent = `${MONTH_NAMES[currentMonth]} - ${nm + 1} / ${currentYear}`;
            } else {
                monthTitle.textContent = `${MONTH_NAMES[currentMonth]}/${currentYear} - ${nm + 1}/${ny}`;
            }
        } else {
            monthTitle.textContent = `${MONTH_NAMES[currentMonth]} / ${currentYear}`;
        }
    }

    // ── Calendar Rendering ───────────────────────────
    function renderCalendar() {
        updateMonthTitle();
        calendarGrid.innerHTML = '';

        if (prefs.mergeMonths) {
            // Tháng 1
            const label1 = document.createElement('div');
            label1.className = 'month-sep-label';
            label1.textContent = MONTH_NAMES[currentMonth];
            calendarGrid.appendChild(label1);

            const headers1 = createDayHeadersInGrid();
            calendarGrid.appendChild(headers1);

            const grid1 = document.createElement('div');
            grid1.className = 'month-grid';
            renderMonthInto(grid1, currentYear, currentMonth);
            calendarGrid.appendChild(grid1);

            // Tháng 2
            let nm = currentMonth + 1, ny = currentYear;
            if (nm > 11) { nm = 0; ny++; }

            const label2 = document.createElement('div');
            label2.className = 'month-sep-label';
            label2.textContent = MONTH_NAMES[nm];
            calendarGrid.appendChild(label2);

            const headers2 = createDayHeadersInGrid();
            calendarGrid.appendChild(headers2);

            const grid2 = document.createElement('div');
            grid2.className = 'month-grid';
            renderMonthInto(grid2, ny, nm);
            calendarGrid.appendChild(grid2);

            // Ẩn day-headers chính
            dayHeaders.style.display = 'none';
            calendarGrid.classList.remove('single');
        } else {
            dayHeaders.style.display = '';
            calendarGrid.classList.add('single');
            renderMonthInto(calendarGrid, currentYear, currentMonth);
        }
    }

    function createDayHeadersInGrid() {
        const container = document.createElement('div');
        container.className = 'inline-day-headers';
        const headers = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
        headers.forEach((h, i) => {
            const el = document.createElement('div');
            el.className = 'day-header' + (i >= 5 ? ' weekend' : '');
            el.textContent = h;
            container.appendChild(el);
        });
        return container;
    }

    function renderMonthInto(container, year, month) {
        const firstDay = new Date(year, month, 1);
        const daysInMonth = new Date(year, month + 1, 0).getDate();
        let startOffset = firstDay.getDay() === 0 ? 6 : firstDay.getDay() - 1;

        // Empty cells
        for (let i = 0; i < startOffset; i++) {
            const empty = document.createElement('div');
            empty.className = 'cal-cell empty';
            container.appendChild(empty);
        }

        // Day cells
        for (let day = 1; day <= daysInMonth; day++) {
            const date = new Date(year, month, day);
            const info = ShiftCalculator.getShiftInfo(prefs.crew, date);
            const isToday = date.getTime() === today.getTime();
            const isOfficialHol = info.isOfficialHoliday;

            const cell = document.createElement('div');
            cell.className = 'cal-cell';

            // Shift color class
            if (info.type === 'NGAY') cell.classList.add('shift-day');
            else if (info.type === 'DEM') cell.classList.add('shift-night');
            else cell.classList.add('shift-off');

            if (isToday) cell.classList.add('today');
            if (info.isHoliday && !isOfficialHol && !isToday) cell.classList.add('ho-border');
            if (isOfficialHol) cell.classList.add('official-holiday');

            // HO dot
            if (info.isHoliday && !isOfficialHol) {
                const dot = document.createElement('div');
                dot.className = 'ho-dot';
                cell.appendChild(dot);
            }

            if (info.holidayName) cell.dataset.holiday = info.holidayName;

            // Day number
            const dayNum = document.createElement('div');
            dayNum.className = 'day-num';
            dayNum.textContent = day;
            cell.appendChild(dayNum);

            // Shift label
            if (!prefs.hideHolidayShift || !isOfficialHol) {
                const label = document.createElement('div');
                label.className = 'shift-label';
                label.textContent = ShiftCalculator.SHIFT_LABELS[info.type];
                cell.appendChild(label);
            }

            // Lunar date
            const lunar = LunarCalendar.getLunarDate(date);
            const lunarEl = document.createElement('div');
            lunarEl.className = 'lunar-text' + (lunar.day === 1 ? ' lunar-first' : '');
            lunarEl.textContent = lunar.dayText;
            cell.appendChild(lunarEl);

            container.appendChild(cell);
        }
    }

    // ── Navigation ───────────────────────────────────
    function setupNavigation() {
        $('btnPrev').addEventListener('click', () => {
            currentMonth--;
            if (currentMonth < 0) { currentMonth = 11; currentYear--; }
            renderCalendar();
            renderHOStats();
        });
        $('btnNext').addEventListener('click', () => {
            currentMonth++;
            if (currentMonth > 11) { currentMonth = 0; currentYear++; }
            renderCalendar();
            renderHOStats();
        });
        $('btnToday').addEventListener('click', () => {
            currentMonth = today.getMonth();
            currentYear = today.getFullYear();
            renderCalendar();
            renderHOStats();
        });

        // Swipe support
        let touchStartX = 0;
        const wrapper = document.querySelector('.calendar-wrapper');
        wrapper.addEventListener('touchstart', e => { touchStartX = e.touches[0].clientX; }, { passive: true });
        wrapper.addEventListener('touchend', e => {
            const diff = touchStartX - e.changedTouches[0].clientX;
            if (Math.abs(diff) > 60) {
                if (diff > 0) $('btnNext').click();
                else $('btnPrev').click();
            }
        }, { passive: true });
    }

    // ── Settings ─────────────────────────────────────
    function setupSettings() {
        const overlay = $('settingsOverlay');

        $('btnSettings').addEventListener('click', () => overlay.classList.add('open'));
        $('btnCloseSettings').addEventListener('click', () => overlay.classList.remove('open'));
        overlay.addEventListener('click', e => {
            if (e.target === overlay) overlay.classList.remove('open');
        });

        // Merge Months
        const switchMerge = $('switchMerge');
        switchMerge.checked = prefs.mergeMonths;
        switchMerge.addEventListener('change', () => {
            prefs.mergeMonths = switchMerge.checked;
            savePrefs(prefs);
            renderCalendar();
        });

        // Hide Holiday Shift
        const switchHide = $('switchHideHoliday');
        switchHide.checked = prefs.hideHolidayShift;
        switchHide.addEventListener('change', () => {
            prefs.hideHolidayShift = switchHide.checked;
            savePrefs(prefs);
            renderCalendar();
        });

        // Khởi tạo các Swatches màu
        updateColorSwatchesUI();

        // Border Width
        document.querySelectorAll('#borderWidthPicker .bw-btn').forEach(btn => {
            const w = parseInt(btn.dataset.width);
            btn.classList.toggle('active', w === prefs.hoBorderWidth);
            btn.addEventListener('click', () => {
                prefs.hoBorderWidth = w;
                savePrefs(prefs);
                document.querySelectorAll('#borderWidthPicker .bw-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                applyColors();
                renderCalendar();
            });
        });
    }

    function updateColorSwatchesUI() {
        const isDark = prefs.theme === 'dark';
        const colors = isDark ? DARK_COLORS : LIGHT_COLORS;
        
        // Cập nhật từng loại swatch
        updateSwatches('dayColors', colors.day, isDark ? 'dayColorDark' : 'dayColorLight', isDark ? 'dayTextDark' : 'dayTextLight');
        updateSwatches('nightColors', colors.night, isDark ? 'nightColorDark' : 'nightColorLight', isDark ? 'nightTextDark' : 'nightTextLight');
        updateSwatches('hoColors', colors.ho, isDark ? 'hoColorDark' : 'hoColorLight');
    }

    function updateSwatches(containerId, colorList, prefKeyColor, prefKeyText = null) {
        const container = $(containerId);
        if (!container) return;
        
        container.innerHTML = '';
        const activeColor = prefs[prefKeyColor];
        
        colorList.forEach(c => {
            const sw = document.createElement('div');
            sw.className = 'color-swatch';
            sw.style.background = c.bg;
            sw.dataset.color = c.bg;
            if (c.text) {
                sw.dataset.text = c.text;
            }
            
            const isActive = activeColor.toUpperCase() === c.bg.toUpperCase();
            sw.classList.toggle('active', isActive);
            
            sw.addEventListener('click', () => {
                prefs[prefKeyColor] = c.bg;
                if (prefKeyText && c.text) {
                    prefs[prefKeyText] = c.text;
                }
                savePrefs(prefs);
                
                container.querySelectorAll('.color-swatch').forEach(s => s.classList.remove('active'));
                sw.classList.add('active');
                
                applyColors();
                renderCalendar();
            });
            
            container.appendChild(sw);
        });
    }

    function setupHeaderScrollBehavior() {
        const header = document.querySelector('.app-header');
        if (!header) return;

        let lastScrollY = window.scrollY;
        let touchStartY = 0;
        let isHeaderHidden = false;

        // 1. Dựa trên sự kiện cuộn trang (Scroll) - Mượt mà nhất cho thiết bị
        window.addEventListener('scroll', () => {
            const currentScrollY = window.scrollY;
            
            if (currentScrollY > 20) {
                if (currentScrollY > lastScrollY) {
                    // Cuộn xuống -> Ẩn header
                    if (!isHeaderHidden) {
                        header.classList.add('header-hidden');
                        isHeaderHidden = true;
                    }
                } else if (currentScrollY < lastScrollY - 5) { // Khoảng đệm 5px tránh nhạy quá
                    // Cuộn lên -> Hiện header
                    if (isHeaderHidden) {
                        header.classList.remove('header-hidden');
                        isHeaderHidden = false;
                    }
                }
            } else {
                // Ở sát trên cùng thì luôn hiện header
                if (isHeaderHidden) {
                    header.classList.remove('header-hidden');
                    isHeaderHidden = false;
                }
            }
            lastScrollY = currentScrollY;
        }, { passive: true });

        // 2. Dựa trên vuốt chạm (Touch) - Chỉ xử lý khi trang không cuộn được để tránh xung đột
        window.addEventListener('touchstart', e => {
            touchStartY = e.touches[0].clientY;
        }, { passive: true });

        window.addEventListener('touchmove', e => {
            // Kiểm tra xem trang có thể cuộn thực tế hay không
            const docHeight = document.documentElement.scrollHeight;
            const winHeight = window.innerHeight;
            const isScrollable = docHeight > winHeight + 10;

            // Nếu trang có thể cuộn, hãy để sự kiện scroll xử lý để mượt mà nhất
            if (isScrollable) return;

            const touchY = e.touches[0].clientY;
            const diffY = touchStartY - touchY;

            // Ngưỡng vuốt tối thiểu để trigger
            if (Math.abs(diffY) > 25) {
                if (diffY > 0) {
                    // Vuốt lên -> Ẩn header
                    if (!isHeaderHidden) {
                        header.classList.add('header-hidden');
                        isHeaderHidden = true;
                    }
                } else {
                    // Vuốt xuống -> Hiện header
                    if (isHeaderHidden) {
                        header.classList.remove('header-hidden');
                        isHeaderHidden = false;
                    }
                }
                touchStartY = touchY;
            }
        }, { passive: true });
    }

    // ── Start ────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', init);
})();
