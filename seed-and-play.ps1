# ============================================================
#  seed-and-play.ps1
#  Testni scenarij za kviz aplikaciju.
#
#  Sto radi:
#    1. registrira organizatora
#    2. kreira kviz s tri pitanja (MC, TRUE_FALSE, OPEN)
#    3. objavi kviz
#    4. registrira tri sudionika
#    5. svaki sudionik odigra kviz razlicitom brzinom i tocnoscu
#    6. ispise rang-listu i statistiku
#
#  Pokretanje:
#    .\seed-and-play.ps1
#
#  Svako pokretanje koristi nova korisnicka imena (vremenski pecat),
#  pa se skripta moze pokretati vise puta bez sudara s pravilom
#  "jedan pokusaj po korisniku".
# ============================================================

$ErrorActionPreference = "Stop"

$Base  = "http://localhost:8080/api"
$Stamp = Get-Date -Format "HHmmss"

# ------------------------------------------------------------
# Pomocne funkcije
# ------------------------------------------------------------

function Invoke-Api {
    param(
        [string] $Method,
        [string] $Path,
        $Body = $null,
        [string] $Token = $null
    )

    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }

    $params = @{
        Method  = $Method
        Uri     = "$Base$Path"
        Headers = $headers
    }

    if ($null -ne $Body) {
        $params["Body"]        = ($Body | ConvertTo-Json -Depth 10)
        $params["ContentType"] = "application/json"
    }

    try {
        return Invoke-RestMethod @params
    }
    catch {
        $status = $_.Exception.Response.StatusCode.value__
        $detail = ""
        try {
            $reader = New-Object System.IO.StreamReader(
                $_.Exception.Response.GetResponseStream())
            $detail = $reader.ReadToEnd()
        } catch { }
        Write-Host "  [HTTP $status] $Method $Path" -ForegroundColor Red
        if ($detail) { Write-Host "  $detail" -ForegroundColor DarkRed }
        throw
    }
}

function Register-User {
    param([string] $Username, [string] $Role)

    $response = Invoke-Api -Method POST -Path "/auth/register" -Body @{
        username = $Username
        email    = "$Username@test.local"
        password = "tajna123"
        role     = $Role
    }
    Write-Host "  registriran: $Username ($Role)" -ForegroundColor DarkGray
    return $response
}

function Write-Section {
    param([string] $Text)
    Write-Host ""
    Write-Host "=== $Text ===" -ForegroundColor Cyan
}

# ------------------------------------------------------------
# 1. Organizator
# ------------------------------------------------------------

Write-Section "1. Organizator"

$organizer      = Register-User -Username "org_$Stamp" -Role "ORGANIZER"
$organizerToken = $organizer.token

# ------------------------------------------------------------
# 2. Kviz
# ------------------------------------------------------------

Write-Section "2. Kreiranje kviza"

$quiz = Invoke-Api -Method POST -Path "/quizzes" -Token $organizerToken -Body @{
    title            = "Testni pub kviz $Stamp"
    description      = "Automatski generiran za testiranje"
    timeLimitMinutes = 30
}

$quizId = $quiz.id
Write-Host "  kviz id=$quizId, status=$($quiz.status)" -ForegroundColor DarkGray

# ------------------------------------------------------------
# 3. Pitanja
# ------------------------------------------------------------

Write-Section "3. Dodavanje pitanja"

$questionPayloads = @(
    @{
        type              = "MULTIPLE_CHOICE"
        text              = "Koji je glavni grad Australije?"
        timeLimitSeconds  = 20
        basePoints        = 100
        options           = @(
            @{ text = "Sydney";    correct = $false },
            @{ text = "Melbourne"; correct = $false },
            @{ text = "Canberra";  correct = $true  },
            @{ text = "Perth";     correct = $false }
        )
    },
    @{
        type             = "TRUE_FALSE"
        text             = "Rijeka Neretva protjece kroz Mostar."
        timeLimitSeconds = 15
        basePoints       = 50
        correctAnswer    = $true
    },
    @{
        type              = "OPEN"
        text              = "Koje godine je sagraden Stari most?"
        timeLimitSeconds  = 25
        basePoints        = 150
        acceptableAnswers = @("1566", "tisucu petsto sezdeset seste")
    }
)

foreach ($payload in $questionPayloads) {
    $created = Invoke-Api -Method POST -Path "/quizzes/$quizId/questions" `
                          -Token $organizerToken -Body $payload
    Write-Host "  pitanje $($created.orderIndex + 1): $($created.type)" -ForegroundColor DarkGray
}

# ------------------------------------------------------------
# 4. Objava
# ------------------------------------------------------------

Write-Section "4. Objava kviza"

$published = Invoke-Api -Method PATCH -Path "/quizzes/$quizId/publish" -Token $organizerToken
Write-Host "  status: $($published.status)" -ForegroundColor DarkGray

# tocni odgovori, da skripta zna sto poslati
$answerKey = Invoke-Api -Method GET -Path "/quizzes/$quizId/questions" -Token $organizerToken

# ------------------------------------------------------------
# 5. Sudionici
# ------------------------------------------------------------

Write-Section "5. Sudionici igraju"

# Profil igraca:
#   Delay      - koliko sekundi ceka prije odgovora (utjece na bodove)
#   WrongOn    - na kojim rednim brojevima pitanja namjerno grijesi
$players = @(
    @{ Name = "ana_$Stamp";   Delay = 1; WrongOn = @()    },
    @{ Name = "marko_$Stamp"; Delay = 4; WrongOn = @(2)   },
    @{ Name = "ivan_$Stamp";  Delay = 8; WrongOn = @(1,3) }
)

foreach ($player in $players) {
    Write-Host ""
    Write-Host "  --- $($player.Name) ---" -ForegroundColor Yellow

    $account = Register-User -Username $player.Name -Role "PARTICIPANT"
    $token   = $account.token

    $current = Invoke-Api -Method POST -Path "/quizzes/$quizId/play" -Token $token
    $participationId = $current.participationId

    $finished = $false
    while (-not $finished) {

        $number   = $current.questionNumber
        $question = $answerKey | Where-Object { $_.id -eq $current.questionId }
        $goWrong  = $player.WrongOn -contains $number

        Start-Sleep -Seconds $player.Delay

        # sastavi tijelo odgovora ovisno o tipu pitanja
        if ($question.type -eq "OPEN") {
            $text = if ($goWrong) { "pogresan odgovor" } else { $question.acceptableAnswers[0] }
            $body = @{ textAnswer = $text }
        }
        else {
            $correctOption = $question.options | Where-Object { $_.correct } | Select-Object -First 1
            $chosen = if ($goWrong) {
                $question.options | Where-Object { -not $_.correct } | Select-Object -First 1
            } else {
                $correctOption
            }
            $body = @{ selectedOptionId = $chosen.id }
        }

        $result = Invoke-Api -Method POST `
            -Path "/participations/$participationId/questions/$($current.questionId)/answer" `
            -Token $token -Body $body

        $mark  = if ($result.correct) { "tocno " } else { "netocno" }
        $color = if ($result.correct) { "Green" } else { "DarkGray" }
        Write-Host ("    P{0}: {1}  {2,4} bodova  ({3,5} ms)" -f `
            $number, $mark, $result.pointsAwarded, $result.timeTakenMs) -ForegroundColor $color

        $finished = $result.quizFinished
        if (-not $finished) {
            $current = Invoke-Api -Method GET `
                -Path "/participations/$participationId/current-question" -Token $token
        }
    }

    $summary = Invoke-Api -Method GET -Path "/participations/$participationId" -Token $token
    Write-Host "    ukupno: $($summary.totalScore) bodova" -ForegroundColor White
}

# ------------------------------------------------------------
# 6. Rezultati
# ------------------------------------------------------------

Write-Section "6. Rang-lista"

$leaderboard = Invoke-Api -Method GET -Path "/quizzes/$quizId/leaderboard" -Token $organizerToken
$leaderboard |
    Select-Object rank,
                  participantName,
                  totalScore,
                  @{ Name = "tocnih"; Expression = { "$($_.correctAnswers)/$($_.totalQuestions)" } },
                  @{ Name = "trajanje_s"; Expression = { [math]::Round($_.durationMs / 1000, 1) } } |
    Format-Table -AutoSize

Write-Section "7. Statistika kviza"

$stats = Invoke-Api -Method GET -Path "/quizzes/$quizId/stats" -Token $organizerToken

Write-Host "  sudionika:        $($stats.participantCount)"
Write-Host "  prosjecno bodova: $($stats.averageScore) / $($stats.maxPossibleScore)"
Write-Host ""

$stats.questions |
    Select-Object @{ Name = "br";       Expression = { $_.orderIndex } },
                  type,
                  @{ Name = "tocnih";   Expression = { "$($_.correctAnswers)/$($_.totalAnswers)" } },
                  @{ Name = "uspjeh_%"; Expression = { $_.successRate } },
                  @{ Name = "prosj_s";  Expression = { $_.averageTimeSeconds } },
                  @{ Name = "prosj_bod";Expression = { $_.averagePoints } } |
    Format-Table -AutoSize

Write-Host ""
Write-Host "Gotovo. Quiz ID: $quizId" -ForegroundColor Green
Write-Host "Organizatorski token (za rucno testiranje u Postmanu):" -ForegroundColor DarkGray
Write-Host $organizerToken -ForegroundColor DarkGray
