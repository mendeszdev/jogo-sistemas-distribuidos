[CmdletBinding()]
param(
    [string]$Maven = 'mvn'
)

$ErrorActionPreference = 'Stop'
$raizProjeto = Split-Path -Parent $PSScriptRoot
$comandoMaven = Get-Command $Maven -ErrorAction SilentlyContinue
if ($comandoMaven) {
    $executavelMaven = $comandoMaven.Source
} else {
    $distribuicoes = Join-Path $env:USERPROFILE '.m2/wrapper/dists'
    $mavenLocal = if (Test-Path -LiteralPath $distribuicoes) {
        Get-ChildItem -LiteralPath $distribuicoes -Filter mvn.cmd -File -Recurse |
            Select-Object -First 1
    }
    if (-not $mavenLocal) {
        throw 'Maven não encontrado. Instale o Maven ou informe -Maven com o caminho do executável.'
    }
    $executavelMaven = $mavenLocal.FullName
}

$inicioUtc = [DateTimeOffset]::UtcNow.ToString('o')
$cacheMaven = Join-Path $env:USERPROFILE '.m2/repository'
$pastaEvidencias = Join-Path $raizProjeto 'docs/evidencias'
$arquivoEvidencia = Join-Path $pastaEvidencias 'task-29-estresse-draft.txt'
$argumentos = @("-Dmaven.repo.local=$cacheMaven", '-pl', 'servidor', '-am', 'test',
    '-Dtest=DraftEstresseTest', '-Dsurefire.failIfNoSpecifiedTests=false', '-Dstyle.color=never')

Push-Location -LiteralPath $raizProjeto
try {
    $saida = @(& $executavelMaven @argumentos 2>&1 | ForEach-Object {
        $linha = $_.ToString()
        Write-Host $linha
        $linha
    })
    $codigoSaida = $LASTEXITCODE
    $resumo = @($saida | Where-Object {
        $_ -match '^\[ESTRESSE-DRAFT\]' -or
        $_ -match '^\[INFO\] (Tests run:|BUILD SUCCESS|BUILD FAILURE|Total time:|Finished at:)'
    })
    $cenarios = @($saida | Where-Object { $_ -match '^\[ESTRESSE-DRAFT\]' })
    $sucesso = $codigoSaida -eq 0 -and $cenarios.Count -eq 4 -and ($saida -match 'BUILD SUCCESS')
    New-Item -ItemType Directory -Path $pastaEvidencias -Force | Out-Null
    @(
        'TASK 29 - TESTE DE ESTRESSE DO DRAFT'
        "Inicio UTC: $inicioUtc"
        'Comando: mvn -pl servidor -am test -Dtest=DraftEstresseTest -Dsurefire.failIfNoSpecifiedTests=false'
        'Escopo: chamadas concorrentes a Partida.escolherCriatura; sem transporte RMI.'
        'Carga: 32 threads de plataforma, 1000 iteracoes por cenario, largada sincronizada.'
        ''
    ) + $resumo + @('', "Status: $(if ($sucesso) { 'APROVADO' } else { 'FALHOU' })") |
        Set-Content -LiteralPath $arquivoEvidencia -Encoding utf8
    if (-not $sucesso) {
        throw "Teste de estresse falhou ou não executou os quatro cenários. Consulte $arquivoEvidencia"
    }
    Write-Host "Evidência salva em $arquivoEvidencia"
} finally {
    Pop-Location
}
