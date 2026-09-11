# ============================================================
#  test-mechanisms.ps1
#  Automatska provjera kontrolnih mehanizama kviz aplikacije.
#
#  Provjerava:
#    - validaciju pitanja po tipovima (modul 3b)
#    - jedan pokusaj po korisniku (modul 5)
#    - zabranu povratka i preskakanja pitanja (modul 5)
#    - mjerenje vremena na serveru i istek timera (modul 5)
#    - da tocni odgovori ne cure sudioniku (modul 5)
#    - autorizaciju i vlasnistvo (moduli 3a, 7)
#    - IDOR zastitu na ugnijezdenim rutama (modul 3b)
#
#  Pokretanje:
#    powershell -ExecutionPolicy Bypass -File .\test-mechanisms.ps1
#
#  Traje oko 30 sekundi (dio testova namjerno ceka istek timera).
# ============================================================

$ErrorActionPreference = "Stop"

$Base  = "http://localhost:8080/api"
$Stamp = Get-Date -Format "HHmmss"

$script:Passed = 0
$script:Failed = 0
$script:Failures = @()

# ------------------------------------------------------------
#  Infrastruktura
# ------------------------------------------------------------

# Poziva API i NIKAD ne baca iznimku.
# Vraca objekt: @{ Status = <int>; Body = <objekt ili $null> }
function Try-Api {
    param(
        [string] $Method,
        [string] $Path,
        $Body = $null,
        [string] $Token = $null
    )

    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }

    $params = @{
        Method      = $Method
        Uri         = "$Base$Path"
        Headers     = $headers
        ErrorAction = "Stop"
    }

    if ($null -ne $Body) {
        $params["Body"]        = ($Body | ConvertTo-Json -Depth 10)
        $params["ContentType"] = "application/json"
    }

    try {
        $response = Invoke-RestMethod @params
        return @{ Status = 200; Body = $response }
    }
    catch {
        $status = 0
        if ($_.Exception.Response) {
            $status = $_.Exception.Response.StatusCode.value__
        }
        return @{ Status = $status; Body = $null }
    }
}

# Poziva API i baca iznimku ako ne uspije (za pripremu podataka).
function Api {
    param([string] $Method, [string] $Path, $Body = $null, [string] $Token = $null)

    $result = Try-Api -Method $Method -Path $Path -Body $Body -Token $Token
    if ($result.Status -ne 200) {
        throw "Priprema podataka nije uspjela: $Method $Path -> HTTP $($result.Status)"
    }
    return $result.Body
}

function Assert-Status {
    param(
        [string] $Name,
        [int]    $Expected,
        [hashtable] $Result
    )

    if ($Result.Status -eq $Expected) {
        Write-Host ("  [OK]   {0}" -f $Name) -ForegroundColor Green
        $script:Passed++
    } else {
        Write-Host ("  [PAD]  {0}" -f $Name) -ForegroundColor Red
        Write-Host ("         ocekivano HTTP {0}, dobiveno {1}" -f $Expected, $Result.Status) -ForegroundColor DarkRed
        $script:Failed++
        $script:Failures += $Name
    }
}

function Assert-True {
    param([string] $Name, [bool] $Condition, [string] $Detail = "")

    if ($Condition) {
        Write-Host ("  [OK]   {0}" -f $Name) -ForegroundColor Green
        $script:Passed++
    } else {
        Write-Host ("  [PAD]  {0}" -f $Name) -ForegroundColor Red
        if ($Detail) { Write-Host ("         {0}" -f $Detail) -ForegroundColor DarkRed }
        $script:Failed++
        $script:Failures += $Name
    }
}

function Write-Section {
    param([string] $Text)
    Write-Host ""
    Write-Host "--- $Text " -ForegroundColor Cyan -NoNewline
    Write-Host ("-" * [Math]::Max(0, 55 - $Text.Length)) -ForegroundColor DarkCyan
}

function New-Account {
    param([string] $Username, [string] $Role)
    return Api -Method POST -Path "/auth/register" -Body @{
        username = $Username
        email    = "$Username@test.local"
        password = "tajna123"
        role     = $Role
    }
}

# ============================================================
#  PRIPREMA
# ============================================================

Write-Host ""
Write-Host "=== PRIPREMA PODATAKA ===" -ForegroundColor White

$org1 = New-Account -Username "torg1_$Stamp" -Role "ORGANIZER"
$org2 = New-Account -Username "torg2_$Stamp" -Role "ORGANIZER"
$p1   = New-Account -Username "tp1_$Stamp"   -Role "PARTICIPANT"
$p2   = New-Account -Username "tp2_$Stamp"   -Role "PARTICIPANT"

# --- Kviz A: objavljen, cetiri pitanja, zadnje s kratkim timerom ---
$quizA = Api -Method POST -Path "/quizzes" -Token $org1.token -Body @{
    title = "Test kviz A $Stamp"; description = "objavljen"; timeLimitMinutes = 30
}

Api -Method POST -Path "/quizzes/$($quizA.id)/questions" -Token $org1.token -Body @{
    type = "MULTIPLE_CHOICE"; text = "Glavni grad Australije?"
    timeLimitSeconds = 20; basePoints = 100
    options = @(
        @{ text = "Sydney"; correct = $false },
        @{ text = "Canberra"; correct = $true },
        @{ text = "Perth"; correct = $false }
    )
} | Out-Null

Api -Method POST -Path "/quizzes/$($quizA.id)/questions" -Token $org1.token -Body @{
    type = "TRUE_FALSE"; text = "Neretva protjece kroz Mostar."
    timeLimitSeconds = 15; basePoints = 50; correctAnswer = $true
} | Out-Null

Api -Method POST -Path "/quizzes/$($quizA.id)/questions" -Token $org1.token -Body @{
    type = "OPEN"; text = "Koje godine je sagraden Stari most?"
    timeLimitSeconds = 25; basePoints = 150
    acceptableAnswers = @("1566")
} | Out-Null

Api -Method POST -Path "/quizzes/$($quizA.id)/questions" -Token $org1.token -Body @{
    type = "MULTIPLE_CHOICE"; text = "Pitanje s kratkim timerom"
    timeLimitSeconds = 5; basePoints = 100
    options = @(
        @{ text = "Tocno"; correct = $true },
        @{ text = "Netocno"; correct = $false }
    )
} | Out-Null

Api -Method PATCH -Path "/quizzes/$($quizA.id)/publish" -Token $org1.token | Out-Null
$keyA = Api -Method GET -Path "/quizzes/$($quizA.id)/questions" -Token $org1.token

# --- Kviz B: ostaje DRAFT, koristi se za testove validacije ---
$quizB = Api -Method POST -Path "/quizzes" -Token $org1.token -Body @{
    title = "Test kviz B $Stamp"; description = "draft"
}
Api -Method POST -Path "/quizzes/$($quizB.id)/questions" -Token $org1.token -Body @{
    type = "TRUE_FALSE"; text = "Placeholder."
    timeLimitSeconds = 10; basePoints = 10; correctAnswer = $true
} | Out-Null

# --- Kviz C: tudi kviz, za testove vlasnistva i IDOR-a ---
$quizC = Api -Method POST -Path "/quizzes" -Token $org2.token -Body @{
    title = "Test kviz C $Stamp"; description = "tudi"
}
$questionC = Api -Method POST -Path "/quizzes/$($quizC.id)/questions" -Token $org2.token -Body @{
    type = "TRUE_FALSE"; text = "Tude pitanje."
    timeLimitSeconds = 10; basePoints = 10; correctAnswer = $true
}

Write-Host "  kviz A (objavljen) id=$($quizA.id), kviz B (draft) id=$($quizB.id), kviz C (tudi) id=$($quizC.id)" -ForegroundColor DarkGray

# ============================================================
#  1. VALIDACIJA PITANJA PO TIPOVIMA  (modul 3b)
# ============================================================

Write-Section "1. Validacija pitanja po tipovima"

$qPath = "/quizzes/$($quizB.id)/questions"

Assert-Status "MC s jednom opcijom se odbija" 400 (Try-Api -Method POST -Path $qPath -Token $org1.token -Body @{
    type = "MULTIPLE_CHOICE"; text = "Test"; timeLimitSeconds = 20; basePoints = 100
    options = @( @{ text = "Jedina"; correct = $true } )
})

Assert-Status "MC s dvije tocne opcije se odbija" 400 (Try-Api -Method POST -Path $qPath -Token $org1.token -Body @{
    type = "MULTIPLE_CHOICE"; text = "Test"; timeLimitSeconds = 20; basePoints = 100
    options = @(
        @{ text = "A"; correct = $true },
        @{ text = "B"; correct = $true },
        @{ text = "C"; correct = $false }
    )
})

Assert-Status "MC bez ijedne tocne opcije se odbija" 400 (Try-Api -Method POST -Path $qPath -Token $org1.token -Body @{
    type = "MULTIPLE_CHOICE"; text = "Test"; timeLimitSeconds = 20; basePoints = 100
    options = @(
        @{ text = "A"; correct = $false },
        @{ text = "B"; correct = $false }
    )
})

Assert-Status "T/F bez correctAnswer se odbija" 400 (Try-Api -Method POST -Path $qPath -Token $org1.token -Body @{
    type = "TRUE_FALSE"; text = "Test"; timeLimitSeconds = 15; basePoints = 50
})

Assert-Status "T/F s vlastitim opcijama se odbija" 400 (Try-Api -Method POST -Path $qPath -Token $org1.token -Body @{
    type = "TRUE_FALSE"; text = "Test"; timeLimitSeconds = 15; basePoints = 50
    correctAnswer = $true
    options = @( @{ text = "Da"; correct = $true }, @{ text = "Mozda"; correct = $false } )
})

Assert-Status "OPEN bez prihvatljivih odgovora se odbija" 400 (Try-Api -Method POST -Path $qPath -Token $org1.token -Body @{
    type = "OPEN"; text = "Test"; timeLimitSeconds = 25; basePoints = 150
})

Assert-Status "OPEN s ponudenim opcijama se odbija" 400 (Try-Api -Method POST -Path $qPath -Token $org1.token -Body @{
    type = "OPEN"; text = "Test"; timeLimitSeconds = 25; basePoints = 150
    acceptableAnswers = @("odgovor")
    options = @( @{ text = "A"; correct = $true }, @{ text = "B"; correct = $false } )
})

Assert-Status "timeLimitSeconds ispod donje granice se odbija" 400 (Try-Api -Method POST -Path $qPath -Token $org1.token -Body @{
    type = "TRUE_FALSE"; text = "Test"; timeLimitSeconds = 3; basePoints = 50; correctAnswer = $true
})

Assert-Status "prazan tekst pitanja se odbija" 400 (Try-Api -Method POST -Path $qPath -Token $org1.token -Body @{
    type = "TRUE_FALSE"; text = ""; timeLimitSeconds = 15; basePoints = 50; correctAnswer = $true
})

# ============================================================
#  2. AUTORIZACIJA I VLASNISTVO  (moduli 3a, 3b)
# ============================================================

Write-Section "2. Autorizacija i vlasnistvo"

Assert-Status "sudionik ne moze kreirati kviz" 403 (Try-Api -Method POST -Path "/quizzes" -Token $p1.token -Body @{
    title = "Neovlasteni kviz"
})

Assert-Status "tudi organizator ne moze urediti kviz" 403 (Try-Api -Method PUT -Path "/quizzes/$($quizB.id)" -Token $org2.token -Body @{
    title = "Otet kviz"
})

Assert-Status "objavljeni kviz se ne moze mijenjati" 409 (Try-Api -Method PUT -Path "/quizzes/$($quizA.id)" -Token $org1.token -Body @{
    title = "Izmjena nakon objave"
})

Assert-Status "objavljenom kvizu se ne moze dodati pitanje" 409 (Try-Api -Method POST -Path "/quizzes/$($quizA.id)/questions" -Token $org1.token -Body @{
    type = "TRUE_FALSE"; text = "Naknadno"; timeLimitSeconds = 10; basePoints = 10; correctAnswer = $true
})

Assert-Status "IDOR: tude pitanje pod svojim kvizom daje 404" 404 (Try-Api -Method PUT -Path "/quizzes/$($quizB.id)/questions/$($questionC.id)" -Token $org1.token -Body @{
    type = "TRUE_FALSE"; text = "Preotimanje"; timeLimitSeconds = 10; basePoints = 10; correctAnswer = $true
})

Assert-Status "DRAFT kviz se ne moze igrati" 409 (Try-Api -Method POST -Path "/quizzes/$($quizB.id)/play" -Token $p1.token)

# ============================================================
#  3. KONTROLNI MEHANIZMI  (modul 5)
# ============================================================

Write-Section "3. Kontrolni mehanizmi"

# --- pokretanje ---
$start = Try-Api -Method POST -Path "/quizzes/$($quizA.id)/play" -Token $p1.token
Assert-Status "sudionik moze pokrenuti objavljeni kviz" 200 $start

$pid1 = $start.Body.participationId
$current = $start.Body

Assert-True "prvo pitanje ima redni broj 1" ($current.questionNumber -eq 1) `
    "dobiveno: $($current.questionNumber)"

# --- curenje tocnih odgovora ---
$leaks = $false
foreach ($opt in $current.options) {
    if ($opt.PSObject.Properties.Name -contains "correct") { $leaks = $true }
}
Assert-True "opcije ne sadrze oznaku tocnosti" (-not $leaks) `
    "PlayQuestionResponse otkriva koji je odgovor tocan"

Assert-True "pitanje ne sadrzi prihvatljive tekstualne odgovore" `
    (-not ($current.PSObject.Properties.Name -contains "acceptableAnswers"))

# --- jedan pokusaj ---
Assert-Status "drugi pokusaj istog korisnika se odbija" 409 `
    (Try-Api -Method POST -Path "/quizzes/$($quizA.id)/play" -Token $p1.token)

# --- rezultati se ne vide tijekom rjesavanja ---
Assert-Status "rezultat nije dostupan dok kviz traje" 409 `
    (Try-Api -Method GET -Path "/participations/$pid1/result" -Token $p1.token)

# --- tudi pokusaj ---
Assert-Status "tudi pokusaj nije vidljiv (404, ne 403)" 404 `
    (Try-Api -Method GET -Path "/participations/$pid1" -Token $p2.token)

# --- timer se ne resetira ponovnim dohvatom ---
$first = Try-Api -Method GET -Path "/participations/$pid1/current-question" -Token $p1.token
Start-Sleep -Seconds 3
$second = Try-Api -Method GET -Path "/participations/$pid1/current-question" -Token $p1.token

$dropped = $second.Body.remainingMs -lt ($first.Body.remainingMs - 2000)
Assert-True "ponovni dohvat pitanja ne resetira timer" $dropped `
    "prvi dohvat: $($first.Body.remainingMs) ms, drugi: $($second.Body.remainingMs) ms"

# --- odgovor na prvo pitanje ---
$q1 = $keyA | Where-Object { $_.id -eq $current.questionId }
$q1Correct = ($q1.options | Where-Object { $_.correct })[0]

$answer1 = Try-Api -Method POST `
    -Path "/participations/$pid1/questions/$($current.questionId)/answer" `
    -Token $p1.token -Body @{ selectedOptionId = $q1Correct.id }

Assert-Status "tocan odgovor se prihvaca" 200 $answer1
Assert-True "tocan odgovor nosi bodove" ($answer1.Body.pointsAwarded -gt 0) `
    "dobiveno bodova: $($answer1.Body.pointsAwarded)"
Assert-True "bodovi su umanjeni zbog protekla 3 sekunde" `
    ($answer1.Body.pointsAwarded -lt 100) `
    "dobiveno: $($answer1.Body.pointsAwarded), ocekivano manje od baznih 100"

# --- zabrana povratka ---
Assert-Status "povratak na prethodno pitanje se odbija" 409 (Try-Api -Method POST `
    -Path "/participations/$pid1/questions/$($current.questionId)/answer" `
    -Token $p1.token -Body @{ selectedOptionId = $q1Correct.id })

# --- zabrana preskakanja ---
$q3Id = $keyA[2].id
Assert-Status "preskakanje na kasnije pitanje se odbija" 409 (Try-Api -Method POST `
    -Path "/participations/$pid1/questions/$q3Id/answer" `
    -Token $p1.token -Body @{ textAnswer = "1566" })

# --- odgovor s opcijom iz drugog pitanja ---
$current = (Try-Api -Method GET -Path "/participations/$pid1/current-question" -Token $p1.token).Body
Assert-Status "opcija iz drugog pitanja se odbija" 400 (Try-Api -Method POST `
    -Path "/participations/$pid1/questions/$($current.questionId)/answer" `
    -Token $p1.token -Body @{ selectedOptionId = $q1Correct.id })

# --- pitanje 2: T/F, tocno ---
$q2 = $keyA | Where-Object { $_.id -eq $current.questionId }
$q2Correct = ($q2.options | Where-Object { $_.correct })[0]
Try-Api -Method POST -Path "/participations/$pid1/questions/$($current.questionId)/answer" `
    -Token $p1.token -Body @{ selectedOptionId = $q2Correct.id } | Out-Null

# --- pitanje 3: OPEN, provjera normalizacije ---
$current = (Try-Api -Method GET -Path "/participations/$pid1/current-question" -Token $p1.token).Body
$answer3 = Try-Api -Method POST -Path "/participations/$pid1/questions/$($current.questionId)/answer" `
    -Token $p1.token -Body @{ textAnswer = "  1566  " }

Assert-True "otvoreni odgovor se normalizira prije usporedbe" ($answer3.Body.correct) `
    "odgovor '  1566  ' nije prepoznat kao tocan"

# --- pitanje 4: istek timera (limit 5 s) ---
$current = (Try-Api -Method GET -Path "/participations/$pid1/current-question" -Token $p1.token).Body
$q4 = $keyA | Where-Object { $_.id -eq $current.questionId }
$q4Correct = ($q4.options | Where-Object { $_.correct })[0]

Write-Host "         cekam istek timera (9 s)..." -ForegroundColor DarkGray
Start-Sleep -Seconds 9

$answer4 = Try-Api -Method POST -Path "/participations/$pid1/questions/$($current.questionId)/answer" `
    -Token $p1.token -Body @{ selectedOptionId = $q4Correct.id }

Assert-True "istekli odgovor je oznacen kao expired" ($answer4.Body.expired) `
    "expired = $($answer4.Body.expired)"
Assert-True "istekli tocan odgovor nosi nula bodova" ($answer4.Body.pointsAwarded -eq 0) `
    "dobiveno bodova: $($answer4.Body.pointsAwarded)"
Assert-True "istekli odgovor se ne racuna kao tocan" (-not $answer4.Body.correct)
Assert-True "kviz je zavrsen nakon zadnjeg pitanja" ($answer4.Body.quizFinished)

# --- nakon zavrsetka ---
Assert-Status "odgovor nakon zavrsetka se odbija" 409 (Try-Api -Method POST `
    -Path "/participations/$pid1/questions/$($current.questionId)/answer" `
    -Token $p1.token -Body @{ selectedOptionId = $q4Correct.id })

Assert-Status "dohvat pitanja nakon zavrsetka se odbija" 409 `
    (Try-Api -Method GET -Path "/participations/$pid1/current-question" -Token $p1.token)

# ============================================================
#  4. REZULTATI I STATISTIKA  (modul 7)
# ============================================================

Write-Section "4. Rezultati i statistika"

$result = Try-Api -Method GET -Path "/participations/$pid1/result" -Token $p1.token
Assert-Status "rezultat je dostupan nakon zavrsetka" 200 $result
Assert-True "rezultat sadrzi sva cetiri pitanja" ($result.Body.answers.Count -eq 4) `
    "dobiveno: $($result.Body.answers.Count)"
Assert-True "rezultat otkriva tocne odgovore tek sada" `
    (-not [string]::IsNullOrEmpty($result.Body.answers[0].correctAnswer))
Assert-True "ukupni bodovi odgovaraju zbroju po pitanjima" `
    ($result.Body.totalScore -eq (($result.Body.answers | Measure-Object -Property pointsAwarded -Sum).Sum)) `
    "ukupno: $($result.Body.totalScore)"

Assert-Status "tudi rezultat nije dostupan" 404 `
    (Try-Api -Method GET -Path "/participations/$pid1/result" -Token $p2.token)

Assert-Status "sudionik ne moze vidjeti statistiku kviza" 403 `
    (Try-Api -Method GET -Path "/quizzes/$($quizA.id)/stats" -Token $p1.token)

Assert-Status "tudi organizator ne moze vidjeti statistiku" 403 `
    (Try-Api -Method GET -Path "/quizzes/$($quizA.id)/stats" -Token $org2.token)

$stats = Try-Api -Method GET -Path "/quizzes/$($quizA.id)/stats" -Token $org1.token
Assert-Status "vlasnik moze vidjeti statistiku" 200 $stats
Assert-True "statistika obuhvaca sva pitanja" ($stats.Body.questions.Count -eq 4) `
    "dobiveno: $($stats.Body.questions.Count)"
Assert-True "statistika biljezi jednog zavrsenog sudionika" ($stats.Body.participantCount -eq 1) `
    "dobiveno: $($stats.Body.participantCount)"

$board = Try-Api -Method GET -Path "/quizzes/$($quizA.id)/leaderboard" -Token $org1.token
Assert-Status "rang-lista je dostupna" 200 $board
Assert-True "rang-lista sadrzi zavrseni pokusaj" ($board.Body.Count -ge 1)
Assert-True "prvi na rang-listi ima mjesto 1" ($board.Body[0].rank -eq 1)

# ============================================================
#  SAZETAK
# ============================================================

Write-Host ""
Write-Host ("=" * 60) -ForegroundColor White
$total = $script:Passed + $script:Failed
Write-Host ("  PROSLO: {0} / {1}" -f $script:Passed, $total) -ForegroundColor Green

if ($script:Failed -gt 0) {
    Write-Host ("  PALO:   {0}" -f $script:Failed) -ForegroundColor Red
    Write-Host ""
    foreach ($name in $script:Failures) {
        Write-Host "    - $name" -ForegroundColor Red
    }
} else {
    Write-Host "  Svi kontrolni mehanizmi rade ispravno." -ForegroundColor Green
}
Write-Host ("=" * 60) -ForegroundColor White
Write-Host ""
