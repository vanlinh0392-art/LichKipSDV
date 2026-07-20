/**
 * App.js - Lịch Kíp SDV Web
 */
(() => {
    'use strict';

    // ── State ────────────────────────────────────────
    const today = new Date(); today.setHours(0,0,0,0);
    let currentMonth = today.getMonth(); // 0-indexed
    let currentYear = today.getFullYear();

    // ── Preferences (localStorage) ───────────────────
    const STORAGE_KEY = 'lichkip_prefs';
    const defaults = {
        crew: 'A',
        dayColor: '#D97706',
        nightColor: '#6D28D9',
        hoColor: '#EC4899',
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
        applyColors();
        renderDayHeaders();
        renderCrewSelector();
        renderCalendar();
        renderHOStats();
        setupSettings();
        setupNavigation();
    }

    // ── Apply Colors to CSS vars ─────────────────────
    function applyColors() {
        const root = document.documentElement.style;
        root.setProperty('--day-color', prefs.dayColor);
        root.setProperty('--night-color', prefs.nightColor);
        root.setProperty('--ho-border', prefs.hoColor);
        const widthMap = { 1: '1px', 2: '2px', 3: '3px' };
        root.setProperty('--ho-border-width', widthMap[prefs.hoBorderWidth] || '2px');

        // Update legend dots
        document.querySelectorAll('.legend-dot.day').forEach(d => d.style.background = prefs.dayColor);
        document.querySelectorAll('.legend-dot.night').forEach(d => d.style.background = prefs.nightColor);
        document.querySelectorAll('.legend-dot.ho').forEach(d => d.style.borderColor = prefs.hoColor);
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

        // Day Colors
        setupColorPicker('dayColors', 'dayColor');
        // Night Colors
        setupColorPicker('nightColors', 'nightColor');
        // HO Colors
        setupColorPicker('hoColors', 'hoColor');

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

    function setupColorPicker(containerId, prefKey) {
        const container = document.getElementById(containerId);
        container.querySelectorAll('.color-swatch').forEach(sw => {
            sw.classList.toggle('active', sw.dataset.color.toUpperCase() === prefs[prefKey].toUpperCase());
            sw.addEventListener('click', () => {
                prefs[prefKey] = sw.dataset.color;
                savePrefs(prefs);
                container.querySelectorAll('.color-swatch').forEach(s => s.classList.remove('active'));
                sw.classList.add('active');
                applyColors();
                renderCalendar();
            });
        });
    }

    // ── Start ────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', init);
})();
