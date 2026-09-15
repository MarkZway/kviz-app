# ============================================================
#  test-teams.ps1
#  Automatska provjera timskog dijela aplikacije (modul 4).
#
#  Provjerava:
#    - kreiranje tima i generiranje koda za pridruzivanje
#    - pridruzivanje kodom i pravila oko clanstva
#    - vidljivost koda (samo clanovi)
#    - timsko rjesavanje kviza (bilo koji clan odgovara)
#    - jedan pokusaj po timu i iskljucivost pojedinacno/timski
#    - prava kapetana i upravljanje clanstvom
#    - rezultate i rang-listu za timove
#
#  Pokretanje:
#    powershell -ExecutionPolicy Bypass -File .\test-teams.ps1
#
#  Traje oko 15 sekundi.
# ============================================================

$ErrorActionPreference = "Stop"

$Base  = "http://localhost:8080/api"
$Stamp = Get-Date -Format "HHmmss"

$script:Passed   = 0
$script:Failed   = 0
$script:Failures = @()

# ------------------------------------------------------------
#  Infrastruktura
# ------------------------------------------------------------

function Try-Api {
    param([string] $Method, [string] $Path, $Body = $null, [string] $Token = $null)

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
        if ($_.Exception.Response) { $status = $_.Exception.Response.StatusCode.value__ }
        return @{ Status = $status; Body = $null }
    }
}

function Api {
    param([string] $Method, [string] $Path, $Body = $null, [string] $Token = $null)
    $result = Try-Api -Method $Method -Path $Path -Body $Body -Token $Token
    if ($result.Status -ne 200) {
        throw "Priprema nije uspjela: $Method $Path -> HTTP $($result.Status)"
    }
    return $result.Body
}

function Assert-Status {
    param([string] $Name, [int] $Expected, [hashtable] $Result)
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

$org      = New-Account -Username "korg_$Stamp"   -Role "ORGANIZER"
$kapetan  = New-Account -Username "kap_$Stamp"    -Role "PARTICIPANT"
$clan1    = New-Account -Username "clan1_$Stamp"  -Role "PARTICIPANT"
$clan2    = New-Account -Username "clan2_$Stamp"  -Role "PARTICIPANT"
$autsajder= New-Account -Username "auts_$Stamp"   -Role "PARTICIPANT"

$quiz = Api -Method POST -Path "/quizzes" -Token $org.token -Body @{
    title = "Timski kviz $Stamp"; description = "test"; timeLimitMinutes = 30
}

Api -Method POST -Path "/quizzes/$($quiz.id)/questions" -Token $org.token -Body @{
    type = "MULTIPLE_CHOICE"; text = "Glavni grad Australije?"
    timeLimitSeconds = 30; basePoints = 100
    options = @(
        @{ text = "Sydney"; correct = $false },
        @{ text = "Canberra"; correct = $true },
        @{ text = "Perth"; correct = $false }
    )
} | Out-Null

Api -Method POST -Path "/quizzes/$($quiz.id)/questions" -Token $org.token -Body @{
    type = "TRUE_FALSE"; text = "Neretva protjece kroz Mostar."
    timeLimitSeconds = 30; basePoints = 50; correctAnswer = $true
} | Out-Null

Api -Method POST -Path "/quizzes/$($quiz.id)/questions" -Token $org.token -Body @{
    type = "OPEN"; text = "Koje godine je sagraden Stari most?"
    timeLimitSeconds = 30; basePoints = 150
    acceptableAnswers = @("1566")
} | Out-Null

Api -Method PATCH -Path "/quizzes/$($quiz.id)/publish" -Token $org.token | Out-Null
$key = Api -Method GET -Path "/quizzes/$($quiz.id)/questions" -Token $org.token

Write-Host "  kviz id=$($quiz.id) objavljen s 3 pitanja" -ForegroundColor DarkGray

# ============================================================
#  1. KREIRANJE TIMA
# ============================================================

Write-Section "1. Kreiranje tima"

$createResult = Try-Api -Method POST -Path "/teams" -Token $kapetan.token -Body @{
    name = "Neretvanski lavovi $Stamp"
}
Assert-Status "kapetan moze kreirati tim" 200 $createResult

$team = $createResult.Body
$teamId = $team.id

Assert-True "tim ima generiran kod za pridruzivanje" `
    (-not [string]::IsNullOrWhiteSpace($team.joinCode)) `
    "joinCode: '$($team.joinCode)'"

Assert-True "kod ima 6 znakova" ($team.joinCode.Length -eq 6) `
    "duljina: $($team.joinCode.Length)"

Assert-True "kod ne sadrzi znakove koji se lako zamijene (0 O 1 I L)" `
    ($team.joinCode -notmatch "[01OIL]") "kod: $($team.joinCode)"

Assert-True "kreator je automatski clan tima" ($team.members.Count -eq 1) `
    "clanova: $($team.members.Count)"

Assert-True "kreator je oznacen kao kapetan" ($team.members[0].captain -eq $true)

Assert-Status "tim s istim nazivom se odbija" 409 (Try-Api -Method POST -Path "/teams" `
    -Token $clan1.token -Body @{ name = "Neretvanski lavovi $Stamp" })

Assert-Status "prekratak naziv tima se odbija" 400 (Try-Api -Method POST -Path "/teams" `
    -Token $clan1.token -Body @{ name = "X" })

Assert-Status "prazan naziv tima se odbija" 400 (Try-Api -Method POST -Path "/teams" `
    -Token $clan1.token -Body @{ name = "" })

# ============================================================
#  2. PRIDRUZIVANJE TIMU
# ============================================================

Write-Section "2. Pridruzivanje timu"

$join1 = Try-Api -Method POST -Path "/teams/join" -Token $clan1.token -Body @{
    joinCode = $team.joinCode
}
Assert-Status "clan se moze pridruziti ispravnim kodom" 200 $join1
Assert-True "tim sada ima dva clana" ($join1.Body.members.Count -eq 2) `
    "clanova: $($join1.Body.members.Count)"

# kod se normalizira na velika slova
$join2 = Try-Api -Method POST -Path "/teams/join" -Token $clan2.token -Body @{
    joinCode = $team.joinCode.ToLower()
}
Assert-Status "kod napisan malim slovima se prihvaca" 200 $join2
Assert-True "tim sada ima tri clana" ($join2.Body.members.Count -eq 3) `
    "clanova: $($join2.Body.members.Count)"

Assert-Status "ponovno pridruzivanje istog clana se odbija" 409 `
    (Try-Api -Method POST -Path "/teams/join" -Token $clan1.token -Body @{ joinCode = $team.joinCode })

Assert-Status "nepostojeci kod daje 404" 404 `
    (Try-Api -Method POST -Path "/teams/join" -Token $autsajder.token -Body @{ joinCode = "ZZZZZZ" })

Assert-Status "prekratak kod se odbija" 400 `
    (Try-Api -Method POST -Path "/teams/join" -Token $autsajder.token -Body @{ joinCode = "AB" })

# ============================================================
#  3. VIDLJIVOST KODA
# ============================================================

Write-Section "3. Vidljivost koda za pridruzivanje"

$asMember = Try-Api -Method GET -Path "/teams/$teamId" -Token $clan1.token
Assert-True "clan vidi kod za pridruzivanje" `
    (-not [string]::IsNullOrWhiteSpace($asMember.Body.joinCode))

$asOutsider = Try-Api -Method GET -Path "/teams/$teamId" -Token $autsajder.token
Assert-Status "necl.an moze vidjeti osnovne podatke o timu" 200 $asOutsider
Assert-True "neclan NE vidi kod za pridruzivanje" `
    ([string]::IsNullOrWhiteSpace($asOutsider.Body.joinCode)) `
    "procurio kod: $($asOutsider.Body.joinCode)"

$mine = Try-Api -Method GET -Path "/teams/mine" -Token $clan2.token
Assert-Status "lista mojih timova je dostupna" 200 $mine
Assert-True "moji timovi sadrze tim kojem sam se pridruzio" `
    (@($mine.Body | Where-Object { $_.id -eq $teamId }).Count -eq 1)

$notMine = Try-Api -Method GET -Path "/teams/mine" -Token $autsajder.token
Assert-True "neclan nema taj tim u svojoj listi" `
    (@($notMine.Body | Where-Object { $_.id -eq $teamId }).Count -eq 0)

# ============================================================
#  4. TIMSKO RJESAVANJE KVIZA
# ============================================================

Write-Section "4. Timsko rjesavanje kviza"

Assert-Status "neclan ne moze pokrenuti kviz za tim" 403 `
    (Try-Api -Method POST -Path "/quizzes/$($quiz.id)/play-as-team/$teamId" -Token $autsajder.token)

$start = Try-Api -Method POST -Path "/quizzes/$($quiz.id)/play-as-team/$teamId" -Token $kapetan.token
Assert-Status "kapetan moze pokrenuti kviz za tim" 200 $start

$partId = $start.Body.participationId

Assert-Status "drugi pokusaj istog tima se odbija" 409 `
    (Try-Api -Method POST -Path "/quizzes/$($quiz.id)/play-as-team/$teamId" -Token $kapetan.token)

Assert-Status "drugi clan tima takoder ne moze pokrenuti novi pokusaj" 409 `
    (Try-Api -Method POST -Path "/quizzes/$($quiz.id)/play-as-team/$teamId" -Token $clan1.token)

Assert-Status "clan tima koji je igrao ne moze igrati pojedinacno" 409 `
    (Try-Api -Method POST -Path "/quizzes/$($quiz.id)/play" -Token $clan2.token)

Assert-Status "neclan ne moze dohvatiti timski pokusaj" 404 `
    (Try-Api -Method GET -Path "/participations/$partId" -Token $autsajder.token)

# --- kljucni test: razliciti clanovi odgovaraju na razlicita pitanja ---

$current = $start.Body

# pitanje 1 odgovara CLAN1 (nije onaj koji je pokrenuo)
$q = $key | Where-Object { $_.id -eq $current.questionId }
$correctOpt = ($q.options | Where-Object { $_.correct })[0]

$a1 = Try-Api -Method POST -Path "/participations/$partId/questions/$($current.questionId)/answer" `
    -Token $clan1.token -Body @{ selectedOptionId = $correctOpt.id }

Assert-Status "clan koji nije pokrenuo kviz moze odgovoriti" 200 $a1
Assert-True "odgovor clana je priznat kao tocan" ($a1.Body.correct)

# pitanje 2 odgovara CLAN2
$current = (Try-Api -Method GET -Path "/participations/$partId/current-question" -Token $clan2.token).Body
$q = $key | Where-Object { $_.id -eq $current.questionId }
$correctOpt = ($q.options | Where-Object { $_.correct })[0]

$a2 = Try-Api -Method POST -Path "/participations/$partId/questions/$($current.questionId)/answer" `
    -Token $clan2.token -Body @{ selectedOptionId = $correctOpt.id }
Assert-Status "treci clan moze odgovoriti na sljedece pitanje" 200 $a2

# neclan pokusava odgovoriti
$current = (Try-Api -Method GET -Path "/participations/$partId/current-question" -Token $kapetan.token).Body
Assert-Status "neclan ne moze odgovoriti u ime tima" 404 (Try-Api -Method POST `
    -Path "/participations/$partId/questions/$($current.questionId)/answer" `
    -Token $autsajder.token -Body @{ textAnswer = "1566" })

# pitanje 3 odgovara KAPETAN
$a3 = Try-Api -Method POST -Path "/participations/$partId/questions/$($current.questionId)/answer" `
    -Token $kapetan.token -Body @{ textAnswer = "1566" }
Assert-Status "kapetan zavrsava kviz" 200 $a3
Assert-True "kviz je zavrsen nakon zadnjeg pitanja" ($a3.Body.quizFinished)
Assert-True "ukupni bodovi tima su veci od nule" ($a3.Body.totalScore -gt 0) `
    "bodovi: $($a3.Body.totalScore)"

# ============================================================
#  5. REZULTATI I RANG-LISTA
# ============================================================

Write-Section "5. Rezultati i rang-lista"

$result = Try-Api -Method GET -Path "/participations/$partId/result" -Token $clan2.token
Assert-Status "clan tima vidi timski rezultat" 200 $result
Assert-True "rezultat sadrzi sva tri pitanja" ($result.Body.answers.Count -eq 3) `
    "dobiveno: $($result.Body.answers.Count)"

Assert-Status "neclan ne vidi timski rezultat" 404 `
    (Try-Api -Method GET -Path "/participations/$partId/result" -Token $autsajder.token)

Assert-Status "organizator kviza vidi timski rezultat" 200 `
    (Try-Api -Method GET -Path "/participations/$partId/result" -Token $org.token)

$board = Try-Api -Method GET -Path "/quizzes/$($quiz.id)/leaderboard" -Token $org.token
Assert-Status "rang-lista je dostupna" 200 $board

$teamEntry = $board.Body | Where-Object { $_.participationId -eq $partId }
Assert-True "rang-lista sadrzi timski pokusaj" ($null -ne $teamEntry)
Assert-True "rang-lista prikazuje naziv tima, a ne korisnicko ime" `
    ($teamEntry.participantName -eq $team.name) `
    "prikazano: '$($teamEntry.participantName)', ocekivano: '$($team.name)'"
Assert-True "pokusaj je oznacen kao timski" ($teamEntry.isTeam -eq $true)

$myParts = Try-Api -Method GET -Path "/participations/mine" -Token $clan1.token
Assert-True "timski pokusaj je vidljiv u 'moji pokusaji' svakog clana" `
    (@($myParts.Body | Where-Object { $_.id -eq $partId }).Count -eq 1)

# ============================================================
#  6. UPRAVLJANJE CLANSTVOM
# ============================================================

Write-Section "6. Upravljanje clanstvom"

Assert-Status "obican clan ne moze ukloniti drugog clana" 403 `
    (Try-Api -Method DELETE -Path "/teams/$teamId/members/$($clan2.userId)" -Token $clan1.token)

Assert-Status "kapetan ne moze napustiti tim" 409 `
    (Try-Api -Method DELETE -Path "/teams/$teamId/members/me" -Token $kapetan.token)

Assert-Status "kapetanstvo se ne moze predati neclanu" 400 `
    (Try-Api -Method PATCH -Path "/teams/$teamId/captain/$($autsajder.userId)" -Token $kapetan.token)

Assert-Status "obican clan ne moze predati kapetanstvo" 403 `
    (Try-Api -Method PATCH -Path "/teams/$teamId/captain/$($clan1.userId)" -Token $clan1.token)

Assert-Status "neclan ne moze napustiti tim" 404 `
    (Try-Api -Method DELETE -Path "/teams/$teamId/members/me" -Token $autsajder.token)

$removed = Try-Api -Method DELETE -Path "/teams/$teamId/members/$($clan2.userId)" -Token $kapetan.token
Assert-Status "kapetan moze ukloniti clana" 200 $removed
Assert-True "tim nakon uklanjanja ima dva clana" ($removed.Body.members.Count -eq 2) `
    "clanova: $($removed.Body.members.Count)"

Assert-Status "kapetan ne moze ukloniti samog sebe" 409 `
    (Try-Api -Method DELETE -Path "/teams/$teamId/members/$($kapetan.userId)" -Token $kapetan.token)

$transferred = Try-Api -Method PATCH -Path "/teams/$teamId/captain/$($clan1.userId)" -Token $kapetan.token
Assert-Status "kapetanstvo se moze predati clanu tima" 200 $transferred
Assert-True "novi kapetan je ispravno postavljen" ($transferred.Body.captainId -eq $clan1.userId) `
    "captainId: $($transferred.Body.captainId), ocekivano: $($clan1.userId)"

Assert-Status "bivsi kapetan sada moze napustiti tim" 200 `
    (Try-Api -Method DELETE -Path "/teams/$teamId/members/me" -Token $kapetan.token)

# ============================================================
#  7. RASPUSTANJE TIMA
# ============================================================

Write-Section "7. Raspustanje tima"

$spareTeam = Api -Method POST -Path "/teams" -Token $autsajder.token -Body @{
    name = "Privremeni tim $Stamp"
}

Assert-Status "obican clan ne moze raspustiti tim" 403 `
    (Try-Api -Method DELETE -Path "/teams/$($spareTeam.id)" -Token $clan1.token)

Assert-Status "kapetan moze raspustiti tim bez odigranih kvizova" 200 `
    (Try-Api -Method DELETE -Path "/teams/$($spareTeam.id)" -Token $autsajder.token)

Assert-Status "raspusteni tim vise nije dostupan" 404 `
    (Try-Api -Method GET -Path "/teams/$($spareTeam.id)" -Token $autsajder.token)

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
    Write-Host "  Timski dio aplikacije radi ispravno." -ForegroundColor Green
}
Write-Host ("=" * 60) -ForegroundColor White
Write-Host ""


