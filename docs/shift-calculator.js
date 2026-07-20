/**
 * ShiftCalculator - Port trực tiếp từ ShiftCalculator.kt
 * Logic tính ca kíp SDV cho web
 */
const ShiftCalculator = (() => {
    const ShiftType = { NGAY: 'NGAY', DEM: 'DEM', NGHI: 'NGHI' };
    const SHIFT_LABELS = { NGAY: 'Ngày', DEM: 'Đêm', NGHI: 'Nghỉ' };
    const CYCLE = [
        ShiftType.NGAY, ShiftType.NGAY, ShiftType.NGAY, ShiftType.NGAY,
        ShiftType.NGHI, ShiftType.NGHI,
        ShiftType.DEM, ShiftType.DEM, ShiftType.DEM, ShiftType.DEM,
        ShiftType.NGHI, ShiftType.NGHI
    ];
    const CREWS = [
        { id: 'A', name: 'Kíp A', offset: 0 },
        { id: 'B', name: 'Kíp B', offset: 8 },
        { id: 'C', name: 'Kíp C', offset: 4 },
        { id: 'HC', name: 'Hành Chính', offset: -1 }
    ];

    // Anchor: Jan 3, 2026
    const ANCHOR = new Date(2026, 0, 3);

    function toJDN(date) {
        const y = date.getFullYear(), m = date.getMonth() + 1, d = date.getDate();
        const a = Math.floor((14 - m) / 12);
        const yy = y + 4800 - a;
        const mm = m + 12 * a - 3;
        return d + Math.floor((153 * mm + 2) / 5) + 365 * yy + Math.floor(yy / 4) - Math.floor(yy / 100) + Math.floor(yy / 400) - 32045;
    }

    function mod(a, b) { return ((a % b) + b) % b; }

    function dateEqual(a, b) {
        return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
    }

    function addDays(date, n) {
        const d = new Date(date);
        d.setDate(d.getDate() + n);
        return d;
    }

    function dayOfWeek(date) {
        const d = date.getDay();
        return d === 0 ? 7 : d; // 1=Mon..7=Sun
    }

    function isSaturdayHO(date) {
        if (dayOfWeek(date) !== 6) return false;
        const y = date.getFullYear(), m = date.getMonth() + 1, d = date.getDate();
        if (y > 2026) return false;
        if (y === 2026) {
            return (m===1&&(d===3||d===17))||(m===2&&d===14)||(m===3&&(d===14||d===28))||
                   (m===4&&(d===11||d===25))||(m===5&&(d===9||d===23))||(m===6&&(d===6||d===20))||
                   (m===7&&d===4)||(m===8&&(d===15||d===29))||(m===9&&(d===12||d===26))||
                   (m===10&&d===24)||(m===11&&d===7)||(m===12&&d===5);
        }
        const nth = Math.floor((d - 1) / 7) + 1;
        return nth === 1 || nth === 3;
    }

    function isHoliday(date) {
        const y = date.getFullYear(), m = date.getMonth() + 1, d = date.getDate();
        if (m===1&&d===1) return true;
        if (m===4&&d===30) return true;
        if (m===5&&d===1) return true;
        if (m===9&&(d===1||d===2)) return true;
        if (m===11&&d===24) return true;
        if (m===12&&d===31) return true;
        switch (y) {
            case 2025: return (m===1&&d>=28&&d<=31)||(m===2&&d===1)||(m===4&&d===7);
            case 2026: return (m===2&&d>=16&&d<=20)||(m===4&&d===26);
            case 2027: return (m===2&&d>=5&&d<=9)||(m===4&&d===16);
            case 2028: return (m===1&&d>=25&&d<=29)||(m===4&&d===4);
            case 2029: return (m===2&&d>=12&&d<=16)||(m===4&&d===23);
            default: return false;
        }
    }

    function isLunarNewYear(date) {
        const y = date.getFullYear(), m = date.getMonth() + 1, d = date.getDate();
        switch (y) {
            case 2025: return (m===1&&d>=28&&d<=31)||(m===2&&d===1);
            case 2026: return m===2&&d>=16&&d<=20;
            case 2027: return m===2&&d>=5&&d<=9;
            case 2028: return m===1&&d>=25&&d<=29;
            case 2029: return m===2&&d>=12&&d<=16;
            default: return false;
        }
    }

    function getHolidayName(date) {
        const y = date.getFullYear(), m = date.getMonth() + 1, d = date.getDate();
        if (m===1&&d===1) return 'Tết Dương lịch';
        if (m===4&&d===30) return 'Giải phóng miền Nam';
        if (m===5&&d===1) return 'Quốc tế Lao động';
        if (m===9&&(d===1||d===2)) return 'Quốc khánh';
        if (m===11&&d===24) return 'Kỷ niệm ngày thành lập';
        if (m===12&&d===31) return 'SDV Day';
        if (y===2025&&((m===1&&d>=28)||(m===2&&d===1))) return 'Tết Nguyên Đán';
        if (y===2025&&m===4&&d===7) return 'Giỗ Tổ Hùng Vương';
        if (y===2026&&m===2&&d>=16&&d<=20) return 'Tết Nguyên Đán';
        if (y===2026&&m===4&&d===26) return 'Giỗ Tổ Hùng Vương';
        if (y===2027&&m===2&&d>=5&&d<=9) return 'Tết Nguyên Đán';
        if (y===2027&&m===4&&d===16) return 'Giỗ Tổ Hùng Vương';
        if (y===2028&&m===1&&d>=25&&d<=29) return 'Tết Nguyên Đán';
        if (y===2028&&m===4&&d===4) return 'Giỗ Tổ Hùng Vương';
        if (y===2029&&m===2&&d>=12&&d<=16) return 'Tết Nguyên Đán';
        if (y===2029&&m===4&&d===23) return 'Giỗ Tổ Hùng Vương';
        if (isHoliday(date)) return 'Ngày lễ';
        return null;
    }

    function countHolidaysBetween(anchor, target) {
        let count = 0;
        if (target > anchor) {
            let curr = addDays(anchor, 1);
            while (curr < target) {
                if (isHoliday(curr)) count++;
                curr = addDays(curr, 1);
            }
        } else if (target < anchor) {
            let curr = addDays(target, 1);
            while (curr <= anchor) {
                if (isHoliday(curr)) count--;
                curr = addDays(curr, 1);
            }
        }
        return count;
    }

    function getShift(crewId, date) {
        if (crewId === 'HC') {
            return dayOfWeek(date) <= 5 ? ShiftType.NGAY : ShiftType.NGHI;
        }
        const crew = CREWS.find(c => c.id === crewId);
        if (!crew) return ShiftType.NGHI;
        const diff = toJDN(date) - toJDN(ANCHOR);
        const holidayAdj = countHolidaysBetween(ANCHOR, date);
        const idx = mod(crew.offset + diff - holidayAdj, CYCLE.length);
        return CYCLE[idx];
    }

    // Bảng ca trực lễ năm 2026 thực tế
    const REAL_2026_HOLIDAYS = {
        '1-1': { A: ShiftType.DEM, B: ShiftType.NGHI, C: ShiftType.NGAY },
        '4-26': { A: ShiftType.NGAY, B: ShiftType.NGHI, C: ShiftType.DEM },
        '4-30': { A: ShiftType.DEM, B: ShiftType.NGAY, C: ShiftType.NGHI },
        '5-1': { A: ShiftType.NGHI, B: ShiftType.NGAY, C: ShiftType.DEM },
        '9-1': { A: ShiftType.NGAY, B: ShiftType.DEM, C: ShiftType.NGHI },
        '9-2': { A: ShiftType.NGAY, B: ShiftType.NGHI, C: ShiftType.DEM },
        '11-24': { A: ShiftType.NGHI, B: ShiftType.NGAY, C: ShiftType.DEM },
        '12-31': { A: ShiftType.NGAY, B: ShiftType.DEM, C: ShiftType.NGHI }
    };

    function getActualShift(crewId, date) {
        if (crewId === 'HC') {
            return (isHoliday(date) || dayOfWeek(date) > 5) ? ShiftType.NGHI : ShiftType.NGAY;
        }
        if (isLunarNewYear(date)) return ShiftType.NGHI;
        if (isHoliday(date)) {
            const y = date.getFullYear(), m = date.getMonth() + 1, d = date.getDate();
            if (y === 2026) {
                const key = `${m}-${d}`;
                const map = REAL_2026_HOLIDAYS[key];
                if (map) return map[crewId] || ShiftType.NGHI;
            }
            const prevDay = addDays(date, -1);
            const nextDay = addDays(date, 1);
            const isPrevHol = isHoliday(prevDay) && !isLunarNewYear(prevDay);
            const isNextHol = isHoliday(nextDay) && !isLunarNewYear(nextDay);
            const baseShifts = { A: getShift('A', date), B: getShift('B', date), C: getShift('C', date) };
            const entries = Object.entries(baseShifts);
            const dayCrew = entries.find(e => e[1] === ShiftType.NGAY)?.[0];
            const nightCrew = entries.find(e => e[1] === ShiftType.DEM)?.[0];
            const offCrew = entries.find(e => e[1] === ShiftType.NGHI)?.[0];

            if (isNextHol) {
                if (crewId === offCrew) return ShiftType.NGAY;
                if (crewId === dayCrew) return ShiftType.DEM;
                if (crewId === nightCrew) return ShiftType.NGHI;
            } else if (isPrevHol) {
                const bsPrev = { A: getShift('A', prevDay), B: getShift('B', prevDay), C: getShift('C', prevDay) };
                const eP = Object.entries(bsPrev);
                const dc = eP.find(e => e[1] === ShiftType.NGAY)?.[0];
                const nc = eP.find(e => e[1] === ShiftType.DEM)?.[0];
                const oc = eP.find(e => e[1] === ShiftType.NGHI)?.[0];
                if (crewId === oc) return ShiftType.NGAY;
                if (crewId === dc) return ShiftType.NGHI;
                if (crewId === nc) return ShiftType.DEM;
            } else {
                if (crewId === dayCrew) return ShiftType.NGAY;
                if (crewId === nightCrew) return ShiftType.NGHI;
                if (crewId === offCrew) return ShiftType.DEM;
            }
            return ShiftType.NGHI;
        }
        return getShift(crewId, date);
    }

    function getShiftInfo(crewId, date) {
        const actualShift = getActualShift(crewId, date);
        const isOfficialHol = isHoliday(date);
        const holName = getHolidayName(date);
        const isSundayWork = dayOfWeek(date) === 7 && actualShift !== ShiftType.NGHI;
        const isSaturdayWork = isSaturdayHO(date) && actualShift !== ShiftType.NGHI;
        const isHO = isOfficialHol || isSundayWork || isSaturdayWork;
        let finalHolName = null;
        if (isOfficialHol) finalHolName = holName;
        else if (isSundayWork) finalHolName = 'Chủ Nhật đi làm (HO)';
        else if (isSaturdayWork) finalHolName = 'Thứ Bảy đi làm (HO)';
        return { type: actualShift, isHoliday: isHO, holidayName: finalHolName, isOfficialHoliday: isOfficialHol };
    }

    function getHOStats(crewId, year) {
        if (crewId === 'HC') return { total: 0, remaining: 0 };
        let total = 0, remaining = 0;
        const today = new Date(); today.setHours(0,0,0,0);
        let curr = new Date(year, 0, 1);
        const end = new Date(year, 11, 31);
        while (curr <= end) {
            const actualShift = getActualShift(crewId, curr);
            const isSunday = dayOfWeek(curr) === 7;
            const isSatWork = isSaturdayHO(curr);
            if ((isSunday || isSatWork) && actualShift !== ShiftType.NGHI) {
                total++;
                if (curr >= today) remaining++;
            }
            curr = addDays(curr, 1);
        }
        return { total, remaining };
    }

    return { ShiftType, SHIFT_LABELS, CREWS, getShiftInfo, getHOStats, isHoliday };
})();
