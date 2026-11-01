param(
    [switch]$Train,
    [switch]$Test
)

$ErrorActionPreference = 'Stop'
if ($Train -and $Test) { throw 'Используйте -Train и -Test по отдельности.' }

Push-Location -LiteralPath $PSScriptRoot
try {
    $jdk = Get-ChildItem -LiteralPath 'runtime' -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/javac.exe') } |
        Select-Object -First 1
    if (!$jdk -or !(Test-Path -LiteralPath 'distribution/iresolver/lib')) {
        throw 'Зависимости не установлены. Выполните python scripts/setup.py.'
    }

    # DL4J 0.9.1 не умеет читать JAR по URL с кириллицей: нужен ASCII-каталог.
    $libraryCache = Join-Path $env:TEMP 'iresolver-task5-lib-v1.1.0'
    New-Item -ItemType Directory -Path $libraryCache, 'build/classes', 'output' -Force | Out-Null
    Get-ChildItem -LiteralPath 'distribution/iresolver/lib' -Filter '*.jar' | ForEach-Object {
        $target = Join-Path $libraryCache $_.Name
        if (!(Test-Path -LiteralPath $target) -or (Get-Item -LiteralPath $target).Length -ne $_.Length) {
            Copy-Item -LiteralPath $_.FullName -Destination $target
        }
    }

    $classpath = "$libraryCache/*;config;build/classes"
    $sourceDirectory = if ($Test) { 'src' } else { 'src/main/java' }
    $sources = @(Get-ChildItem -LiteralPath $sourceDirectory -Recurse -Filter '*.java' |
        ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
    [IO.File]::WriteAllLines((Join-Path $PSScriptRoot 'build/sources.txt'),
        [string[]]$sources, [Text.UTF8Encoding]::new($false))
    & (Join-Path $jdk.FullName 'bin/javac.exe') '-J-Dfile.encoding=UTF-8' -encoding UTF-8 -Xlint:unchecked `
        -cp $classpath -d 'build/classes' '@build/sources.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Ошибка компиляции Java.' }

    $mainClass = if ($Test) { 'ru.polytech.iresolver.ExperimentTest' } else { 'ru.polytech.iresolver.Experiment' }
    $arguments = @('-Xmx3g', '-Dfile.encoding=UTF-8', '-Djava.awt.headless=true', '-cp', $classpath, $mainClass)
    if ($Train) { $arguments += '--train' }
    $log = if ($Test) { 'output/test.log' } else { 'output/run.log' }
    & (Join-Path $jdk.FullName 'bin/java.exe') @arguments 2>&1 | Tee-Object -FilePath $log
    if ($LASTEXITCODE -ne 0) { throw "Ошибка выполнения; подробности в $log." }
} finally {
    Pop-Location
}
