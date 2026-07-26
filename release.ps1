<#
.SYNOPSIS
  All-in-One Release Script cho LichKipSDV
.DESCRIPTION
  - Tự động tăng/cập nhật versionCode & versionName
  - Build APK Release (gradlew.bat assembleRelease)
  - Commit & Push code lên GitHub origin main
  - Đăng phát hành GitHub Release kèm file APK tiếng Việt
#>

param(
    [string]$Note = "",
    [switch]$AutoBump = $false,
    [string]$NewVersionName = ""
)

$ErrorActionPreference = "Stop"
$ProjectDir = $PSScriptRoot
Set-Location $ProjectDir

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "ALL-IN-ONE RELEASE SCRIPT - LICH KIP SDV" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$GradleFile = Join-Path $ProjectDir "app\build.gradle.kts"
$GradleContent = Get-Content $GradleFile -Raw

# 1. Doc versionCode va versionName hien tai
if ($GradleContent -match 'versionCode\s*=\s*(\d+)') {
    $CurrentCode = [int]$Matches[1]
} else {
    Write-Error "Khong tim thay versionCode trong app/build.gradle.kts"
}

if ($GradleContent -match 'versionName\s*=\s*"([^"]+)"') {
    $CurrentName = $Matches[1]
} else {
    Write-Error "Khong tim thay versionName trong app/build.gradle.kts"
}

Write-Host "Phien ban hien tai: v$CurrentName (code: $CurrentCode)" -ForegroundColor Yellow

# Tang phien ban neu co co -AutoBump hoac $NewVersionName duoc chi dinh
$TargetCode = $CurrentCode
$TargetName = $CurrentName

if ($NewVersionName -ne "") {
    $TargetName = $NewVersionName
    $TargetCode = $CurrentCode + 1
} elseif ($AutoBump) {
    $TargetCode = $CurrentCode + 1
    $parts = $CurrentName.Split('.')
    if ($parts.Length -ge 2) {
        $lastIdx = $parts.Length - 1
        $parsedNum = 0
        if ([int]::TryParse($parts[$lastIdx], [ref]$parsedNum)) {
            $parts[$lastIdx] = ($parsedNum + 1).ToString()
            $TargetName = $parts -join '.'
        } else {
            $TargetName = $CurrentName + ".1"
        }
    } else {
        $TargetName = $CurrentName + ".1"
    }
}

if ($TargetCode -ne $CurrentCode -or $TargetName -ne $CurrentName) {
    Write-Host "Cap nhat phien ban moi: v$TargetName (code: $TargetCode)" -ForegroundColor Green
    $GradleContent = $GradleContent -replace 'versionCode\s*=\s*\d+', "versionCode = $TargetCode"
    $GradleContent = $GradleContent -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$TargetName`""
    Set-Content -Path $GradleFile -Value $GradleContent -NoNewline
}

# 2. Build Release APK
Write-Host "`n1/4. Dang Build APK Release..." -ForegroundColor Cyan
& .\gradlew.bat assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Error "Build APK that bai! Da dung lai."
}

$ApkSrc = Join-Path $ProjectDir "app\build\outputs\apk\release\app-release.apk"
$ApkOut = Join-Path $ProjectDir "LichNoti_v${TargetName}.apk"

if (-not (Test-Path $ApkSrc)) {
    Write-Error "Khong tim thay tep APK tai $ApkSrc"
}

Copy-Item -Path $ApkSrc -Destination $ApkOut -Force
Write-Host "Da tao tep APK: $ApkOut" -ForegroundColor Green

# 3. Commit & Push Git
Write-Host "`n2/4. Commit & Push Git..." -ForegroundColor Cyan

if ([string]::IsNullOrWhiteSpace($Note)) {
    $Note = "Cap nhat ung dung phien ban v$TargetName"
}

$CommitMsg = "v${TargetName}: $Note"

git add -A
$gitDiff = git status --porcelain
if ($gitDiff) {
    git commit -m "$CommitMsg"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Git commit that bai."
    }
    Write-Host "Da commit: $CommitMsg" -ForegroundColor Green
} else {
    Write-Host "Khong co thay doi moi de commit." -ForegroundColor Yellow
}

Write-Host "Dang push len origin main..." -ForegroundColor Cyan
git push origin main
if ($LASTEXITCODE -ne 0) {
    Write-Error "Git push that bai."
}
Write-Host "Da push thanh cong len GitHub origin main" -ForegroundColor Green

# 4. Tao GitHub Release
Write-Host "`n3/4. Tao GitHub Release..." -ForegroundColor Cyan
$Tag = "v$TargetName"
$Title = "LichNoti $Tag"

$TempNotes = Join-Path $ProjectDir "release_notes_tmp.md"
$ReleaseBody = "### Cap nhat LichNoti $Tag`n`n- Phien ban: $Tag (Build $TargetCode)`n- Mo ta thay doi: $Note`n- Tep cai dat: LichNoti_$Tag.apk`n`n---`n*Tu dong phat hanh boi All-in-One Release Script.*"

Set-Content -Path $TempNotes -Value $ReleaseBody -Encoding UTF8

$ghExists = Get-Command gh -ErrorAction SilentlyContinue
if ($ghExists) {
    & gh release create $Tag $ApkOut --title $Title --notes-file $TempNotes --clobber
    if ($LASTEXITCODE -eq 0) {
        Write-Host "DA PHAT HANH GITHUB RELEASE $Tag THANH CONG!" -ForegroundColor Green
        Write-Host "Link: https://github.com/vanlinh0392-art/LichKipSDV/releases/tag/$Tag" -ForegroundColor Yellow
    } else {
        Write-Host "Da tai APK de len Release $Tag..." -ForegroundColor Yellow
        & gh release upload $Tag $ApkOut --clobber
    }
} else {
    Write-Host "Chua cai dat GitHub CLI (gh). Vui long tai thu cong APK len GitHub Release." -ForegroundColor Yellow
}

if (Test-Path $TempNotes) { Remove-Item $TempNotes -Force }

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "HOAN THANH TOAN BO QUY TRINH RELEASE v$TargetName" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
