/**
 * Lịch Âm Việt Nam - Chuyển đổi Dương lịch sang Âm lịch
 * Dựa trên thuật toán của Hồ Ngọc Đức (https://www.informatik.uni-leipzig.de/~duc/amlich/)
 * Hỗ trợ chính xác từ 1900-2100
 */
const LunarCalendar = (() => {
    const PI = Math.PI;

    function jdFromDate(dd, mm, yy) {
        const a = Math.floor((14 - mm) / 12);
        const y = yy + 4800 - a;
        const m = mm + 12 * a - 3;
        let jd = dd + Math.floor((153 * m + 2) / 5) + 365 * y + Math.floor(y / 4) - Math.floor(y / 100) + Math.floor(y / 400) - 32045;
        if (jd < 2299161) {
            jd = dd + Math.floor((153 * m + 2) / 5) + 365 * y + Math.floor(y / 4) - 32083;
        }
        return jd;
    }

    function jdToDate(jd) {
        let a, b, c, d, e, m;
        if (jd > 2299160) {
            a = jd + 32044;
            b = Math.floor((4 * a + 3) / 146097);
            c = a - Math.floor(146097 * b / 4);
        } else {
            b = 0;
            c = jd + 32082;
        }
        d = Math.floor((4 * c + 3) / 1461);
        e = c - Math.floor(1461 * d / 4);
        m = Math.floor((5 * e + 2) / 153);
        const day = e - Math.floor((153 * m + 2) / 5) + 1;
        const month = m + 3 - 12 * Math.floor(m / 10);
        const year = b * 100 + d - 4800 + Math.floor(m / 10);
        return [day, month, year];
    }

    function newMoon(k) {
        const T = k / 1236.85;
        const T2 = T * T;
        const T3 = T2 * T;
        const dr = PI / 180;
        let Jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3;
        Jd1 += 0.00033 * Math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr);
        const M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3;
        const Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3;
        const F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3;
        let C1 = (0.1734 - 0.000393 * T) * Math.sin(M * dr) + 0.0021 * Math.sin(2 * dr * M);
        C1 = C1 - 0.4068 * Math.sin(Mpr * dr) + 0.0161 * Math.sin(dr * 2 * Mpr);
        C1 -= 0.0004 * Math.sin(dr * 3 * Mpr);
        C1 += 0.0104 * Math.sin(dr * 2 * F) - 0.0051 * Math.sin(dr * (M + Mpr));
        C1 -= 0.0074 * Math.sin(dr * (M - Mpr)) + 0.0004 * Math.sin(dr * (2 * F + M));
        C1 -= 0.0004 * Math.sin(dr * (2 * F - M)) - 0.0006 * Math.sin(dr * (2 * F + Mpr));
        C1 += 0.0010 * Math.sin(dr * (2 * F - Mpr)) + 0.0005 * Math.sin(dr * (2 * Mpr + M));
        let deltat;
        if (T < -11) {
            deltat = 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3;
        } else {
            deltat = -0.000278 + 0.000265 * T + 0.000262 * T2;
        }
        return Jd1 + C1 - deltat;
    }

    function sunLongitude(jdn) {
        const T = (jdn - 2451545.0) / 36525;
        const T2 = T * T;
        const dr = PI / 180;
        const M = 357.5291 + 35999.0503 * T - 0.0001559 * T2 - 0.00000048 * T * T2;
        const L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2;
        let DL = (1.9146 - 0.004817 * T - 0.000014 * T2) * Math.sin(dr * M);
        DL += (0.019993 - 0.000101 * T) * Math.sin(dr * 2 * M) + 0.00029 * Math.sin(dr * 3 * M);
        let L = L0 + DL;
        L = L * dr;
        L = L - PI * 2 * Math.floor(L / (PI * 2));
        return L;
    }

    function getSunLongitude(dayNumber, timeZone) {
        return Math.floor(sunLongitude(dayNumber - 0.5 - timeZone / 24) / PI * 6);
    }

    function getNewMoonDay(k, timeZone) {
        return Math.floor(newMoon(k) + 0.5 + timeZone / 24);
    }

    function getLunarMonth11(yy, timeZone) {
        const off = jdFromDate(31, 12, yy) - 2415021;
        const k = Math.floor(off / 29.530588853);
        let nm = getNewMoonDay(k, timeZone);
        const sunLong = getSunLongitude(nm, timeZone);
        if (sunLong >= 9) {
            nm = getNewMoonDay(k - 1, timeZone);
        }
        return nm;
    }

    function getLeapMonthOffset(a11, timeZone) {
        const k = Math.floor((a11 - 2415021.076998695) / 29.530588853 + 0.5);
        let last = 0;
        let i = 1;
        let arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone);
        do {
            last = arc;
            i++;
            arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone);
        } while (arc !== last && i < 14);
        return i - 1;
    }

    /**
     * Chuyển đổi ngày Dương lịch sang Âm lịch
     * @param {number} dd - ngày
     * @param {number} mm - tháng (1-12)
     * @param {number} yy - năm
     * @param {number} timeZone - múi giờ (7 cho Việt Nam)
     * @returns {[number, number, number, boolean]} [lunarDay, lunarMonth, lunarYear, isLeapMonth]
     */
    function convertSolar2Lunar(dd, mm, yy, timeZone = 7) {
        const dayNumber = jdFromDate(dd, mm, yy);
        const k = Math.floor((dayNumber - 2415021.076998695) / 29.530588853);
        let monthStart = getNewMoonDay(k + 1, timeZone);
        if (monthStart > dayNumber) {
            monthStart = getNewMoonDay(k, timeZone);
        }
        let a11 = getLunarMonth11(yy, timeZone);
        let b11 = a11;
        let lunarYear;
        if (a11 >= monthStart) {
            lunarYear = yy;
            a11 = getLunarMonth11(yy - 1, timeZone);
        } else {
            lunarYear = yy + 1;
            b11 = getLunarMonth11(yy + 1, timeZone);
        }
        const lunarDay = dayNumber - monthStart + 1;
        const diff = Math.floor((monthStart - a11) / 29);
        let lunarLeap = false;
        let lunarMonth = diff + 11;
        if (b11 - a11 > 365) {
            const leapMonthDiff = getLeapMonthOffset(a11, timeZone);
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10;
                if (diff === leapMonthDiff) {
                    lunarLeap = true;
                }
            }
        }
        if (lunarMonth > 12) {
            lunarMonth -= 12;
        }
        if (lunarMonth >= 11 && diff < 4) {
            lunarYear -= 1;
        }
        return [lunarDay, lunarMonth, lunarYear, lunarLeap];
    }

    /**
     * Lấy thông tin âm lịch cho một ngày dương lịch
     * @param {Date} date - đối tượng Date
     * @returns {{ day: number, month: number, year: number, isLeap: boolean, dayText: string }}
     */
    function getLunarDate(date) {
        const [day, month, year, isLeap] = convertSolar2Lunar(
            date.getDate(), date.getMonth() + 1, date.getFullYear(), 7
        );
        // Hiển thị ngắn gọn: ngày âm, nếu là mùng 1 thì hiện cả tháng
        let dayText;
        if (day === 1) {
            dayText = `${day}/${month}`;
        } else {
            dayText = `${day}`;
        }
        return { day, month, year, isLeap, dayText };
    }

    return { getLunarDate, convertSolar2Lunar };
})();
