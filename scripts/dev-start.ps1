[CmdletBinding()]
param(
    [string]$EnvFile,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$composeFile = Join-Path $repoRoot "deploy/docker-compose.yml"
$backendDirectory = Join-Path $repoRoot "backend"

if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $envPath = Join-Path $repoRoot ".env"
} else {
    $envPath = $EnvFile
}

if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) {
    throw "Environment file not found: $envPath. Copy .env.example to .env and fill in local values."
}
$envPath = (Resolve-Path -LiteralPath $envPath).Path
if (-not (Test-Path -LiteralPath $composeFile -PathType Leaf)) {
    throw "Docker Compose file not found: $composeFile"
}
if (-not (Test-Path -LiteralPath $backendDirectory -PathType Container)) {
    throw "Backend directory not found: $backendDirectory"
}

function Convert-DotEnvValue {
    param([string]$RawValue, [int]$LineNumber)

    $value = $RawValue.Trim()
    if ($value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')) {
        return $value.Substring(1, $value.Length - 2).Replace('\"', '"').Replace('\\', '\').Replace('\n', [Environment]::NewLine)
    }
    if ($value.Length -ge 2 -and $value.StartsWith("'") -and $value.EndsWith("'")) {
        return $value.Substring(1, $value.Length - 2)
    }
    if ($value.StartsWith('"') -or $value.StartsWith("'")) {
        throw "Unclosed quoted value in $envPath at line $LineNumber"
    }
    return [regex]::Replace($value, "\s+#.*$", "").Trim()
}

$loadedNames = [System.Collections.Generic.List[string]]::new()
$lineNumber = 0
foreach ($line in Get-Content -LiteralPath $envPath) {
    $lineNumber++
    $trimmed = $line.Trim().TrimStart([char]0xFEFF)
    if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) {
        continue
    }

    $assignment = [regex]::Match($trimmed, "^(?:export\s+)?(?<name>[A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?<value>.*)$")
    if (-not $assignment.Success) {
        throw "Invalid .env assignment in $envPath at line $lineNumber"
    }

    $name = $assignment.Groups["name"].Value
    $value = Convert-DotEnvValue $assignment.Groups["value"].Value $lineNumber
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
    $loadedNames.Add($name)
}

$requiredNames = @("MYSQL_ROOT_PASSWORD", "MYSQL_PASSWORD", "JWT_SECRET")
$missingNames = @($requiredNames | Where-Object {
        [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, "Process"))
    })
if ($missingNames.Count -gt 0) {
    throw "Missing required local values in {0}: {1}" -f $envPath, ($missingNames -join ', ')
}

Write-Host "Loaded $($loadedNames.Count) variables from $envPath"
if ($DryRun) {
    Write-Host "Dry run completed. No containers or backend process were started."
    return
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker command was not found in PATH."
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "mvn command was not found in PATH. Configure Java and Maven before running this script."
}

$composeArguments = @("--env-file", $envPath, "-f", $composeFile)
Write-Host "Starting MySQL and Qdrant..."
& docker compose @composeArguments up -d --wait --wait-timeout 90 mysql qdrant
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose failed to start MySQL or Qdrant."
}

foreach ($service in @("mysql", "qdrant")) {
    $containerId = (& docker compose @composeArguments ps -q $service | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
        throw "Could not find the $service container after startup."
    }
    $containerInfo = (& docker inspect $containerId | ConvertFrom-Json)[0]
    $state = [string]$containerInfo.State.Status
    $health = ""
    $healthProperty = $containerInfo.State.PSObject.Properties["Health"]
    if ($null -ne $healthProperty -and $null -ne $healthProperty.Value) {
        $health = [string]$healthProperty.Value.Status
    }
    if ($state -ne "running" -or ($health -and $health -ne "healthy")) {
        throw "$service is not healthy. state=$state health=$health"
    }
    $healthSuffix = if ($health) { " (health=$health)" } else { "" }
    Write-Host ($service + " is running" + $healthSuffix)
}

& docker compose @composeArguments ps
if ($LASTEXITCODE -ne 0) {
    throw "Could not inspect Docker Compose service status."
}

Write-Host "Starting Spring Boot backend. Press Ctrl+C to stop the backend process."
Push-Location $backendDirectory
try {
    & mvn spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "mvn spring-boot:run exited with code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}
