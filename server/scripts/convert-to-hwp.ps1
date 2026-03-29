param(
    [Parameter(Mandatory = $true)]
    [string]$DocxPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [Parameter(Mandatory = $false)]
    [string]$HmlPath
)

$ErrorActionPreference = "Stop"

function Invoke-TemplateCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Template
    )

    $command = $Template.Replace("{docx}", $DocxPath).Replace("{output}", $OutputPath).Replace("{hml}", $HmlPath)
    & cmd /c $command
    return $LASTEXITCODE
}

if ($env:HWP_NATIVE_CONVERTER_COMMAND) {
    $exitCode = Invoke-TemplateCommand -Template $env:HWP_NATIVE_CONVERTER_COMMAND
    if ($exitCode -ne 0) {
        throw "Native HWP converter command failed with exit code $exitCode."
    }
} elseif ($env:HWP_NATIVE_CONVERTER_EXE) {
    & $env:HWP_NATIVE_CONVERTER_EXE $DocxPath $OutputPath $HmlPath
    if ($LASTEXITCODE -ne 0) {
        throw "Native HWP converter executable failed with exit code $LASTEXITCODE."
    }
} else {
    throw "No native HWP converter is configured. Set HWP_NATIVE_CONVERTER_COMMAND or HWP_NATIVE_CONVERTER_EXE."
}

if (-not (Test-Path -LiteralPath $OutputPath)) {
    throw "HWP converter finished without creating output: $OutputPath"
}
